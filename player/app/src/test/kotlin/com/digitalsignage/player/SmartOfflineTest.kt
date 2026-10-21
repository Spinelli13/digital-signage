package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigZone
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.OfflinePlan
import com.digitalsignage.player.data.PlanPlaylist
import com.digitalsignage.player.data.PlanSegment
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.engine.EngineState
import com.digitalsignage.player.engine.PlayerEngine
import com.digitalsignage.player.engine.Timing
import com.digitalsignage.player.plan.OfflinePlanner
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.Telemetry
import com.digitalsignage.player.time.ClockOffsetStore
import com.digitalsignage.player.time.TrustedClock
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val MIN = 60_000L
private const val HOUR = 60 * MIN
private const val DAY = 24 * HOUR

private val imgBase = "base".toByteArray()
private val imgPromo = "promo".toByteArray()
private val imgNight = "night".toByteArray()

private fun segment(from: Long, to: Long, playlist: Long?, campaign: Long? = null, name: String? = null, version: Long = playlist ?: 0) =
    PlanSegment(from, to, campaign, name, playlist, version)

/** Base de 0 a 10 min, campanha Promo de 10 a 20 min, base de novo até 7 dias. */
private fun plan(version: Long = 77, until: Long = 7 * DAY) = OfflinePlan(
    version = version,
    generatedAtMs = 0,
    untilMs = until,
    segments = listOf(
        segment(0, 10 * MIN, 1),
        segment(10 * MIN, 20 * MIN, 2, campaign = 9, name = "Promo"),
        segment(20 * MIN, until, 1),
    ),
    playlists = listOf(
        PlanPlaylist(1, listOf(itemFor(1, imgBase))),
        PlanPlaylist(2, listOf(itemFor(2, imgPromo))),
    ),
)

private fun baseConfig(plan: OfflinePlan? = plan()) =
    PlayerConfig(deviceId = 1, playlistId = 1, configVersion = 1, items = listOf(itemFor(1, imgBase)), plan = plan)

class TrustedClockTest {

    private class Offset(override var clockOffsetMs: Long = 0) : ClockOffsetStore

    @Test
    fun `without any contact it uses the device clock`() {
        val clock = TrustedClock(wall = { 5_000 }, uptime = { 0 })
        assertEquals(5_000L, clock.now())
        assertFalse(clock.isSynced())
    }

    @Test
    fun `after a contact it follows the server time and ignores jumps of the device clock`() {
        var wall = 1_000_000L
        var uptime = 500L
        val clock = TrustedClock(wall = { wall }, uptime = { uptime })

        clock.sync(9_000_000L) // o servidor diz 9.000.000; o relógio da TV está 8.000.000 atrás
        uptime += 60_000
        wall += 60_000
        assertEquals(9_060_000L, clock.now())

        wall = 0 // alguém mexeu na hora da TV
        uptime += 60_000
        assertEquals(9_120_000L, clock.now())
        assertTrue(clock.isSynced())
    }

    @Test
    fun `after the Player restarts it uses the device clock corrected by the last known difference`() {
        val store = Offset()
        TrustedClock(wall = { 1_000 }, uptime = { 0 }, store = store).sync(4_000) // TV 3 s atrasada

        val reopened = TrustedClock(wall = { 11_000 }, uptime = { 0 }, store = store)

        assertEquals(14_000L, reopened.now())
        assertFalse(reopened.isSynced())
    }

    @Test
    fun `a server that sends no time (zero) does not disturb the clock`() {
        val store = Offset(2_000)
        val clock = TrustedClock(wall = { 10_000 }, uptime = { 0 }, store = store)

        clock.sync(0)

        assertEquals(12_000L, clock.now())
        assertEquals(2_000L, store.clockOffsetMs)
    }
}

class OfflinePlannerTest {

    @Test
    fun `picks the interval the time falls in`() {
        val base = baseConfig()

        assertEquals(1L, OfflinePlanner.configAt(base, 5 * MIN)?.playlistId)
        val promo = OfflinePlanner.configAt(base, 10 * MIN)!!
        assertEquals(2L, promo.playlistId)
        assertEquals(9L, promo.campaignId)
        assertEquals("Promo", promo.campaignName)
        assertEquals(listOf(2L), promo.items.map { it.mediaId })
        // o fim do intervalo é exclusivo: 20 min já é a base de novo
        assertEquals(1L, OfflinePlanner.configAt(base, 20 * MIN)?.playlistId)
        assertNull(OfflinePlanner.configAt(base, 20 * MIN)?.campaignId)
    }

