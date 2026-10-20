package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.ConfigZone
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.PixelRect
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.ZoneGeometry
import com.digitalsignage.player.engine.EngineState
import com.digitalsignage.player.engine.PlayerEngine
import com.digitalsignage.player.engine.Timing
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Telemetry
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun zone(id: Long, name: String, x: Int, y: Int, w: Int, h: Int, main: Boolean, vararg items: ConfigItem) =
    ConfigZone(id, name, x, y, w, h, main, items.toList())

private fun webItem(id: Long, url: String, seconds: Int = 30) = ConfigItem(
    mediaId = id, name = "painel.exemplo.com", type = "WEB", url = url, checksum = null, fileSize = 0, position = 0, durationSeconds = seconds,
)

/** Layouts com zonas e páginas da web no Player: geometria, leitura da configuração, download e estado. */
@OptIn(ExperimentalCoroutinesApi::class)
class ZonesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val imgA = "imagem-a".toByteArray()
    private val imgB = "imagem-b".toByteArray()
    private val imgC = "imagem-c".toByteArray()

    // ---------- geometria ----------

    @Test
    fun `a full screen zone fills the screen`() {
        val rect = ZoneGeometry.rect(zone(1, "P", 0, 0, 100, 100, true), 1920, 1080)

        assertEquals(PixelRect(0, 0, 1920, 1080), rect)
    }

    @Test
    fun `a main zone and a side strip tile the screen without a gap`() {
        val main = ZoneGeometry.rect(zone(1, "P", 0, 0, 75, 100, true), 1920, 1080)
        val side = ZoneGeometry.rect(zone(2, "L", 75, 0, 25, 100, false), 1920, 1080)

        assertEquals(PixelRect(0, 0, 1440, 1080), main)
        assertEquals(PixelRect(1440, 0, 480, 1080), side)
    }

    /** Em resoluções que não dividem certinho (1366x768) cada borda é arredondada sozinha: nenhuma fresta e nenhuma sobreposição. */
    @Test
    fun `neighbouring zones still touch exactly on screens that do not divide evenly`() {
        for ((width, height) in listOf(1366 to 768, 1280 to 720, 3840 to 2160, 854 to 480)) {
            val left = ZoneGeometry.rect(zone(1, "E", 0, 0, 33, 100, true), width, height)
            val middle = ZoneGeometry.rect(zone(2, "M", 33, 0, 34, 100, false), width, height)
            val right = ZoneGeometry.rect(zone(3, "D", 67, 0, 33, 100, false), width, height)

            assertEquals("$width: esquerda encosta no meio", left.left + left.width, middle.left)
            assertEquals("$width: meio encosta na direita", middle.left + middle.width, right.left)
            assertEquals("$width: a direita termina na borda", width, right.left + right.width)
        }
    }

    @Test
    fun `vertical neighbours tile too and a zone is never zero pixels wide`() {
        val top = ZoneGeometry.rect(zone(1, "C", 0, 0, 100, 85, true), 1366, 768)
        val bottom = ZoneGeometry.rect(zone(2, "R", 0, 85, 100, 15, false), 1366, 768)

        assertEquals(768, bottom.top + bottom.height)
        assertEquals(top.top + top.height, bottom.top)
        assertTrue(ZoneGeometry.rect(zone(3, "X", 0, 0, 5, 5, false), 10, 10).width >= 1)
    }

    // ---------- leitura da configuração ----------

    private val layoutJson = """
        {"deviceId":1,"playlistId":5,"configVersion":777,
         "items":[{"mediaId":1,"name":"a.png","type":"IMAGE","url":"/api/v1/media/1/file","checksum":"x","fileSize":8,"position":0,"durationSeconds":10}],
         "zones":[
           {"id":10,"name":"Principal","x":0,"y":0,"width":75,"height":100,"main":true,"playlistId":5,
            "items":[{"mediaId":1,"name":"a.png","type":"IMAGE","url":"/api/v1/media/1/file","checksum":"x","fileSize":8,"position":0,"durationSeconds":10}]},
           {"id":11,"name":"Lateral","x":75,"y":0,"width":25,"height":100,"main":false,"playlistId":7,
            "items":[{"mediaId":2,"name":"painel","type":"WEB","url":"https://painel.exemplo.com/tv","checksum":null,"fileSize":0,"position":0,"durationSeconds":45},
                     {"mediaId":3,"name":"b.png","type":"IMAGE","url":"/api/v1/media/3/file","checksum":"y","fileSize":8,"position":1,"durationSeconds":5}]}
         ]}
    """.trimIndent()

    @Test
    fun `reads the zones of a layout with their items and the web page`() {
        val config = Json.parseConfig(layoutJson)

        assertEquals(2, config.zones.size)
        val side = config.zones[1]
        assertEquals("Lateral", side.name)
        assertEquals(75, side.x)
        assertEquals(25, side.width)
        assertFalse(side.main)
        assertEquals(listOf("WEB", "IMAGE"), side.items.map { it.type })
        assertTrue(side.items[0].isWeb)
        assertEquals("https://painel.exemplo.com/tv", side.items[0].url)
        assertNull(side.items[0].checksum)
        assertEquals(45, side.items[0].durationSeconds)
        assertTrue(config.zones[0].main)
    }

    @Test
    fun `a config from an older server without zones becomes one full screen main zone`() {
        val old = Json.parseConfig(
            """{"deviceId":1,"playlistId":5,"configVersion":3,"items":[{"mediaId":1,"name":"a.png","type":"IMAGE","url":"/x","checksum":"x","fileSize":8,"position":0,"durationSeconds":10}]}""",
        )

        assertTrue(old.zones.isEmpty())
        val zones = old.zonesOrDefault()
        assertEquals(1, zones.size)
        assertTrue(zones[0].isFullScreen && zones[0].main)
        assertEquals(1, zones[0].items.size)
        assertTrue(old.hasContent())
    }

    @Test
    fun `lists every media of every zone once`() {
        val config = Json.parseConfig(layoutJson)

        assertEquals(listOf(1L, 2L, 3L), config.allItems().map { it.mediaId })
    }

    @Test
    fun `has no content when every zone is empty`() {
        assertFalse(PlayerConfig(1, null, 0, emptyList()).hasContent())
        assertFalse(PlayerConfig(1, null, 9, emptyList(), listOf(zone(1, "P", 0, 0, 100, 100, true))).hasContent())
        assertTrue(PlayerConfig(1, null, 9, emptyList(), listOf(zone(1, "P", 0, 0, 50, 100, true), zone(2, "L", 50, 0, 50, 100, false, itemFor(1, imgA)))).hasContent())
    }

    // ---------- sincronização ----------

    @Test
    fun `downloads the media of every zone, only once, and never tries to download a web page`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        val web = webItem(9, "https://painel.exemplo.com/tv")
        val config = PlayerConfig(
            1, 5, 100, listOf(a),
            listOf(zone(10, "Principal", 0, 0, 75, 100, true, a), zone(11, "Lateral", 75, 0, 25, 100, false, web, b, a)),
        )
        val api = FakePlayerApi().also { it.configResponse = { config } }
        val downloader = FakeDownloader().also {
            it.files[a.url] = imgA
            it.files[b.url] = imgB
        }
        val cache = MediaCache(File(folder.root, "media"))
        val store = InMemoryStore()

        val synced = ConfigSync(api, downloader, cache, store).sync("token")

        assertEquals(listOf(a.url, b.url), downloader.requested) // a mídia repetida e a página não baixam
        assertTrue(cache.isCached(a) && cache.isCached(b) && cache.isCached(web))
        assertEquals(2, synced.zones.size)
        assertEquals(2, store.load()!!.zones.size) // as zonas ficam guardadas para a TV voltar a tocar sem rede
    }

    @Test
    fun `cleans the cache of media no zone uses any more but keeps what any zone still needs`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        val c = itemFor(3, imgC)
        val downloader = FakeDownloader().also {
            it.files[a.url] = imgA
            it.files[b.url] = imgB
            it.files[c.url] = imgC
        }
        val cache = MediaCache(File(folder.root, "media"))
        var config = PlayerConfig(1, 5, 1, listOf(a), listOf(zone(10, "P", 0, 0, 75, 100, true, a), zone(11, "L", 75, 0, 25, 100, false, b, c)))
        val api = FakePlayerApi().also { it.configResponse = { config } }
        val sync = ConfigSync(api, downloader, cache, InMemoryStore())
        sync.sync("token")

        config = PlayerConfig(1, 5, 2, listOf(a), listOf(zone(10, "P", 0, 0, 75, 100, true, a), zone(11, "L", 75, 0, 25, 100, false, b)))
        sync.sync("token")

        assertTrue(cache.isCached(a) && cache.isCached(b))
        assertFalse(cache.isCached(c))
    }

    @Test
    fun `a web page counts as cached even with an empty cache folder`() {
        val cache = MediaCache(File(folder.root, "media"))

        assertTrue(cache.isCached(webItem(9, "https://painel.exemplo.com/tv")))
    }

    // ---------- estado do motor ----------

    private fun TestScope.start(engine: PlayerEngine) {
        backgroundScope.launch { engine.run() }
        runCurrent()
    }

    private fun engineFor(config: PlayerConfig): PlayerEngine {
        val api = FakePlayerApi()
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(config.playlistId, config.configVersion) }
        api.configResponse = { config }
        val downloader = FakeDownloader().also {
            config.allItems().filter { item -> !item.isWeb }.forEach { item -> it.files[item.url] = imgA }
        }
        val store = InMemoryStore()
        return PlayerEngine(
            api, ConfigSync(api, downloader, MediaCache(File(folder.root, "media")), store), store,
            token = { "tok" }, playerVersion = "0.6.0", timing = Timing(), telemetry = Telemetry(),
        )
    }

    @Test
    fun `plays when only a secondary zone has content (the main zone has no playlist)`() = runTest {
        val a = itemFor(1, imgA)
        val config = PlayerConfig(1, null, 321, emptyList(), listOf(zone(10, "P", 0, 0, 75, 100, true), zone(11, "L", 75, 0, 25, 100, false, a)))
        val engine = engineFor(config)

        start(engine)

        val state = engine.state.value
        assertTrue("esperava Playing, veio $state", state is EngineState.Playing)
        assertEquals(2, (state as EngineState.Playing).config.zones.size)
    }

    @Test
    fun `shows no content when a layout has no media in any zone`() = runTest {
        val config = PlayerConfig(1, null, 321, emptyList(), listOf(zone(10, "P", 0, 0, 100, 100, true)))
        val engine = engineFor(config)

        start(engine)

        assertEquals(EngineState.NoContent, engine.state.value)
    }

}
