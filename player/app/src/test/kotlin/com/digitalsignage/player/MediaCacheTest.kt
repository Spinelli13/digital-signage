package com.digitalsignage.player

import com.digitalsignage.player.cache.ChecksumMismatchException
import com.digitalsignage.player.cache.MediaCache
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cache() = MediaCache(File(folder.root, "media"))

    @Test
    fun `stores a file whose checksum matches and reports it as cached`() {
        val bytes = "conteudo".toByteArray()
        val item = itemFor(1, bytes)
        val cache = cache()

        assertFalse(cache.isCached(item))
        val file = cache.store(item, ByteArrayInputStream(bytes))

        assertTrue(cache.isCached(item))
        assertArrayEquals(bytes, file.readBytes())
    }

    /** Regressão: download corrompido nunca pode virar arquivo "válido" no cache. */
    @Test
    fun `rejects corrupted download and leaves nothing behind`() {
        val item = itemFor(1, "original".toByteArray())
        val cache = cache()

        try {
            cache.store(item, ByteArrayInputStream("corrompido".toByteArray()))
            fail("deveria rejeitar")
        } catch (expected: ChecksumMismatchException) {
            // esperado
        }

        assertFalse(cache.isCached(item))
        assertEquals(emptyList<String>(), File(folder.root, "media").list()!!.toList())
    }

    @Test
    fun `rejects a download that stops halfway`() {
        val bytes = ByteArray(1000) { it.toByte() }
        val item = itemFor(1, bytes)

        try {
            cache().store(item, ByteArrayInputStream(bytes.copyOf(400)))
            fail("deveria rejeitar")
        } catch (expected: ChecksumMismatchException) {
            // esperado
        }

        assertFalse(cache().isCached(item))
    }

    @Test
    fun `a file with the wrong size is not considered cached`() {
        val bytes = "12345".toByteArray()
        val item = itemFor(1, bytes)
        val cache = cache()
        cache.fileFor(item).writeBytes("123".toByteArray())

        assertFalse(cache.isCached(item))
    }

    @Test
    fun `same media id with a new checksum is a different cache entry`() {
        val cache = cache()
        val v1 = itemFor(1, "versao-1".toByteArray())
        val v2 = itemFor(1, "versao-2".toByteArray())

        cache.store(v1, ByteArrayInputStream("versao-1".toByteArray()))

        assertTrue(cache.isCached(v1))
        assertFalse(cache.isCached(v2))
    }

    @Test
    fun `retainOnly removes media outside the current config and interrupted downloads`() {
        val cache = cache()
        val keep = itemFor(1, "a".toByteArray())
        val drop = itemFor(2, "b".toByteArray())
        cache.store(keep, ByteArrayInputStream("a".toByteArray()))
        cache.store(drop, ByteArrayInputStream("b".toByteArray()))
        File(folder.root, "media/99-xyz.mp4${MediaCache.PART_SUFFIX}").writeText("meio baixado")

        cache.retainOnly(listOf(keep))

        assertTrue(cache.isCached(keep))
        assertFalse(cache.isCached(drop))
        assertEquals(1, File(folder.root, "media").list()!!.size)
    }

}
