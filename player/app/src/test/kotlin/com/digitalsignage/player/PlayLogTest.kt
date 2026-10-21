package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.engine.PlayerEngine
import com.digitalsignage.player.engine.Timing
import com.digitalsignage.player.proof.MemoryPlayLogState
import com.digitalsignage.player.proof.PlayHash
import com.digitalsignage.player.proof.PlayInfo
import com.digitalsignage.player.proof.PlayLog
import com.digitalsignage.player.proof.PlayRecord
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.Telemetry
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val DIRECT = Executor { it.run() }

private fun play(mediaId: Long = 5, name: String = "Promoção de verão", startedAtMs: Long = 1_762_354_800_123, durationMs: Long = 10_012, completed: Boolean = true) =
    PlayInfo(
        deviceId = 42, mediaId = mediaId, mediaName = name, mediaType = "IMAGE", playlistId = 7, campaignId = null, campaignName = null,
        zoneName = "Principal", startedAtMs = startedAtMs, durationMs = durationMs, plannedMs = 10_000, completed = completed,
    )

class PlayLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun log(state: MemoryPlayLogState = MemoryPlayLogState(), max: Int = PlayLog.MAX_PENDING, dropped: (Int) -> Unit = {}) =
        PlayLog(File(folder.root, "playlog.jsonl"), state, max, dropped, DIRECT)

    /** O mesmo vetor que o servidor confere em PlayProofTest (a conta tem de dar igual nos dois lados). */
    @Test
    fun `the hash matches the server's calculation`() {
        val first = PlayHash.compute(PlayHash.GENESIS, 1, 42, 5, "Promoção de verão", "IMAGE", 7, null, "Principal", 1_762_354_800_123, 10_012, 10_000, true)
        val second = PlayHash.compute(first, 2, 42, 6, "Cardápio", "VIDEO", 7, 55, "Lateral", 1_762_354_810_135, 8_000, null, false)

        assertEquals("9d538e07afa7754a5276f62c82025327f52d320a9aa0b9f498dc0be7b89807ec", first)
        assertEquals("1a93acc86cac654c535ae7f001c01d4ea206e984b78507c216de3bb3611e6b37", second)
    }

    @Test
    fun `each play gets the next number and links to the previous hash`() {
        val log = log()

        log.record(play(startedAtMs = 1_000))
        log.record(play(mediaId = 6, startedAtMs = 2_000))
        log.record(play(mediaId = 7, startedAtMs = 3_000))

        val records = log.pending(10)
        assertEquals(listOf(1L, 2L, 3L), records.map { it.seq })
        assertEquals(PlayHash.GENESIS, records[0].prevHash)
        assertEquals(records[0].hash, records[1].prevHash)
        assertEquals(records[1].hash, records[2].prevHash)
        assertEquals(3, log.pendingCount())
        // o hash guardado é o que se recalcula a partir dos campos
        val first = records[0]
        assertEquals(
            first.hash,
            PlayHash.compute(first.prevHash, first.seq, 42, first.mediaId, first.mediaName, first.mediaType, first.playlistId, first.campaignId, first.zoneName, first.startedAtMs, first.durationMs, first.plannedMs, first.completed),
        )
    }

    @Test
    fun `changing any field changes the hash`() {
        val base = PlayHash.compute(PlayHash.GENESIS, 1, 42, 5, "A", "IMAGE", 7, null, "Principal", 1000, 10_000, 10_000, true)

        assertNotEquals(base, PlayHash.compute(PlayHash.GENESIS, 1, 42, 5, "A", "IMAGE", 7, null, "Principal", 1000, 10_001, 10_000, true))
        assertNotEquals(base, PlayHash.compute(PlayHash.GENESIS, 1, 42, 5, "A", "IMAGE", 7, null, "Principal", 1001, 10_000, 10_000, true))
        assertNotEquals(base, PlayHash.compute(PlayHash.GENESIS, 2, 42, 5, "A", "IMAGE", 7, null, "Principal", 1000, 10_000, 10_000, true))
        assertNotEquals(base, PlayHash.compute(PlayHash.GENESIS, 1, 42, 5, "A", "IMAGE", 7, null, "Principal", 1000, 10_000, 10_000, false))
        assertNotEquals(base, PlayHash.compute("1".repeat(64), 1, 42, 5, "A", "IMAGE", 7, null, "Principal", 1000, 10_000, 10_000, true))
    }

    @Test
    fun `the diary survives a restart and the chain continues from where it stopped`() {
        val state = MemoryPlayLogState()
        log(state).apply {
            record(play(startedAtMs = 1_000))
            record(play(startedAtMs = 2_000))
        }

        val reopened = log(state)
        reopened.record(play(startedAtMs = 3_000))

        val records = reopened.pending(10)
        assertEquals(listOf(1L, 2L, 3L), records.map { it.seq })
        assertEquals(records[1].hash, records[2].prevHash)
    }

    @Test
    fun `the file wins when the saved state is behind it (a drop between the two writes)`() {
        val state = MemoryPlayLogState()
        log(state).apply {
            record(play())
            record(play())
        }
        state.nextSeq = 1 // o estado ficou para trás
        state.lastHash = PlayHash.GENESIS

        val reopened = log(state)
        reopened.record(play())

        assertEquals(listOf(1L, 2L, 3L), reopened.pending(10).map { it.seq })
        assertEquals(reopened.pending(10)[1].hash, reopened.pending(10)[2].prevHash)
    }

    @Test
    fun `acknowledged records leave the device but the numbering never goes back`() {
        val state = MemoryPlayLogState()
        val log = log(state)
        repeat(5) { log.record(play(startedAtMs = it * 1000L)) }

        log.acknowledge(3)

        assertEquals(listOf(4L, 5L), log.pending(10).map { it.seq })
        log.acknowledge(5)
        assertEquals(0, log.pendingCount())
        log.record(play())
        assertEquals(listOf(6L), log.pending(10).map { it.seq })
        assertEquals(6L, log(state).pending(10).single().seq) // também depois de reabrir
    }

    @Test
    fun `a torn last line from a power cut is ignored`() {
        val file = File(folder.root, "playlog.jsonl")
        val state = MemoryPlayLogState()
        PlayLog(file, state, executor = DIRECT).apply {
            record(play())
            record(play())
        }
        file.appendText("{\"seq\":3,\"mediaId\":5,\"mediaNa")

        val reopened = PlayLog(file, state, executor = DIRECT)

        assertEquals(listOf(1L, 2L), reopened.pending(10).map { it.seq })
    }

    @Test
    fun `without internet the diary only grows until the limit and then drops the oldest, saying so`() {
        var dropped = 0
        val log = log(max = 3, dropped = { dropped += it })

        repeat(5) { log.record(play(startedAtMs = it * 1000L)) }

        assertEquals(listOf(3L, 4L, 5L), log.pending(10).map { it.seq })
        assertEquals(2, dropped)
    }

    @Test
    fun `pending is returned in batches, oldest first`() {
        val log = log()
        repeat(7) { log.record(play(startedAtMs = it * 1000L)) }

        assertEquals(listOf(1L, 2L, 3L), log.pending(3).map { it.seq })
    }

    @Test
    fun `an interrupted play is kept with its real duration`() {
        val log = log()
        log.record(play(durationMs = 3_456, completed = false))

        val record = log.pending(1).single()
        assertFalse(record.completed)
        assertEquals(3_456L, record.durationMs)
    }

    @Test
    fun `json round trip keeps every field including the empty ones`() {
        val record = PlayRecord(9, null, "Sem mídia", "WEB", null, null, null, "Lateral", 5, 6, null, false, PlayHash.GENESIS, "a".repeat(64))

        assertEquals(record, PlayLog.fromJson(PlayLog.toJson(record)))
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayUploadTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()
    private val telemetry = Telemetry()
    private val timing = Timing(activationPollMs = 5_000, heartbeatMs = 30_000, retryMs = 10_000, disabledPollMs = 30_000)
    private lateinit var log: PlayLog

    private fun engine(): PlayerEngine {
        val store = InMemoryStore()
        val cache = MediaCache(File(folder.root, "media"))
        log = PlayLog(File(folder.root, "playlog.jsonl"), MemoryPlayLogState(), executor = DIRECT)
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(null, 0) }
        api.configResponse = { configOf(null, 0) }
        return PlayerEngine(api, ConfigSync(api, downloader, cache, store), store, token = { "tok" }, playerVersion = "0.8.0", timing = timing, telemetry = telemetry, plays = log, wallClock = { 1_700_000_000_000 })
    }

    @Test
    fun `the diary is delivered after a heartbeat and then leaves the device`() = runTest {
        val engine = engine()
        repeat(3) { log.record(play(startedAtMs = it * 1000L)) }

        backgroundScope.launch { engine.run() }
        runCurrent()

        assertEquals(1, api.playBatches.size)
        assertEquals(listOf(1L, 2L, 3L), api.playBatches[0].map { it.seq })
        assertEquals(1_700_000_000_000, api.playDeviceTimes[0])
        assertEquals(0, log.pendingCount())
    }

    @Test
    fun `without internet the records wait and go out on the next cycle`() = runTest {
        val engine = engine()
        log.record(play())
        var online = false
        api.sendPlaysResponse = { if (online) it.last().seq else throw IOException("sem internet") }

        backgroundScope.launch { engine.run() }
        runCurrent()
        assertEquals(1, log.pendingCount())

        online = true
        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(0, log.pendingCount())
    }

    @Test
    fun `a batch the server refuses is dropped with a warning so it never blocks the next ones`() = runTest {
        val engine = engine()
        log.record(play())
        api.sendPlaysResponse = { throw ApiException(400, "hash inválido") }

        backgroundScope.launch { engine.run() }
        runCurrent()

        assertEquals(0, log.pendingCount())
        val warnings = telemetry.snapshot().events.filter { it.level == Level.WARN }
        assertTrue(warnings.any { it.message.contains("recusou 1 registro") })
    }

    @Test
    fun `a server error keeps the records for later`() = runTest {
        val engine = engine()
        log.record(play())
        api.sendPlaysResponse = { throw ApiException(503, "acordando") }

        backgroundScope.launch { engine.run() }
        runCurrent()

        assertEquals(1, log.pendingCount())
    }

    @Test
    fun `a big backlog goes out in batches of 200 in the same cycle`() = runTest {
        val engine = engine()
        repeat(450) { log.record(play(startedAtMs = it * 10L)) }

        backgroundScope.launch { engine.run() }
        runCurrent()

        assertEquals(listOf(200, 200, 50), api.playBatches.map { it.size })
        assertEquals(0, log.pendingCount())
    }

}
