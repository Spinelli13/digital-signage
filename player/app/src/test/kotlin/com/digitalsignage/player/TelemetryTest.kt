package com.digitalsignage.player

import com.digitalsignage.player.telemetry.HeartbeatReport
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.PlayCount
import com.digitalsignage.player.telemetry.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTest {

    private var now = 1_000_000L
    private fun telemetry(maxQueued: Int = 100, dedupeMs: Long = 5 * 60_000L, maxEvents: Int = 30, maxPlays: Int = 100) =
        Telemetry(clock = { now }, maxQueued = maxQueued, dedupeMs = dedupeMs, maxEventsPerReport = maxEvents, maxPlaysPerReport = maxPlays)

    // ---------- ocorrências ----------

    @Test
    fun `keeps the events in the order they happened with the time they happened`() {
        val t = telemetry()

        t.event(Level.ERROR, "primeiro")
        now += 5_000
        t.event(Level.WARN, "segundo")

        val events = t.snapshot().events
        assertEquals(listOf("primeiro", "segundo"), events.map { it.message })
        assertEquals(listOf(Level.ERROR, Level.WARN), events.map { it.level })
        assertEquals(listOf(1_000_000L, 1_005_000L), events.map { it.atMillis })
    }

    @Test
    fun `ignores blank messages and trims and truncates long ones to what the server accepts`() {
        val t = telemetry()

        t.event(Level.INFO, "   ")
        t.event(Level.INFO, "  ok  ")
        t.event(Level.ERROR, "x".repeat(900))

        val messages = t.snapshot().events.map { it.message }
        assertEquals(2, messages.size)
        assertEquals("ok", messages[0])
        assertEquals(Telemetry.MAX_MESSAGE, messages[1].length)
    }

    /** Sem internet o Player tenta de novo a cada poucos segundos; o histórico não pode encher da mesma frase. */
    @Test
    fun `does not repeat the same message within the window but accepts it again after`() {
        val t = telemetry(dedupeMs = 60_000)

        t.event(Level.ERROR, "Sem conexão")
        now += 10_000
        t.event(Level.ERROR, "Sem conexão")
        now += 49_000
        t.event(Level.ERROR, "Sem conexão")
        assertEquals(1, t.pendingEvents())

        now += 2_000
        t.event(Level.ERROR, "Sem conexão")
        assertEquals(2, t.pendingEvents())
    }

    @Test
    fun `the same text at a different level is a different event`() {
        val t = telemetry()

        t.event(Level.WARN, "disco quase cheio")
        t.event(Level.ERROR, "disco quase cheio")

        assertEquals(2, t.pendingEvents())
    }

    @Test
    fun `when the queue is full the oldest events are the ones dropped`() {
        val t = telemetry(maxQueued = 3)

        for (i in 1..5) {
            now += 1_000
            t.event(Level.INFO, "evento $i")
        }

        assertEquals(listOf("evento 3", "evento 4", "evento 5"), t.snapshot().events.map { it.message })
    }

    @Test
    fun `a report never carries more events than the server accepts and the backlog drains oldest first`() {
        val t = telemetry(maxEvents = 30)
        for (i in 1..45) {
            now += 1_000
            t.event(Level.INFO, "evento $i")
        }

        val first = t.snapshot()
        assertEquals(30, first.events.size)
        assertEquals("evento 1", first.events.first().message)

        t.acknowledge(first)

        val second = t.snapshot()
        assertEquals(15, second.events.size)
        assertEquals("evento 31", second.events.first().message)
        t.acknowledge(second)
        assertEquals(0, t.pendingEvents())
    }

    @Test
    fun `taking a snapshot does not remove anything until the server confirms`() {
        val t = telemetry()
        t.event(Level.ERROR, "falha")

        val one = t.snapshot()
        val two = t.snapshot() // o heartbeat falhou e foi refeito

        assertEquals(one, two)
        assertEquals(1, t.pendingEvents())
    }

    /** O que aconteceu enquanto o heartbeat estava no ar não pode ser apagado pela confirmação dele. */
    @Test
    fun `acknowledging a report keeps what arrived after the snapshot`() {
        val t = telemetry()
        t.event(Level.ERROR, "antes")
        val report = t.snapshot()

        now += 1_000
        t.event(Level.ERROR, "durante o envio")
        t.acknowledge(report)

        assertEquals(listOf("durante o envio"), t.snapshot().events.map { it.message })
    }

    // ---------- mídia na tela e exibições ----------

    @Test
    fun `reports the media on screen and forgets it when nothing is shown`() {
        val t = telemetry()
        assertNull(t.snapshot().currentMediaId)

        t.mediaShown(7)
        assertEquals(7L, t.snapshot().currentMediaId)

        t.mediaShown(null)
        assertNull(t.snapshot().currentMediaId)
    }

    @Test
    fun `counts plays per media and adds up the seconds`() {
        val t = telemetry()

        t.played(1, 10)
        t.played(1, 12)
        t.played(2, 30)

        assertEquals(listOf(PlayCount(1, 2, 22), PlayCount(2, 1, 30)), t.snapshot().plays)
    }

    @Test
    fun `a negative duration counts as a play of zero seconds`() {
        val t = telemetry()

        t.played(1, -5)

        assertEquals(listOf(PlayCount(1, 1, 0)), t.snapshot().plays)
    }

    @Test
    fun `acknowledging subtracts only what was sent, so plays during the heartbeat are not lost`() {
        val t = telemetry()
        t.played(1, 10)
        t.played(1, 10)
        t.played(2, 5)
        val report = t.snapshot()

        t.played(1, 10) // uma exibição terminou enquanto o heartbeat estava a caminho
        t.acknowledge(report)

        assertEquals(listOf(PlayCount(1, 1, 10)), t.snapshot().plays)
    }

    @Test
    fun `a report never carries more media entries than the server accepts`() {
        val t = telemetry(maxPlays = 3)
        for (id in 1L..5L) t.played(id, 1)

        val first = t.snapshot()
        assertEquals(listOf(1L, 2L, 3L), first.plays.map { it.mediaId })
        t.acknowledge(first)
        assertEquals(listOf(4L, 5L), t.snapshot().plays.map { it.mediaId })
    }

    @Test
    fun `an empty telemetry produces an empty report`() {
        val report = telemetry().snapshot()

        assertTrue(report.isEmpty)
        assertEquals(HeartbeatReport.EMPTY, report)
    }

}
