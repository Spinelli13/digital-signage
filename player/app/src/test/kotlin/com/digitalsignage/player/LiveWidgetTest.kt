package com.digitalsignage.player

import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.WidgetSpec
import com.digitalsignage.player.time.TrustedClock
import com.digitalsignage.player.widget.Beeper
import com.digitalsignage.player.widget.LiveWidgetFeed
import com.digitalsignage.player.widget.WidgetStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LiveWidgetTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class CountingBeeper : Beeper {
        var beeps = 0
        override fun beep() {
            beeps++
        }
    }

    private val api = FakePlayerApi()
    private val beeper = CountingBeeper()
    private val clock = TrustedClock(wall = { 1_000_000L }, uptime = { 0L })
    private val store by lazy { WidgetStore(File(folder.root, "widgets")) }
    private val feed by lazy { LiveWidgetFeed(api, { "tok" }, store, clock, beeper) }

    private fun board(sound: Boolean = true) = itemFor(5, ByteArray(0)).copy(
        name = "Painel",
        type = "WIDGET",
        url = "/api/v1/player/widgets/5/data",
        durationSeconds = 15,
        widget = WidgetSpec("QUEUE", JSONObject().put("sound", sound).put("calls", 5).toString()),
    )

    private fun answer(label: String, calledAtMs: Long, callCount: Int = 1, waiting: Int = 2) =
        JSONObject()
            .put("kind", "QUEUE")
            .put("serverTimeMs", 2_000_000L)
            .put(
                "data",
                JSONObject()
                    .put("waiting", waiting)
                    .put("calls", JSONArray().put(JSONObject().put("label", label).put("counter", "Guichê 1").put("calledAtMs", calledAtMs).put("callCount", callCount).put("status", "CALLING"))),
            )
            .toString()

    @Test
    fun `the call board is a live widget that needs data, the others are not`() {
        assertTrue(WidgetSpec("QUEUE", "{}").live)
        assertTrue(WidgetSpec("QUEUE", "{}").needsData)
        assertFalse(WidgetSpec("PRICES", "{}").live)
        assertFalse(WidgetSpec("CLOCK", "{}").live)
    }

    @Test
    fun `the first look at the board delivers the data and does not beep, a new call does`() = runTest {
        api.widgetResponse = { answer("A001", 1_000) }
        val first = feed.fetch(board())

        assertNotNull(first)
        val payload = JSONObject(first!!)
        assertEquals("A001", payload.getJSONObject("data").getJSONArray("calls").getJSONObject(0).getString("label"))
        assertTrue(payload.has("nowMs"))
        assertEquals(0, beeper.beeps)

        // nada mudou: nenhum aviso
        feed.fetch(board())
        assertEquals(0, beeper.beeps)

        // outra senha chamada: avisa uma vez
        api.widgetResponse = { answer("A002", 2_000) }
        feed.fetch(board())
        feed.fetch(board())
        assertEquals(1, beeper.beeps)
    }

    @Test
    fun `calling the same ticket again beeps again`() = runTest {
        api.widgetResponse = { answer("A001", 1_000, callCount = 1) }
        feed.fetch(board())

        api.widgetResponse = { answer("A001", 5_000, callCount = 2) }
        feed.fetch(board())

        assertEquals(1, beeper.beeps)
    }

    @Test
    fun `with the sound turned off in the widget nothing beeps`() = runTest {
        api.widgetResponse = { answer("A001", 1_000) }
        feed.fetch(board(sound = false))
        api.widgetResponse = { answer("A002", 2_000) }
        feed.fetch(board(sound = false))

        assertEquals(0, beeper.beeps)
    }

    @Test
    fun `an empty board never beeps and the data is kept for offline`() = runTest {
        api.widgetResponse = { JSONObject().put("data", JSONObject().put("calls", JSONArray()).put("waiting", 0)).toString() }
        feed.fetch(board())
        feed.fetch(board())

        assertEquals(0, beeper.beeps)
        assertNotNull(store.load(5))
    }

    @Test
    fun `a response without data is ignored and a network error reaches the caller`() = runTest {
        api.widgetResponse = { JSONObject().put("data", JSONObject.NULL).put("error", "sem dados").toString() }
        assertNull(feed.fetch(board()))

        api.widgetResponse = { throw IOException("offline") }
        var failed = false
        try {
            feed.fetch(board())
        } catch (e: IOException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(0, beeper.beeps)
    }

    @Test
    fun `the page receives the server time, so the highlight uses the real clock`() = runTest {
        api.widgetResponse = { answer("A001", 1_000) }
        val payload = JSONObject(feed.fetch(board())!!)

        // a hora do servidor (2.000.000) foi aprendida na consulta
        assertTrue(payload.getLong("nowMs") >= 2_000_000L)
    }
}
