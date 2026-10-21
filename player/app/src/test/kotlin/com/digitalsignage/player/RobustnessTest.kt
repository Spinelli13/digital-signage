package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.memory.MemoryProfile
import com.digitalsignage.player.memory.TrimLevels
import com.digitalsignage.player.restart.RestartPolicy
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.sync.InsufficientSpaceException
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestartPolicyTest {

    @Test
    fun `the first crash reopens quickly and a series of crashes backs off`() {
        assertEquals(3_000, RestartPolicy.crashDelayMs(1))
        assertEquals(10_000, RestartPolicy.crashDelayMs(2))
        assertEquals(30_000, RestartPolicy.crashDelayMs(3))
        assertEquals(60_000, RestartPolicy.crashDelayMs(4))
        assertEquals(300_000, RestartPolicy.crashDelayMs(5))
        assertEquals("o teto é de 5 minutos", 300_000, RestartPolicy.crashDelayMs(50))
    }

    @Test
    fun `old crashes stop counting after the window`() {
        val now = 10_000_000L
        val history = listOf(now - RestartPolicy.CRASH_WINDOW_MS - 1, now - 60_000, now - 5_000)

        val recent = RestartPolicy.recentCrashes(history, now)

        assertEquals(listOf(now - 60_000, now - 5_000, now), recent)
        assertEquals(30_000, RestartPolicy.crashDelayMs(recent.size))
    }

    @Test
    fun `the watchdog reopens a Player that stopped giving signs of life`() {
        val now = 1_000_000L
        assertTrue(RestartPolicy.shouldRelaunch(now, enabled = true, lastAliveAt = now - RestartPolicy.STALE_AFTER_MS - 1, userExitUntil = 0))
        assertTrue("nunca abriu", RestartPolicy.shouldRelaunch(now, enabled = true, lastAliveAt = 0, userExitUntil = 0))
    }

    @Test
    fun `the watchdog leaves a healthy Player alone`() {
        val now = 1_000_000L
        assertFalse(RestartPolicy.shouldRelaunch(now, enabled = true, lastAliveAt = now - RestartPolicy.ALIVE_EVERY_MS, userExitUntil = 0))
    }

    @Test
    fun `someone who left on purpose gets the grace period, then the Player comes back`() {
        val left = 1_000_000L
        val until = left + RestartPolicy.USER_EXIT_GRACE_MS
        assertFalse(RestartPolicy.shouldRelaunch(left + 60_000, enabled = true, lastAliveAt = left, userExitUntil = until))
        assertTrue(RestartPolicy.shouldRelaunch(until + 1, enabled = true, lastAliveAt = left, userExitUntil = until))
    }

    @Test
    fun `turning auto restart off disables the watchdog`() {
        assertFalse(RestartPolicy.shouldRelaunch(1_000_000, enabled = false, lastAliveAt = 0, userExitUntil = 0))
    }

}

class MemoryProfileTest {

    @Test
    fun `devices flagged as low RAM, with a small heap or about 1 GB get the light profile`() {
        assertTrue(MemoryProfile.choose(lowRamFlag = true, memoryClassMb = 256, totalMb = 4_000).low)
        assertTrue(MemoryProfile.choose(lowRamFlag = false, memoryClassMb = 96, totalMb = 4_000).low)
        assertTrue(MemoryProfile.choose(lowRamFlag = false, memoryClassMb = 192, totalMb = 1_024).low)
    }

    @Test
    fun `comfortable devices keep full quality`() {
        val profile = MemoryProfile.choose(lowRamFlag = false, memoryClassMb = 256, totalMb = 2_048)

        assertFalse(profile.low)
        assertTrue(profile.secondaryVideo)
        assertEquals(0, profile.maxImageWidth)
        assertFalse(profile.rgb565)
    }

    @Test
    fun `the light profile saves memory where it hurts least`() {
        val low = MemoryProfile.LOW

        assertTrue(low.rgb565)
        assertFalse("um vídeo só, na zona principal", low.secondaryVideo)
        assertTrue(low.releaseAfterUse)
        assertTrue(low.bufferBytes < MemoryProfile.NORMAL.bufferBytes)
        assertEquals(1280, low.maxImageWidth)
    }

