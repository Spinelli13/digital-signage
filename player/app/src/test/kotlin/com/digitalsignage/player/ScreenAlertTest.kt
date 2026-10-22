package com.digitalsignage.player

import com.digitalsignage.player.alert.AlertLook
import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.ScreenAlert
import com.digitalsignage.player.engine.PlayerEngine
import com.digitalsignage.player.engine.Timing
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Telemetry
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** O aviso em tela cheia ("Alertar agora"): como a TV lê o heartbeat, conta o prazo e tira o aviso do ar. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenAlertTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()
    private val telemetry = Telemetry()
    private val timing = Timing(activationPollMs = 5_000, heartbeatMs = 30_000, retryMs = 10_000, disabledPollMs = 30_000)
    private val img = "imagem".toByteArray()

    private val evacuation = ScreenAlert(7, "Atenção", "Evacuação: sigam para a saída", "URGENT")

    private fun TestScope.engine(): PlayerEngine {
        val item = itemFor(1, img)
        downloader.files[item.url] = img
        api.registerResponse = { active() }
        api.configResponse = { configOf(5, 1, item) }
        val store = InMemoryStore()
        val cache = MediaCache(File(folder.root, "media"))
        // o relógio da TV anda junto com o tempo virtual do teste
        val clock = TrustedClock(uptime = { testScheduler.currentTime })
        return PlayerEngine(api, ConfigSync(api, downloader, cache, store), store, token = { "tok" }, playerVersion = "0.2.0", timing = timing, telemetry = telemetry, clock = clock)
    }

    private fun TestScope.start(engine: PlayerEngine) {
        backgroundScope.launch { engine.run() }
        runCurrent()
    }

    private fun withAlert(remaining: Long, alert: ScreenAlert? = evacuation) =
        Desired(5, 1, alert = alert, alertRemainingSeconds = remaining)

    @Test
    fun `shows the alert the server sent and counts the remaining time by itself`() = runTest {
        api.heartbeatResponse = { withAlert(600) }
        val engine = engine()
        assertNull(engine.alert.value)
        assertEquals(0L, engine.alertRemainingMs())

        start(engine)

        assertEquals(evacuation, engine.alert.value)
        assertEquals(600_000L, engine.alertRemainingMs())
        // sem nova resposta o tempo só diminui: o aviso sai sozinho no prazo, mesmo sem internet
        api.heartbeatResponse = { throw IOException("sem internet") }
        advanceTimeBy(100_000)
        assertEquals(500_000L, engine.alertRemainingMs())
        advanceTimeBy(500_000)
        assertEquals(0L, engine.alertRemainingMs())
    }

    @Test
    fun `keeps the same alert untouched when the next heartbeat repeats it, only renewing the time left`() = runTest {
        var remaining = 120L
        api.heartbeatResponse = { withAlert(remaining) }
        val engine = engine()
        val seen = mutableListOf<ScreenAlert?>()
        backgroundScope.launch { engine.alert.collect { seen += it } }
        start(engine)

        remaining = 90
        advanceTimeBy(30_001)
        runCurrent()

        // uma só vez na tela: sem piscar a cada heartbeat
        assertEquals(listOf(null, evacuation), seen)
        // o prazo foi renovado pelo segundo heartbeat (em 30 s): 90 s a partir dali, menos o 1 ms que já passou
        assertEquals(89_999L, engine.alertRemainingMs())
    }

    @Test
    fun `swaps to another alert and clears when the server stops sending one`() = runTest {
        val sale = ScreenAlert(8, null, "Promoção relâmpago", "INFO")
        var current: ScreenAlert? = evacuation
        api.heartbeatResponse = { withAlert(300, current) }
        val engine = engine()
        start(engine)
        assertEquals(evacuation, engine.alert.value)

        current = sale
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(sale, engine.alert.value)

        current = null
        api.heartbeatResponse = { Desired(5, 1) }
        advanceTimeBy(30_001)
        runCurrent()
        assertNull(engine.alert.value)
        assertEquals(0L, engine.alertRemainingMs())
    }

    @Test
    fun `an alert with no time left from an odd server still shows for at least a second, then goes`() = runTest {
        api.heartbeatResponse = { withAlert(0) }
        val engine = engine()

        start(engine)

        assertEquals(evacuation, engine.alert.value)
        assertEquals(1_000L, engine.alertRemainingMs())
        advanceTimeBy(1_500)
        assertEquals(0L, engine.alertRemainingMs())
    }

    @Test
    fun `tells the history when an alert goes up and when it goes down`() = runTest {
        var current: ScreenAlert? = evacuation
        api.heartbeatResponse = { withAlert(300, current) }
        val engine = engine()
        start(engine)
        current = null
        advanceTimeBy(30_001)
        runCurrent()

        // o que já foi entregue nos heartbeats mais o que ainda espera o próximo
        val messages = (api.reportsSeen.flatMap { it.events } + telemetry.snapshot().events).map { it.message }
        assertTrue(messages.any { it.startsWith("Aviso em tela cheia no ar") && it.contains("Evacuação") })
        assertTrue(messages.contains("O aviso em tela cheia saiu do ar."))
    }

    @Test
    fun `reads the alert from the heartbeat json and ignores what is not a usable alert`() {
        val full = Json.parseDesired(
            """{"playlistId":4,"configVersion":9,"serverTime":"2026-10-19T12:00:00Z",
               "alert":{"id":3,"title":"ATENÇÃO","message":"Fechamos mais cedo","tone":"WARNING","remainingSeconds":540}}""",
        )
        assertEquals(ScreenAlert(3, "ATENÇÃO", "Fechamos mais cedo", "WARNING"), full.alert)
        assertEquals(540L, full.alertRemainingSeconds)

        val noTitle = Json.parseDesired("""{"playlistId":4,"configVersion":9,"serverTime":"x","alert":{"id":3,"title":null,"message":"Oi","tone":"INFO","remainingSeconds":60}}""")
        assertNull(noTitle.alert?.title)

        for (raw in listOf(
            """{"playlistId":4,"configVersion":9,"serverTime":"x"}""", // servidor antigo
            """{"playlistId":4,"configVersion":9,"serverTime":"x","alert":null}""", // nenhum aviso
            """{"playlistId":4,"configVersion":9,"serverTime":"x","alert":{"id":3,"message":"   ","tone":"URGENT","remainingSeconds":60}}""", // sem texto
            """{"playlistId":4,"configVersion":9,"serverTime":"x","alert":{"message":"Sem id","tone":"URGENT","remainingSeconds":60}}""",
        )) {
            val desired = Json.parseDesired(raw)
            assertNull(raw, desired.alert)
            assertEquals(0L, desired.alertRemainingSeconds)
        }
    }

    @Test
    fun `colours the alert by urgency and treats an unknown urgency as the strongest`() {
        assertEquals(0xFFB3261E.toInt(), AlertLook.background("URGENT"))
        assertEquals(0xFFB45309.toInt(), AlertLook.background("WARNING"))
        assertEquals(0xFF5B21B6.toInt(), AlertLook.background("info"))
        assertEquals(AlertLook.background("URGENT"), AlertLook.background("ALGO_NOVO"))
        assertTrue(AlertLook.background("WARNING") != AlertLook.background("URGENT"))
    }

    @Test
    fun `writes the title in capitals and drops a blank one`() {
        assertEquals("ATENÇÃO", AlertLook.heading("  Atenção "))
        assertNull(AlertLook.heading("   "))
        assertNull(AlertLook.heading(null))
        assertFalse(AlertLook.heading("x").isNullOrEmpty())
    }
}
