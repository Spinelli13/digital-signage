package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.sync.ConfigSync
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigSyncTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val downloader = FakeDownloader()
    private val store = InMemoryStore()
    private lateinit var cache: MediaCache
    private lateinit var sync: ConfigSync

    private val imgA = "imagem-a".toByteArray()
    private val imgB = "imagem-b".toByteArray()

    @org.junit.Before
    fun setUp() {
        cache = MediaCache(File(folder.root, "media"))
        sync = ConfigSync(api, downloader, cache, store)
    }

    private fun serve(vararg items: com.digitalsignage.player.data.ConfigItem) {
        items.forEach { downloader.files[it.url] = if (it.mediaId == 1L) imgA else imgB }
    }

    @Test
    fun `first sync downloads every item and commits the config`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        serve(a, b)
        api.configResponse = { configOf(5, 1, a, b) }

        val config = sync.sync("token")

        assertEquals(1L, config.configVersion)
        assertEquals(listOf(a.url, b.url), downloader.requested)
        assertTrue(cache.isCached(a) && cache.isCached(b))
        assertEquals(5L, store.load()!!.playlistId)
    }

    @Test
    fun `media already in cache is not downloaded again`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        serve(a, b)
        api.configResponse = { configOf(5, 1, a) }
        sync.sync("token")
        downloader.requested.clear()

        api.configResponse = { configOf(5, 2, a, b) }
        sync.sync("token")

        assertEquals(listOf(b.url), downloader.requested)
    }

    /** Regressão: um download que falha NÃO pode derrubar a playlist que já está tocando. */
    @Test
    fun `failed download keeps the previous config untouched`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        serve(a, b)
        api.configResponse = { configOf(5, 1, a) }
        sync.sync("token")

        api.configResponse = { configOf(5, 2, a, b) }
        downloader.failWith = IOException("rede caiu")
        try {
            sync.sync("token")
            fail("deveria propagar a falha")
        } catch (expected: IOException) {
            // esperado
        }

        assertEquals(1L, store.load()!!.configVersion)
        assertTrue("mídia antiga continua em cache", cache.isCached(a))
    }

    @Test
    fun `corrupted download also keeps the previous config`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        downloader.files[a.url] = imgA
        downloader.files[b.url] = "bytes-errados".toByteArray()
        api.configResponse = { configOf(5, 1, a) }
        sync.sync("token")

        api.configResponse = { configOf(5, 2, a, b) }
        try {
            sync.sync("token")
            fail("deveria rejeitar")
        } catch (expected: IOException) {
            // esperado
        }

        assertEquals(1L, store.load()!!.configVersion)
        assertFalse(cache.isCached(b))
    }

    @Test
    fun `old media is removed once the new config is in place`() = runBlocking {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        serve(a, b)
        api.configResponse = { configOf(5, 1, a) }
        sync.sync("token")

        api.configResponse = { configOf(5, 2, b) }
        sync.sync("token")

        assertFalse(cache.isCached(a))
        assertTrue(cache.isCached(b))
    }

    @Test
    fun `nothing stored means a sync is needed`() {
        assertTrue(sync.needsSync(Desired(playlistId = null, configVersion = 0)))
    }

    @Test
    fun `same playlist and version needs no sync`() = runBlocking {
        val a = itemFor(1, imgA)
        serve(a)
        api.configResponse = { configOf(5, 3, a) }
        sync.sync("token")

        assertFalse(sync.needsSync(Desired(5, 3)))
        assertTrue(sync.needsSync(Desired(5, 4)))
    }

    /** Regressão: playlists diferentes podem ter a mesma versão — trocar de playlist exige sincronizar. */
    @Test
    fun `different playlist with the same version needs a sync`() = runBlocking {
        val a = itemFor(1, imgA)
        serve(a)
        api.configResponse = { configOf(5, 1, a) }
        sync.sync("token")

        assertTrue(sync.needsSync(Desired(playlistId = 6, configVersion = 1)))
    }

    @Test
    fun `an empty config for a device without playlist is stored once and then settled`() = runBlocking {
        api.configResponse = { configOf(null, 0) }
        sync.sync("token")

        assertFalse(sync.needsSync(Desired(null, 0)))
    }

    @Test
    fun `reset forgets config and cached media`() = runBlocking {
        val a = itemFor(1, imgA)
        serve(a)
        api.configResponse = { configOf(5, 1, a) }
        sync.sync("token")

        sync.reset()

        assertNull(store.load())
        assertFalse(cache.isCached(a))
    }

}
