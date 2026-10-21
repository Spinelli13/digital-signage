package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.data.WidgetSpec
import com.digitalsignage.player.engine.EngineState
import com.digitalsignage.player.engine.PlayerEngine
import com.digitalsignage.player.engine.Timing
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Telemetry
import com.digitalsignage.player.time.TrustedClock
import com.digitalsignage.player.widget.WidgetAssets
import com.digitalsignage.player.widget.WidgetHost
import com.digitalsignage.player.widget.WidgetRefresher
import com.digitalsignage.player.widget.WidgetStore
import com.digitalsignage.player.widget.WidgetTemplate
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun widgetItem(id: Long, kind: String, settings: String = "{}", seconds: Int = 10) = ConfigItem(
    mediaId = id,
    name = "widget$id",
    type = "WIDGET",
    url = "/api/v1/player/widgets/$id/data",
    checksum = null,
    fileSize = 0,
    position = (id - 1).toInt(),
    durationSeconds = seconds,
    widget = WidgetSpec(kind, settings),
)

private fun response(dataJson: String?) =
    """{"kind":"X","data":${dataJson ?: "null"},"error":${if (dataJson == null) "\"falhou\"" else "null"},"serverTimeMs":1}"""

/** Os modelos de verdade (a pasta frontend/public/widgets do repositório), os mesmos que o painel usa na pré-visualização. */
private val REAL_TEMPLATES = object : WidgetAssets {
    private val dir = File("../../frontend/public/widgets")
    override fun template(): String =
        WidgetTemplate.assemble(File(dir, "widget.html").readText(), File(dir, "widget.css").readText(), File(dir, "widget.js").readText())
}

class WidgetModelTest {

    @Test
    fun `the server config brings the widget with its settings and no file`() {
        val config = PlayerConfig(1, 5, 1, listOf(widgetItem(3, "WEATHER", """{"city":"Campinas","latitude":-22.9,"longitude":-47.06,"days":3}""")))

        val parsed = Json.parseConfig(rawJson(config))

        val item = parsed.items.single()
        assertTrue(item.isWidget)
        assertFalse(item.hasFile)
        assertTrue(item.usesWebView)
        assertEquals("WEATHER", item.widget?.kind)
        assertTrue(item.widget!!.needsData)
        assertTrue(item.widget!!.settingsJson.contains("\"city\":\"Campinas\""))
    }

    @Test
    fun `clock, notice and countdown need no data from the server`() {
        assertFalse(WidgetSpec("CLOCK", "{}").needsData)
        assertFalse(WidgetSpec("NOTICE", "{}").needsData)
        assertFalse(WidgetSpec("COUNTDOWN", "{}").needsData)
        assertTrue(WidgetSpec("NEWS", "{}").needsData)
        assertTrue(WidgetSpec("RATES", "{}").needsData)
        assertTrue(WidgetSpec("PRICES", "{}").needsData)
    }

    @Test
    fun `an image and a web page keep their behavior`() {
        val image = itemFor(1, "x".toByteArray())
        val web = image.copy(type = "WEB")

        assertTrue(image.hasFile)
        assertFalse(web.hasFile)
        assertTrue(web.usesWebView)
        assertFalse(image.usesWebView)
        assertNull(image.widget)
    }
}

class WidgetSyncTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()

    @Test
    fun `a widget is never downloaded or counted for space, and it is always ready to play`() = runTest {
        val image = itemFor(1, "foto".toByteArray())
        downloader.files[image.url] = "foto".toByteArray()
        val config = PlayerConfig(1, 5, 1, listOf(image, widgetItem(2, "CLOCK")))
        api.configResponse = { config }
        val cache = MediaCache(File(folder.root, "media"))

        ConfigSync(api, downloader, cache, InMemoryStore(), freeBytes = { 4 }, reserveBytes = 0).sync("tok")

        assertEquals(listOf(image.url), downloader.requested)
        assertTrue(cache.isCached(widgetItem(2, "CLOCK")))
    }

    @Test
    fun `cleaning the cache keeps working with widgets in the config`() = runTest {
        val config = PlayerConfig(1, 5, 1, listOf(widgetItem(2, "NEWS")))
        api.configResponse = { config }
        val cache = MediaCache(File(folder.root, "media"))
        File(folder.root, "media/velho.png").writeText("lixo")

        ConfigSync(api, downloader, cache, InMemoryStore()).sync("tok")

        assertFalse(File(folder.root, "media/velho.png").exists())
    }
}

class WidgetStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000L
    private fun store() = WidgetStore(File(folder.root, "widgets"), wall = { now })

    @Test
    fun `keeps the last data and when it arrived`() {
        val store = store()

        assertTrue(store.saveResponse(7, response("""{"current":{"temperature":21.5},"fetchedAtMs":900}""")))

        val stored = store.load(7)!!
        assertEquals(21.5, stored.data.getJSONObject("current").getDouble("temperature"), 0.001)
        assertEquals(1_000L, stored.receivedAtMs)
    }

    @Test
    fun `a failed answer never erases what was already stored`() {
        val store = store()
        store.saveResponse(7, response("""{"v":1}"""))

        assertFalse(store.saveResponse(7, response(null)))
        assertFalse(store.saveResponse(7, "isso nem é json"))

        assertEquals(1, store.load(7)!!.data.getInt("v"))
    }

    @Test
    fun `newer data replaces the older one`() {
        val store = store()
        store.saveResponse(7, response("""{"v":1}"""))
        now = 5_000

        store.saveResponse(7, response("""{"v":2}"""))

        assertEquals(2, store.load(7)!!.data.getInt("v"))
        assertEquals(5_000L, store.load(7)!!.receivedAtMs)
    }

    @Test
    fun `a corrupted file is ignored and removed`() {
        val store = store()
        File(folder.root, "widgets/9.json").writeText("{quebrado")

        assertNull(store.load(9))
        assertFalse(File(folder.root, "widgets/9.json").exists())
    }

    @Test
    fun `forgets the widgets that left the config`() {
        val store = store()
        store.saveResponse(1, response("""{"v":1}"""))
        store.saveResponse(2, response("""{"v":2}"""))

        store.retainOnly(setOf(2))

        assertNull(store.load(1))
        assertNotNull(store.load(2))
    }
}

class WidgetRefresherTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val store get() = WidgetStore(File(folder.root, "widgets"))

    private fun config(vararg items: ConfigItem) = PlayerConfig(1, 5, 1, items.toList())

    @Test
    fun `asks only for the widgets that show data from outside`() = runTest {
        api.widgetResponse = { response("""{"ok":true}""") }
        val items = arrayOf(widgetItem(1, "CLOCK"), widgetItem(2, "WEATHER"), widgetItem(3, "NOTICE"), widgetItem(4, "RATES"), itemFor(5, "x".toByteArray()))

        val updated = WidgetRefresher(api, store).refresh("tok", config(*items))

        assertEquals(2, updated)
        assertEquals(listOf("/api/v1/player/widgets/2/data", "/api/v1/player/widgets/4/data"), api.widgetCalls)
    }

    @Test
    fun `one widget failing does not stop the others and keeps its last data`() = runTest {
        val store = store
        store.saveResponse(2, response("""{"v":"antigo"}"""))
        api.widgetResponse = { url -> if (url.contains("/2/")) throw java.io.IOException("sem rede") else response("""{"v":"novo"}""") }

        val updated = WidgetRefresher(api, store).refresh("tok", config(widgetItem(2, "WEATHER"), widgetItem(3, "NEWS")))

        assertEquals(1, updated)
        assertEquals("antigo", store.load(2)!!.data.getString("v"))
        assertEquals("novo", store.load(3)!!.data.getString("v"))
    }

    @Test
    fun `also prepares the widgets of the offline schedule`() = runTest {
        api.widgetResponse = { response("""{"ok":true}""") }
        val plan = com.digitalsignage.player.data.OfflinePlan(
            1, 0, 1_000,
            listOf(com.digitalsignage.player.data.PlanSegment(0, 1_000, 9, "Clima", 2, 2)),
            listOf(com.digitalsignage.player.data.PlanPlaylist(2, listOf(widgetItem(8, "WEATHER")))),
        )

        WidgetRefresher(api, store).refresh("tok", config(widgetItem(2, "NEWS")).copy(plan = plan))

        assertEquals(listOf("/api/v1/player/widgets/2/data", "/api/v1/player/widgets/8/data"), api.widgetCalls)
    }

    @Test
    fun `forgets the data of widgets that are gone`() = runTest {
        val store = store
        store.saveResponse(99, response("""{"v":1}"""))
        api.widgetResponse = { response("""{"v":2}""") }

        WidgetRefresher(api, store).refresh("tok", config(widgetItem(2, "NEWS")))

        assertNull(store.load(99))
    }

    @Test
    fun `an unauthorized answer goes up so the engine can register again`() = runTest {
        api.widgetResponse = { throw UnauthorizedException() }
        try {
            WidgetRefresher(api, store).refresh("tok", config(widgetItem(2, "NEWS")))
            fail("deveria ter lançado")
        } catch (e: UnauthorizedException) {
            // esperado
        }
    }
}

class WidgetHostTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { WidgetStore(File(folder.root, "widgets")) }
    private val clock = TrustedClock(wall = { 1_793_890_800_000L }, uptime = { 0 })

    private fun host() = WidgetHost(REAL_TEMPLATES, store, clock)

    @Test
    fun `the page has everything inside and no leftover marker`() {
        val html = host().html(widgetItem(1, "CLOCK", """{"timezone":"America/Sao_Paulo","showSeconds":true}"""))

        assertTrue(html.contains("Smartplay.start("))
        assertFalse(html.contains("/*__"))
        assertTrue(html.contains("<style>"))
        assertTrue(html.contains("window.Smartplay"))
        assertTrue(html.contains("\"kind\":\"CLOCK\""))
        assertTrue(html.contains("\"nowMs\":1793890800000"))
        // nada de recurso externo: a TV desenha sem internet
        assertFalse(Regex("""(src|href)\s*=\s*["']https?:""").containsMatchIn(html))
    }

    @Test
    fun `the clock uses the trusted time, not the device clock`() {
        clock.sync(1_800_000_000_000L)

        val html = host().html(widgetItem(1, "CLOCK"))

        assertTrue(html.contains("\"nowMs\":1800000000000"))
    }

    @Test
    fun `data widgets carry the last stored data and the others carry none`() {
        store.saveResponse(2, response("""{"current":{"temperature":19.0},"fetchedAtMs":42}"""))

        val weather = host().html(widgetItem(2, "WEATHER", """{"city":"Campinas"}"""))
        val notice = host().html(widgetItem(3, "NOTICE", """{"title":"Oi"}"""))
        val empty = host().html(widgetItem(4, "NEWS"))

        assertTrue(weather.contains("\"temperature\":19"))
        assertTrue(notice.contains("\"data\":null"))
        assertTrue(empty.contains("\"data\":null"))
    }

    @Test
    fun `a headline cannot close the script or break the page`() {
        store.saveResponse(5, response("""{"items":[{"title":"</script><script>alert(1)</script>   fim"}]}"""))

        val html = host().html(widgetItem(5, "NEWS"))
        val start = html.indexOf("Smartplay.start(")
        val init = html.substring(start)

        assertFalse(init.contains("</script><script>alert"))
        assertFalse(init.contains(" "))
        assertTrue(init.contains("<\\/script>"))
    }

    @Test
    fun `broken settings do not crash the page`() {
        val html = host().html(widgetItem(6, "CLOCK", "isso não é json"))

        assertTrue(html.contains("\"settings\":{}"))
    }

    @Test
    fun `a price board carries the intervals of the next hours so prices change on time without internet`() {
        store.saveResponse(8, response("""{"list":{"name":"Bar","currency":"BRL"},"segments":[{"fromMs":0,"toMs":100,"rule":null,"items":[{"name":"Cerveja","price":10}]},{"fromMs":100,"toMs":200,"rule":"Happy hour","items":[{"name":"Cerveja","price":8}]}]}"""))

        val html = host().html(widgetItem(8, "PRICES", """{"priceListId":3,"columns":2}"""))

        assertTrue(html.contains("\"Happy hour\""))
        assertTrue(html.contains("\"kind\":\"PRICES\""))
        assertTrue(html.contains("PRICES: function"))
    }

    @Test
    fun `the R-dollar of the rates template survives the assembly`() {
        val html = host().html(widgetItem(7, "RATES"))

        assertTrue(html.contains("'R$'"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()
    private val telemetry = Telemetry()
    private val timing = Timing(activationPollMs = 5_000, heartbeatMs = 30_000, retryMs = 10_000, disabledPollMs = 30_000, widgetRefreshMs = 600_000)

    private fun TestScope.engine(config: PlayerConfig): PlayerEngine {
        val store = InMemoryStore(config)
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(config.playlistId, config.configVersion) }
        api.widgetResponse = { response("""{"ok":true}""") }
        val clock = TrustedClock(uptime = { testScheduler.currentTime })
        val widgetStore = WidgetStore(File(folder.root, "widgets"))
        return PlayerEngine(
            api, ConfigSync(api, downloader, MediaCache(File(folder.root, "media")), store), store,
            token = { "tok" }, playerVersion = "0.11.0", timing = timing, telemetry = telemetry, clock = clock,
            widgets = WidgetRefresher(api, widgetStore),
        )
    }

    private val config = PlayerConfig(1, 5, 1, listOf(widgetItem(2, "WEATHER"), widgetItem(3, "CLOCK")))

    @Test
    fun `a new configuration only goes on air after its widget data arrived`() = runTest {
        val store = InMemoryStore()
        val newConfig = PlayerConfig(1, 5, 1, listOf(widgetItem(2, "WEATHER")))
        val stateWhenAsked = mutableListOf<EngineState>()
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(5, 1) }
        api.configResponse = { newConfig }
        api.widgetResponse = { url ->
            stateWhenAsked += engineState()
            response("""{"ok":true}""")
        }
        val clock = TrustedClock(uptime = { testScheduler.currentTime })
        val engine = PlayerEngine(
            api, ConfigSync(api, downloader, MediaCache(File(folder.root, "media")), store), store,
            token = { "tok" }, playerVersion = "0.11.0", timing = timing, telemetry = telemetry, clock = clock,
            widgets = WidgetRefresher(api, WidgetStore(File(folder.root, "widgets"))),
        )
        engineHolder = engine

        backgroundScope.launch { engine.run() }
        runCurrent()

        assertEquals(1, api.widgetCalls.size)
        assertTrue(stateWhenAsked.single() !is EngineState.Playing) // ainda não estava no ar quando os dados foram pedidos
        assertTrue(engine.state.value is EngineState.Playing)
    }

    private var engineHolder: PlayerEngine? = null
    private fun engineState(): EngineState = engineHolder!!.state.value

    @Test
    fun `fetches the widget data right away and then every ten minutes`() = runTest {
        val engine = engine(config)

        backgroundScope.launch { engine.run() }
        runCurrent()
        assertEquals(listOf("/api/v1/player/widgets/2/data"), api.widgetCalls)

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(1, api.widgetCalls.size) // ainda não deu o tempo

        advanceTimeBy(6 * 60_000L)
        runCurrent()
        assertEquals(2, api.widgetCalls.size)
    }
}
