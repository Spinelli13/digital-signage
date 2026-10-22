package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.engine.EngineState
import com.digitalsignage.player.engine.PlayerEngine
import com.digitalsignage.player.engine.Timing
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.PlayCount
import com.digitalsignage.player.telemetry.Telemetry
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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakePlayerApi()
    private val reported = mutableListOf<String>()
    private val downloader = FakeDownloader()
    private val telemetry = Telemetry()
    private val timing = Timing(activationPollMs = 5_000, heartbeatMs = 30_000, retryMs = 10_000, disabledPollMs = 30_000)

    private val imgA = "imagem-a".toByteArray()
    private val imgB = "imagem-b".toByteArray()

    private fun build(store: InMemoryStore): Pair<PlayerEngine, MediaCache> {
        val cache = MediaCache(File(folder.root, "media"))
        val sync = ConfigSync(api, downloader, cache, store)
        return PlayerEngine(api, sync, store, token = { "tok" }, playerVersion = "0.2.0", timing = timing, onError = { reported += it }, telemetry = telemetry) to cache
    }

    private fun TestScope.startEngine(engine: PlayerEngine) {
        backgroundScope.launch { engine.run() }
        runCurrent()
    }

    @Test
    fun `shows the activation code and starts playing once the admin activates the device`() = runTest {
        val a = itemFor(1, imgA)
        downloader.files[a.url] = imgA
        var activated = false
        api.registerResponse = { pending("K7M2QX") }
        api.statusResponse = { if (activated) active() else pending("K7M2QX") }
        api.heartbeatResponse = { Desired(5, 1) }
        api.configResponse = { configOf(5, 1, a) }
        val store = InMemoryStore()
        val (engine, _) = build(store)

        startEngine(engine)
        assertEquals(EngineState.WaitingActivation("K7M2QX"), engine.state.value)

        activated = true
        advanceTimeBy(5_001)
        runCurrent()

        val state = engine.state.value
        assertTrue("esperava Playing, veio $state", state is EngineState.Playing)
        assertEquals(1L, (state as EngineState.Playing).config.configVersion)
        assertNotNull(store.load())
    }

    /** Regressão do requisito "funcionar sem internet / após reiniciar": começa tocando o cache sem rede. */
    @Test
    fun `plays the cached playlist immediately and keeps playing while offline`() = runTest {
        val a = itemFor(1, imgA)
        val store = InMemoryStore(configOf(5, 1, a))
        api.registerResponse = { throw IOException("sem internet") }
        val (engine, _) = build(store)

        startEngine(engine)
        assertTrue(engine.state.value is EngineState.Playing)

        advanceTimeBy(60_000)
        runCurrent()
        assertTrue("continua tocando mesmo com a rede fora", engine.state.value is EngineState.Playing)
        assertNotNull(store.load())
    }

    @Test
    fun `reports a connection error only when there is nothing cached to show`() = runTest {
        api.registerResponse = { throw IOException("sem internet") }
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)

        assertEquals(EngineState.ConnectionError, engine.state.value)
    }

    @Test
    fun `recovers by itself when the network comes back`() = runTest {
        val a = itemFor(1, imgA)
        downloader.files[a.url] = imgA
        var online = false
        api.registerResponse = { if (online) active() else throw IOException("offline") }
        api.heartbeatResponse = { Desired(5, 1) }
        api.configResponse = { configOf(5, 1, a) }
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)
        assertEquals(EngineState.ConnectionError, engine.state.value)

        online = true
        advanceTimeBy(10_001)
        runCurrent()

        assertTrue(engine.state.value is EngineState.Playing)
    }

    /** Regressão: download que falha não derruba a playlist que está no ar; na próxima tentativa conclui. */
    @Test
    fun `keeps the old playlist while a new one fails to download, then switches`() = runTest {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        downloader.files[a.url] = imgA
        downloader.files[b.url] = imgB
        val store = InMemoryStore(configOf(5, 1, a))
        val cache = MediaCache(File(folder.root, "media"))
        cache.store(a, imgA.inputStream())
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(5, 2) }
        api.configResponse = { configOf(5, 2, a, b) }
        downloader.failWith = IOException("rede instável")
        val (engine, _) = build(store)

        startEngine(engine)
        assertEquals(1L, (engine.state.value as EngineState.Playing).config.configVersion)

        downloader.failWith = null
        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(2L, (engine.state.value as EngineState.Playing).config.configVersion)
        assertEquals(2L, store.load()!!.configVersion)
    }

    @Test
    fun `passes a panel request for an update check to the app on every heartbeat that carries it`() = runTest {
        val a = itemFor(1, imgA)
        downloader.files[a.url] = imgA
        val tokens = mutableListOf<Long>()
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(5, 1, updateCheckToken = 777) }
        api.configResponse = { configOf(5, 1, a) }
        val cache = MediaCache(File(folder.root, "media"))
        val store = InMemoryStore()
        val engine = PlayerEngine(
            api, ConfigSync(api, downloader, cache, store), store, token = { "tok" }, playerVersion = "0.2.0",
            timing = timing, onUpdateCheckRequested = { tokens += it },
        )

        startEngine(engine)
        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(listOf(777L, 777L), tokens.take(2))
    }

    @Test
    fun `does not call the update callback when no check was requested`() = runTest {
        val a = itemFor(1, imgA)
        downloader.files[a.url] = imgA
        var calls = 0
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(5, 1) }
        api.configResponse = { configOf(5, 1, a) }
        val cache = MediaCache(File(folder.root, "media"))
        val store = InMemoryStore()
        val engine = PlayerEngine(
            api, ConfigSync(api, downloader, cache, store), store, token = { "tok" }, playerVersion = "0.2.0",
            timing = timing, onUpdateCheckRequested = { calls++ },
        )

        startEngine(engine)
        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(0, calls)
    }

    @Test
    fun `heartbeat reports the version actually synced, not the one the server wants`() = runTest {
        val a = itemFor(1, imgA)
        downloader.files[a.url] = imgA
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(5, 1) }
        api.configResponse = { configOf(5, 1, a) }
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)
        advanceTimeBy(30_001)
        runCurrent()

        // 1º heartbeat: nada sincronizado ainda (0); depois de baixar, informa a versão 1.
        assertEquals(listOf(0L, 1L), api.heartbeatsSeenVersions.take(2))
    }

    // ---------- relato de monitoramento no heartbeat ----------

    @Test
    fun `the heartbeat carries what the Player recorded and the server confirmation clears it`() = runTest {
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(null, 0) }
        telemetry.event(Level.ERROR, "Falha ao baixar a mídia")
        telemetry.mediaShown(7)
        telemetry.played(7, 10)
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)
        advanceTimeBy(30_001)
        runCurrent()

        val first = api.reportsSeen[0]
        assertEquals(listOf("Falha ao baixar a mídia"), first.events.map { it.message })
        assertEquals(7L, first.currentMediaId)
        assertEquals(listOf(PlayCount(7, 1, 10)), first.plays)
        // o segundo heartbeat não repete o que o servidor já recebeu, mas continua dizendo o que está na tela
        val second = api.reportsSeen[1]
        assertTrue(second.events.isEmpty())
        assertTrue(second.plays.isEmpty())
        assertEquals(7L, second.currentMediaId)
    }

    @Test
    fun `a heartbeat that fails keeps the report for the next attempt`() = runTest {
        api.registerResponse = { active() }
        var failing = true
        api.heartbeatResponse = { if (failing) throw IOException("sem internet") else Desired(null, 0) }
        telemetry.event(Level.ERROR, "erro guardado")
        telemetry.played(3, 8)
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)
        assertEquals(1, api.reportsSeen.size)
        assertEquals(1, telemetry.pendingEvents())

        failing = false
        advanceTimeBy(60_000)
        runCurrent()

        val delivered = api.reportsSeen[1] // a primeira tentativa depois da falha
        assertEquals(listOf("erro guardado"), delivered.events.map { it.message })
        assertEquals(listOf(PlayCount(3, 1, 8)), delivered.plays)
        assertEquals(0, telemetry.pendingEvents())
    }

    /** Sem isso um relato recusado (HTTP 400) seria reenviado para sempre e a TV pareceria offline. */
    @Test
    fun `a report the server refuses with 400 is dropped so heartbeats keep flowing`() = runTest {
        api.registerResponse = { active() }
        var calls = 0
        api.heartbeatResponse = { if (calls++ == 0) throw ApiException(400, "relato inválido") else Desired(null, 0) }
        telemetry.event(Level.ERROR, "relato que o servidor não aceita")
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(api.reportsSeen.size >= 2)
        assertTrue("o relato recusado não pode voltar", api.reportsSeen.drop(1).all { it.events.isEmpty() })
    }

    @Test
    fun `other server errors keep the report`() = runTest {
        api.registerResponse = { active() }
        var calls = 0
        api.heartbeatResponse = { if (calls++ == 0) throw ApiException(503, "indisponível") else Desired(null, 0) }
        telemetry.event(Level.ERROR, "ainda pendente")
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(listOf("ainda pendente"), api.reportsSeen[1].events.map { it.message })
    }

    /** O administrador excluiu o dispositivo: a TV esquece o conteúdo e volta a pedir ativação. */
    @Test
    fun `goes back to activation and wipes content when the server rejects the token`() = runTest {
        val a = itemFor(1, imgA)
        val store = InMemoryStore(configOf(5, 1, a))
        var deleted = false
        api.registerResponse = { if (deleted) pending("NEW123") else active() }
        api.heartbeatResponse = { if (deleted) throw UnauthorizedException() else Desired(5, 1) }
        val (engine, _) = build(store)

        startEngine(engine)
        assertTrue(engine.state.value is EngineState.Playing)

        deleted = true
        advanceTimeBy(30_001)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(EngineState.WaitingActivation("NEW123"), engine.state.value)
        assertNull("conteúdo antigo não pode continuar disponível", store.load())
    }

    @Test
    fun `the waiting screen carries the address the pairing QR Code leads to`() = runTest {
        api.registerResponse = { pending("K7M2QX", "https://painel.test/parear/K7M2QX") }
        api.statusResponse = { pending("K7M2QX", "https://painel.test/parear/K7M2QX") }
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)

        assertEquals(EngineState.WaitingActivation("K7M2QX", "https://painel.test/parear/K7M2QX"), engine.state.value)
    }

    @Test
    fun `a disabled device stops playing and drops its content`() = runTest {
        val a = itemFor(1, imgA)
        val store = InMemoryStore(configOf(5, 1, a))
        api.registerResponse = { disabled() }
        val (engine, _) = build(store)

        startEngine(engine)

        assertEquals(EngineState.Disabled, engine.state.value)
        assertNull(store.load())
    }

    // --- avisos de erro para o usuário ---

    @Test
    fun `tells the user when the server cannot be reached, without stopping the cached playlist`() = runTest {
        val a = itemFor(1, imgA)
        val store = InMemoryStore(configOf(5, 1, a))
        api.registerResponse = { throw java.net.UnknownHostException("sem internet") }
        val (engine, _) = build(store)

        startEngine(engine)

        assertTrue(reported.single().startsWith("Servidor: "))
        assertTrue(reported.single().contains("internet"))
        assertTrue("segue tocando o cache", engine.state.value is EngineState.Playing)
    }

    @Test
    fun `tells the user when this device was removed from the panel`() = runTest {
        val store = InMemoryStore(configOf(5, 1, itemFor(1, imgA)))
        api.registerResponse = { throw UnauthorizedException() }
        val (engine, _) = build(store)

        startEngine(engine)

        assertTrue(reported.single().contains("painel"))
    }

    @Test
    fun `explains a failed content update and says the previous playlist keeps playing`() = runTest {
        val a = itemFor(1, imgA)
        val b = itemFor(2, imgB)
        downloader.files[b.url] = imgB
        val store = InMemoryStore(configOf(5, 1, a))
        MediaCache(File(folder.root, "media")).store(a, imgA.inputStream())
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(5, 2) }
        api.configResponse = { configOf(5, 2, a, b) }
        downloader.failWith = java.net.SocketTimeoutException("lento")
        val (engine, _) = build(store)

        startEngine(engine)

        assertTrue(reported.single().contains("continuo exibindo o anterior"))
        assertTrue(engine.state.value is EngineState.Playing)
    }

    @Test
    fun `stays quiet when everything works`() = runTest {
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(null, 0) }
        api.configResponse = { configOf(null, 0) }
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)

        assertTrue(reported.isEmpty())
    }

    /** O administrador tirou a playlist da TV: ela para de exibir o conteúdo antigo e libera o cache. */
    @Test
    fun `goes to the no-content screen and frees the cache when the playlist is removed from the device`() = runTest {
        val a = itemFor(1, imgA)
        val store = InMemoryStore(configOf(5, 1, a))
        val cache = MediaCache(File(folder.root, "media"))
        cache.store(a, imgA.inputStream())
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(null, 0) }
        api.configResponse = { configOf(null, 0) }
        val (engine, _) = build(store)

        startEngine(engine)

        assertEquals(EngineState.NoContent, engine.state.value)
        assertFalse("a mídia da playlist removida não deve ficar no cache", cache.isCached(a))
        assertNull(store.load()?.playlistId)
    }

    @Test
    fun `an active device with an empty playlist shows the no-content screen`() = runTest {
        api.registerResponse = { active() }
        api.heartbeatResponse = { Desired(null, 0) }
        api.configResponse = { configOf(null, 0) }
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)

        assertEquals(EngineState.NoContent, engine.state.value)
    }

    @Test
    fun `survives an unexpected server answer and tries again`() = runTest {
        var calls = 0
        api.registerResponse = {
            calls++
            if (calls == 1) throw IllegalStateException("json estranho") else active()
        }
        api.heartbeatResponse = { Desired(null, 0) }
        api.configResponse = { configOf(null, 0) }
        val (engine, _) = build(InMemoryStore())

        startEngine(engine)
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(2, api.registerCalls)
        assertEquals(EngineState.NoContent, engine.state.value)
    }

}