    @Test
    fun `unknown values do not wrongly mark a device as low`() {
        assertFalse(MemoryProfile.choose(lowRamFlag = false, memoryClassMb = 0, totalMb = 0).low)
    }

    @Test
    fun `the system asks for memory back from RUNNING_LOW on`() {
        assertFalse(TrimLevels.shouldRelease(5))
        assertTrue(TrimLevels.shouldRelease(TrimLevels.RUNNING_LOW))
        assertTrue(TrimLevels.shouldRelease(TrimLevels.RUNNING_CRITICAL))
        assertTrue(TrimLevels.shouldRelease(TrimLevels.UI_HIDDEN))
    }

}

class PlaybackFailureTest {

    @Test
    fun `decoder problems tell the person to resend in H264 up to 1080p`() {
        for (code in listOf(4001, 4002, 4003, 4004)) {
            val message = ErrorMessages.playbackFailure(code, "X")
            assertTrue(message, message.contains("H.264") && message.contains("1080p"))
        }
    }

    @Test
    fun `an unreadable container says the file is not recognised`() {
        assertTrue(ErrorMessages.playbackFailure(3003, "X").contains("não reconhece"))
    }

    @Test
    fun `other failures keep the technical code for support`() {
        assertEquals("falha ao reproduzir o vídeo (ERROR_CODE_TIMEOUT)", ErrorMessages.playbackFailure(1003, "ERROR_CODE_TIMEOUT"))
    }

}

class StorageSpaceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()
    private val store = InMemoryStore()
    private lateinit var cache: MediaCache

    private val imgA = ByteArray(1024) { 1 }
    private val imgB = ByteArray(2048) { 2 }

    @org.junit.Before
    fun setUp() {
        cache = MediaCache(File(folder.root, "media"))
    }

    private fun syncWith(free: Long) = ConfigSync(api, downloader, cache, store, freeBytes = { free }, reserveBytes = 0)

    @Test
    fun `new content that does not fit is refused before downloading anything, and the old keeps playing`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        downloader.files[a.url] = imgA
        downloader.files[b.url] = imgB
        api.configResponse = { configOf(5, 1, a) }
        syncWith(free = 10_000).sync("token")
        downloader.requested.clear()

        api.configResponse = { configOf(5, 2, a, b) }
        try {
            syncWith(free = 1_000).sync("token")
            fail("deveria recusar por falta de espaço")
        } catch (expected: InsufficientSpaceException) {
            assertTrue(expected.neededBytes >= 2048)
        }

        assertTrue("nada foi baixado", downloader.requested.isEmpty())
        assertEquals(1L, store.load()!!.configVersion)
        assertTrue("a mídia atual continua no cache", cache.isCached(a))
    }

    @Test
    fun `enough room downloads normally`() = runBlocking {
        val a = itemFor(1, imgA)
        downloader.files[a.url] = imgA
        api.configResponse = { configOf(5, 1, a) }

        syncWith(free = 1_000_000).sync("token")

        assertTrue(cache.isCached(a))
    }

    @Test
    fun `leftover partial downloads are cleaned to make room`() = runBlocking {
        val a = itemFor(1, imgA)
        downloader.files[a.url] = imgA
        api.configResponse = { configOf(5, 1, a) }
        val orphan = File(folder.root, "media/99-abc.mp4.part").apply { writeBytes(ByteArray(10)) }
        var free = 100L
        val sync = ConfigSync(api, downloader, cache, store, freeBytes = { if (orphan.exists()) free else 5_000 }, reserveBytes = 0)

        sync.sync("token")

        assertFalse(orphan.exists())
        assertTrue(cache.isCached(a))
    }

    @Test
    fun `the message explains what to do`() {
        val message = ErrorMessages.describe(InsufficientSpaceException(10L * 1024 * 1024, 2L * 1024 * 1024))

        assertTrue(message.contains("Não há espaço"))
        assertTrue(message.contains("8 MB"))
        assertTrue(message.contains("Continuo exibindo"))
    }

}