    @Test
    fun `an expired plan or a config without plan changes nothing`() {
        assertNull(OfflinePlanner.configAt(baseConfig(), 8 * DAY))
        assertNull(OfflinePlanner.configAt(baseConfig(plan = null), 5 * MIN))
        assertNull(OfflinePlanner.configAt(baseConfig(), -1))
    }

    @Test
    fun `replaces only the main zone and keeps the fixed zones`() {
        val side = itemFor(7, "lateral".toByteArray())
        val zones = listOf(
            ConfigZone(1, "Principal", 0, 0, 75, 100, true, listOf(itemFor(1, imgBase))),
            ConfigZone(2, "Lateral", 75, 0, 25, 100, false, listOf(side)),
        )
        val base = baseConfig().copy(zones = zones)

        val promo = OfflinePlanner.configAt(base, 12 * MIN)!!

        assertEquals(listOf(2L), promo.zones[0].items.map { it.mediaId })
        assertEquals(listOf(7L), promo.zones[1].items.map { it.mediaId })
        assertEquals(listOf(2L), promo.items.map { it.mediaId })
    }

    @Test
    fun `the result equals what the server would send at that moment, so the screen does not restart when it reconnects`() {
        val server = PlayerConfig(deviceId = 1, playlistId = 2, configVersion = 2, items = listOf(itemFor(2, imgPromo)), campaignId = 9, campaignName = "Promo", plan = plan(version = 99))

        val local = OfflinePlanner.configAt(baseConfig(), 12 * MIN)!!

        assertEquals(server.forScreen(), local.forScreen())
    }

    @Test
    fun `a gap with no playlist shows nothing`() {
        val gap = OfflinePlan(1, 0, 1_000, listOf(segment(0, 1_000, null)), emptyList())

        val config = OfflinePlanner.configAt(baseConfig(gap), 500)!!

        assertFalse(config.hasContent())
        assertNull(config.playlistId)
    }
}

class OfflinePlanParsingTest {

    @Test
    fun `parses the plan the server sends inside the config`() {
        val config = baseConfig().copy(campaignId = 9, campaignName = "Promo")

        val parsed = Json.parseConfig(rawJson(config))

        assertEquals(config, parsed)
        assertEquals(3, parsed.plan!!.segments.size)
        assertEquals(listOf(1L, 2L), parsed.plan!!.playlists.map { it.playlistId })
        assertEquals(listOf(1L, 2L), parsed.plan!!.allItems().map { it.mediaId })
    }

    @Test
    fun `a config without plan (old server or no campaigns) parses with no plan`() {
        assertNull(Json.parseConfig(rawJson(baseConfig(plan = null))).plan)
        assertNull(Json.parseConfig("""{"deviceId":1,"playlistId":null,"configVersion":0,"items":[],"offlinePlan":null}""").plan)
    }

    @Test
    fun `the heartbeat brings the plan version and the server time`() {
        val desired = Json.parseDesired("""{"playlistId":3,"configVersion":5,"serverTime":"2026-11-05T15:00:00.123456Z","updateCheckToken":0,"campaignId":null,"planVersion":4242}""")

        assertEquals(4242L, desired.planVersion)
        assertEquals(1_793_890_800_123L, desired.serverTimeMs)
    }

    @Test
    fun `server time accepts whole seconds, numbers and refuses what it does not understand`() {
        assertEquals(1_793_890_800_000L, Json.parseIsoUtc("2026-11-05T15:00:00Z"))
        assertEquals(1_793_890_800_500L, Json.parseIsoUtc("2026-11-05T15:00:00.5Z"))
        assertEquals(1_793_890_800_000L, Json.parseServerTime(1_793_890_800.0))
        assertEquals(0L, Json.parseIsoUtc("2026-11-05T15:00:00-03:00"))
        assertEquals(0L, Json.parseIsoUtc("ontem"))
        assertEquals(0L, Json.parseServerTime(null))
    }

    @Test
    fun `an old server without the new fields still parses`() {
        val desired = Json.parseDesired("""{"playlistId":3,"configVersion":5}""")

        assertEquals(0L, desired.planVersion)
        assertEquals(0L, desired.serverTimeMs)
    }
}

class PlanPrefetchTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()
    private val store = InMemoryStore()
    private val notices = mutableListOf<String>()
    private var now = 0L
    private val clock = TrustedClock(wall = { now }, uptime = { now })

    private fun sync(free: Long = Long.MAX_VALUE) =
        ConfigSync(api, downloader, MediaCache(File(folder.root, "media")), store, freeBytes = { free }, reserveBytes = 0, clock = clock, onNotice = { notices += it })

    private fun serve(config: PlayerConfig) {
        config.retainable().forEach { downloader.files[it.url] = if (it.mediaId == 1L) imgBase else if (it.mediaId == 2L) imgPromo else imgNight }
        api.configResponse = { config }
    }

    @Test
    fun `downloads the media of upcoming campaigns after the current content`() = runTest {
        serve(baseConfig())

        sync().sync("tok")

        assertEquals(listOf("/api/v1/media/1/file", "/api/v1/media/2/file"), downloader.requested)
        assertTrue(File(folder.root, "media").listFiles()!!.size == 2)
    }

    @Test
    fun `media already on the device is not downloaded again`() = runTest {
        serve(baseConfig())
        val sync = sync()
        sync.sync("tok")
        downloader.requested.clear()

        sync.sync("tok")

        assertTrue(downloader.requested.isEmpty())
    }

    @Test
    fun `keeps the media of the schedule when the content on air changes`() = runTest {
        serve(baseConfig())
        val sync = sync()
        sync.sync("tok")

        // a campanha entra no ar: a configuração atual passa a ser a dela e a base continua na agenda
        val promoNow = baseConfig().copy(playlistId = 2, configVersion = 2, items = listOf(itemFor(2, imgPromo)), campaignId = 9, campaignName = "Promo")
        serve(promoNow)
        downloader.requested.clear()
        sync.sync("tok")

        assertTrue(downloader.requested.isEmpty())
        assertEquals(2, File(folder.root, "media").listFiles()!!.size)
    }

    @Test
    fun `without space the future media stay out, the current content is safe and the panel is told`() = runTest {
        serve(baseConfig())
        val small = itemFor(1, imgBase).fileSize

        // cabe só a mídia que está no ar
        sync(free = small).sync("tok")

        assertEquals(listOf("/api/v1/media/1/file"), downloader.requested)
        assertTrue(notices.single().startsWith("Sem espaço para guardar toda a agenda offline: 1 mídia(s)"))
    }

    @Test
    fun `a failing download of a future media never fails the sync of the current content`() = runTest {
        val config = baseConfig()
        api.configResponse = { config }
        downloader.files["/api/v1/media/1/file"] = imgBase // a mídia 2 (da agenda) não existe no servidor

        val synced = sync().sync("tok")

        assertEquals(1L, synced.playlistId)
        assertTrue(notices.single().startsWith("Não foi possível guardar 1 mídia(s) da agenda offline"))
    }

    @Test
    fun `retries the failed schedule media a few minutes later`() = runTest {
        val config = baseConfig()
        api.configResponse = { config }
        downloader.files["/api/v1/media/1/file"] = imgBase
        val sync = sync()
        sync.sync("tok")
        store.save(rawJson(config))
        val desired = Desired(1, 1, planVersion = 77)

        assertFalse(sync.needsSync(desired))
        now += 6 * MIN
        assertTrue(sync.needsSync(desired))

        downloader.files["/api/v1/media/2/file"] = imgPromo
        sync.sync("tok")
        assertFalse(sync.needsSync(desired))
    }

    @Test
    fun `a new plan version asks for a new sync even when what is on air is the same`() = runTest {
        serve(baseConfig())
        val sync = sync()
        sync.sync("tok")

        assertFalse(sync.needsSync(Desired(1, 1, planVersion = 77)))
        assertTrue(sync.needsSync(Desired(1, 1, planVersion = 78)))
        // a TV tinha agenda e o servidor diz que não há mais campanhas
        assertTrue(sync.needsSync(Desired(1, 1, planVersion = 0)))
    }

    @Test
    fun `renews the schedule when less than five days are left, at most once an hour`() = runTest {
        serve(baseConfig(plan(until = 7 * DAY)))
        val sync = sync()
        now = 0
        sync.sync("tok")
        val desired = Desired(1, 1, planVersion = 77)

        now = 1 * DAY
        assertFalse(sync.needsSync(desired))
        now = 3 * DAY
        assertTrue(sync.needsSync(desired))

        // o servidor responde com a mesma janela curta (relógio estranho): não vira laço
        sync.sync("tok")
        now += 10 * MIN
        assertFalse(sync.needsSync(desired))
        now += 2 * HOUR
        assertTrue(sync.needsSync(desired))
    }

    @Test
    fun `an old server without plan keeps working as before`() = runTest {
        serve(baseConfig(plan = null))
        val sync = sync()
        sync.sync("tok")

        assertFalse(sync.needsSync(Desired(1, 1)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()
    private val telemetry = Telemetry()
    private val timing = Timing(activationPollMs = 5_000, heartbeatMs = 30_000, retryMs = 10_000, disabledPollMs = 30_000, offlineCheckMs = 15_000, offlineAfterMs = 75_000)
    private var online = true

    /** A TV sempre está 40 min atrasada em relação ao servidor: a agenda só acerta se usar a hora do servidor. */
    private fun TestScope.engine(store: InMemoryStore, serverStart: Long, planned: Boolean = true): PlayerEngine {
        val clock = TrustedClock(wall = { serverStart - 40 * MIN + testScheduler.currentTime }, uptime = { testScheduler.currentTime })
        val cache = MediaCache(File(folder.root, "media"))
        api.registerResponse = { if (online) active() else throw IOException("sem internet") }
        api.heartbeatResponse = {
            if (!online) throw IOException("sem internet")
            Desired(1, 1, planVersion = if (planned) 77 else 0, serverTimeMs = serverStart + testScheduler.currentTime)
        }
        api.configResponse = { baseConfig(if (planned) plan() else null) }
        val sync = ConfigSync(api, downloader, cache, store, clock = clock)
        return PlayerEngine(api, sync, store, token = { "tok" }, playerVersion = "0.9.0", timing = timing, telemetry = telemetry, clock = clock)
    }

    private fun TestScope.start(engine: PlayerEngine) {
        backgroundScope.launch { engine.run() }
        runCurrent()
    }

    private fun playing(engine: PlayerEngine): PlayerConfig? = (engine.state.value as? EngineState.Playing)?.config

    private fun prepared(config: PlayerConfig = baseConfig()): InMemoryStore {
        downloader.files["/api/v1/media/1/file"] = imgBase
        downloader.files["/api/v1/media/2/file"] = imgPromo
        return InMemoryStore(config)
    }

    /** Liga com internet (o primeiro contato acerta o relógio) e depois a internet cai. */
    private fun TestScope.startThenGoOffline(engine: PlayerEngine) {
        start(engine)
        online = false
    }

    @Test
    fun `without internet the TV switches to the campaign at the right time and back when it ends`() = runTest {
        val engine = engine(prepared(), serverStart = 5 * MIN)
        startThenGoOffline(engine)
        assertEquals(1L, playing(engine)?.playlistId) // 5 min: base

        advanceTimeBy(6 * MIN) // 11 min no servidor: a campanha começou
        runCurrent()
        assertEquals(2L, playing(engine)?.playlistId)
        assertEquals("Promo", playing(engine)?.campaignName)

        advanceTimeBy(10 * MIN) // 21 min: acabou
        runCurrent()
        assertEquals(1L, playing(engine)?.playlistId)
        assertNull(playing(engine)?.campaignId)
    }

    @Test
    fun `the TV opens already in the right interval of the schedule, even before any contact`() = runTest {
        val store = prepared()
        // desligada e ligada de novo às 12 min do servidor; o relógio dela está 40 min atrás, mas a diferença guardada corrige
        val offset = object : ClockOffsetStore { override var clockOffsetMs = 40 * MIN }
        val clock = TrustedClock(wall = { 12 * MIN - 40 * MIN }, uptime = { 0 }, store = offset)
        val engine = PlayerEngine(api, ConfigSync(api, downloader, MediaCache(File(folder.root, "media")), store, clock = clock), store, token = { "tok" }, playerVersion = "0.9.0", timing = timing, telemetry = telemetry, clock = clock)
        online = false

        start(engine)

        assertEquals(2L, playing(engine)?.playlistId)
        assertEquals(9L, playing(engine)?.campaignId)
    }

    @Test
    fun `with internet the server decides, not the schedule`() = runTest {
        val engine = engine(prepared(), serverStart = 11 * MIN) // pela agenda já seria a campanha

        start(engine)
        advanceTimeBy(5 * MIN)
        runCurrent()

        // o servidor diz que continua a base (ex.: o administrador pausou a campanha agora)
        assertEquals(1L, playing(engine)?.playlistId)
    }

    @Test
    fun `when the internet returns the TV follows the server again and the screen does not restart if it already agrees`() = runTest {
        val engine = engine(prepared(), serverStart = 5 * MIN)
        startThenGoOffline(engine)
        advanceTimeBy(6 * MIN) // campanha pela agenda
        runCurrent()
        assertEquals(2L, playing(engine)?.playlistId)

        // volta a internet: o servidor confirma a campanha (configuração dela, com o mesmo conteúdo)
        val promo = baseConfig().copy(playlistId = 2, configVersion = 2, items = listOf(itemFor(2, imgPromo)), campaignId = 9, campaignName = "Promo")
        api.configResponse = { promo }
        api.heartbeatResponse = { Desired(2, 2, campaignId = 9, planVersion = 77, serverTimeMs = 5 * MIN + testScheduler.currentTime) }
        online = true
        val shownBefore = engine.state.value
        advanceTimeBy(40_000)
        runCurrent()

        assertEquals(2L, playing(engine)?.playlistId)
        assertEquals(shownBefore, engine.state.value) // o mesmo conteúdo: a tela não recomeça
    }

    @Test
    fun `when the internet returns and the server says something else, the server wins`() = runTest {
        val engine = engine(prepared(), serverStart = 5 * MIN)
        startThenGoOffline(engine)
        advanceTimeBy(6 * MIN)
        runCurrent()
        assertEquals(2L, playing(engine)?.playlistId) // pela agenda: campanha

        // o administrador pausou a campanha enquanto a TV estava sem internet
        api.heartbeatResponse = { Desired(1, 1, planVersion = 78, serverTimeMs = 11 * MIN + testScheduler.currentTime) }
        api.configResponse = { baseConfig(plan(version = 78)) }
        online = true
        advanceTimeBy(40_000)
        runCurrent()

        assertEquals(1L, playing(engine)?.playlistId)
    }

    @Test
    fun `when the internet returns and the server just confirms what was stored, the screen goes back to the stored content`() = runTest {
        val engine = engine(prepared(), serverStart = 5 * MIN)
        startThenGoOffline(engine)
        advanceTimeBy(6 * MIN)
        runCurrent()
        assertEquals(2L, playing(engine)?.playlistId) // agenda: campanha

        // o servidor confirma exatamente a configuração guardada (a base): a campanha da agenda não vale mais
        api.heartbeatResponse = { Desired(1, 1, planVersion = 77, serverTimeMs = 11 * MIN + testScheduler.currentTime) }
        online = true
        advanceTimeBy(40_000)
        runCurrent()

        assertEquals(1L, playing(engine)?.playlistId)
    }

    @Test
    fun `the history tells when the TV went offline, which campaign is on, and when it came back`() = runTest {
        val engine = engine(prepared(), serverStart = 5 * MIN)
        startThenGoOffline(engine)

        advanceTimeBy(6 * MIN)
        runCurrent()
        online = true
        advanceTimeBy(40_000)
        runCurrent()

        val messages = (api.reportsSeen.flatMap { it.events } + telemetry.snapshot().events).filter { it.level == Level.INFO }.map { it.message }
        assertTrue(messages.toString(), messages.any { it.startsWith("Sem contato com o servidor: a TV segue a agenda guardada") })
        assertTrue(messages.toString(), messages.any { it.contains("Agenda offline: no ar a campanha \"Promo\"") })
        assertTrue(messages.toString(), messages.any { it.startsWith("Contato com o servidor restabelecido") })
    }

    @Test
    fun `each heartbeat tells the panel until when the stored schedule is valid`() = runTest {
        val engine = engine(prepared(), serverStart = 5 * MIN)

        start(engine)

        assertEquals(7 * DAY, api.planUntilSeen.first())
    }

    @Test
    fun `a TV without a plan tells zero`() = runTest {
        val engine = engine(prepared(baseConfig(plan = null)), serverStart = 5 * MIN, planned = false)

        start(engine)

        assertEquals(0L, api.planUntilSeen.first())
    }

    @Test
    fun `a TV without a plan keeps showing what it had, as before`() = runTest {
        val engine = engine(prepared(baseConfig(plan = null)), serverStart = 11 * MIN, planned = false)
        startThenGoOffline(engine)

        advanceTimeBy(10 * MIN)
        runCurrent()

        assertEquals(1L, playing(engine)?.playlistId)
    }

    @Test
    fun `an expired plan keeps the last content instead of showing nothing`() = runTest {
        val engine = engine(prepared(baseConfig(plan(until = 30 * MIN))), serverStart = 25 * MIN)
        api.configResponse = { baseConfig(plan(until = 30 * MIN)) }
        startThenGoOffline(engine)

        advanceTimeBy(20 * MIN) // 45 min: depois do fim da agenda
        runCurrent()

        assertNotEquals(EngineState.NoContent, engine.state.value)
        assertEquals(1L, playing(engine)?.playlistId)
    }
}
