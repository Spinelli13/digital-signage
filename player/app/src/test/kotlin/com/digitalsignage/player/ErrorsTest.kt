package com.digitalsignage.player

import com.digitalsignage.player.cache.ChecksumMismatchException
import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.errors.ErrorReporter
import com.digitalsignage.player.update.UpdateIntegrityException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorsTest {

    // --- relator: cada mensagem 1x, sem repetir a cada tentativa ---

    @Test
    fun `delivers each distinct message`() = runTest(UnconfinedTestDispatcher()) {
        val reporter = ErrorReporter(clock = { 0L })
        val received = mutableListOf<String>()
        val job = launch { reporter.messages.toList(received) }

        reporter.report("A")
        reporter.report("B")

        assertEquals(listOf("A", "B"), received)
        job.cancel()
    }

    /** Regressão de usabilidade: com a internet fora o Player tenta a cada poucos segundos; a tarja não pode ficar fixa. */
    @Test
    fun `suppresses the same message while it is still recent`() {
        var now = 0L
        val reporter = ErrorReporter(clock = { now }, dedupeMs = 5 * 60_000L)

        assertTrue(reporter.report("Sem conexão"))
        now += 10_000
        assertFalse(reporter.report("Sem conexão"))
        now += 4 * 60_000
        assertFalse(reporter.report("Sem conexão"))
    }

    @Test
    fun `repeats the message once the window has passed`() {
        var now = 0L
        val reporter = ErrorReporter(clock = { now }, dedupeMs = 5 * 60_000L)

        reporter.report("Sem conexão")
        now += 5 * 60_000 + 1

        assertTrue(reporter.report("Sem conexão"))
    }

    @Test
    fun `a custom repeat window and zero window are respected`() {
        var now = 0L
        val reporter = ErrorReporter(clock = { now })

        assertTrue(reporter.report("Aviso do dia", repeatAfterMs = 24 * 60 * 60_000L))
        now += 60 * 60_000L
        assertFalse(reporter.report("Aviso do dia", repeatAfterMs = 24 * 60 * 60_000L))
        // 0 = sempre mostra (ex.: atualização cancelada: é um evento, não um estado)
        assertTrue(reporter.report("Evento", repeatAfterMs = 0))
        assertTrue(reporter.report("Evento", repeatAfterMs = 0))
    }

    @Test
    fun `messages display for ten seconds`() {
        assertEquals(10_000L, ErrorReporter.DISPLAY_MS)
    }

    // --- tradução de erros técnicos ---

    @Test
    fun `explains network failures in plain words`() {
        assertTrue(ErrorMessages.describe(UnknownHostException("x")).contains("internet"))
        assertTrue(ErrorMessages.describe(ConnectException("x")).contains("conectar"))
        assertTrue(ErrorMessages.describe(SocketTimeoutException("x")).contains("1 minuto"))
    }

    @Test
    fun `blames the date and time of the tv for certificate errors`() {
        val message = ErrorMessages.describe(SSLHandshakeException("cert"))

        assertTrue(message.contains("data e a hora"))
    }

    @Test
    fun `tells the user when the device was removed from the panel`() {
        assertTrue(ErrorMessages.describe(UnauthorizedException()).contains("painel"))
    }

    @Test
    fun `distinguishes server errors from refusals`() {
        assertTrue(ErrorMessages.describe(ApiException(503, "x")).contains("erro (HTTP 503)"))
        assertTrue(ErrorMessages.describe(ApiException(404, "x")).contains("não encontrado"))
        assertTrue(ErrorMessages.describe(ApiException(400, "x")).contains("recusou"))
    }

    @Test
    fun `corrupted downloads are described as discarded, not as a crash`() {
        assertTrue(ErrorMessages.describe(UpdateIntegrityException("sha")).contains("corrompido"))
        val item = itemFor(1, "x".toByteArray())
        assertTrue(ErrorMessages.describe(ChecksumMismatchException(item)).contains("descartado"))
    }

    @Test
    fun `bad json and unknown errors still produce a readable message`() {
        assertTrue(ErrorMessages.describe(JSONException("x")).contains("resposta inesperada"))
        assertTrue(ErrorMessages.describe(IOException("disco cheio")).contains("disco cheio"))
        assertTrue(ErrorMessages.describe(IllegalStateException("boom")).contains("boom"))
    }

    @Test
    fun `a context prefix is prepended`() {
        assertTrue(ErrorMessages.describe("Servidor", UnknownHostException("x")).startsWith("Servidor: "))
    }

}
