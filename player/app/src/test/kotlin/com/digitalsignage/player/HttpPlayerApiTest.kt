package com.digitalsignage.player

import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.HttpPlayerApi
import com.digitalsignage.player.data.UnauthorizedException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpPlayerApiTest {

    private lateinit var server: MockWebServer
    private lateinit var storage: MockWebServer
    private lateinit var api: HttpPlayerApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        storage = MockWebServer().apply { start() }
        api = HttpPlayerApi(baseUrl = { server.url("/").toString() })
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.shutdown()
    }

    @Test
    fun `register posts the token and parses the status`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"deviceId":1,"status":"PENDING","activationCode":"ABC123","deviceName":"Novo"}"""))

        val status = api.register("token-secreto", "0.2.0")

        assertEquals("ABC123", status.activationCode)
        val request = server.takeRequest()
        assertEquals("/api/v1/player/register", request.path)
        assertTrue(request.body.readUtf8().contains("token-secreto"))
    }

    @Test
    fun `authenticated calls send the device token header`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"playlistId":2,"configVersion":3,"serverTime":"x"}"""))

        val desired = api.heartbeat("meu-token", "0.2.0", 1)

        assertEquals(3L, desired.configVersion)
        val request = server.takeRequest()
        assertEquals("meu-token", request.getHeader("X-Device-Token"))
        assertTrue(request.body.readUtf8().contains("\"configVersion\":1"))
    }

    @Test
    fun `401 becomes UnauthorizedException`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        try {
            api.status("token-velho")
            fail("deveria lançar")
        } catch (expected: UnauthorizedException) {
            // esperado
        }
    }

    @Test
    fun `server errors become ApiException instead of crashing the parser`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("erro interno"))

        try {
            api.status("t")
            fail("deveria lançar")
        } catch (expected: ApiException) {
            assertEquals(500, expected.code)
        }
    }

    @Test
    fun `downloads a media file resolving the relative url against the server`() = runBlocking {
        server.enqueue(MockResponse().setBody("bytes-da-midia"))

        val bytes = api.download("tok", "/api/v1/media/7/file") { it.readBytes() }

        assertArrayEquals("bytes-da-midia".toByteArray(), bytes)
        val request = server.takeRequest()
        assertEquals("/api/v1/media/7/file", request.path)
        assertEquals("tok", request.getHeader("X-Device-Token"))
    }

    /**
     * Regressão de segurança: com armazenamento S3 o backend responde 302 para uma URL assinada em OUTRO host.
     * O token do aparelho jamais pode ser enviado a esse host.
     */
    @Test
    fun `follows a redirect to external storage without leaking the device token`() = runBlocking {
        val external = storage.url("/bucket/arquivo.png?X-Amz-Signature=abc").toString()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", external))
        storage.enqueue(MockResponse().setBody("conteudo-externo"))

        val bytes = api.download("token-secreto", "/api/v1/media/7/file") { it.readBytes() }

        assertArrayEquals("conteudo-externo".toByteArray(), bytes)
        assertEquals("token-secreto", server.takeRequest().getHeader("X-Device-Token"))
        val externalRequest = storage.takeRequest()
        assertNull("o token não pode ir para o armazenamento externo", externalRequest.getHeader("X-Device-Token"))
        assertEquals("/bucket/arquivo.png?X-Amz-Signature=abc", externalRequest.path)
    }

    @Test
    fun `gives up on redirect loops`() = runBlocking {
        repeat(10) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/api/v1/media/7/file")) }

        try {
            api.download("t", "/api/v1/media/7/file") { it.readBytes() }
            fail("deveria desistir")
        } catch (expected: IllegalStateException) {
            // esperado
        }
    }

    @Test
    fun `download of a missing file fails instead of caching an error page`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Mídia não encontrada"}"""))

        try {
            api.download("t", "/api/v1/media/9/file") { it.readBytes() }
            fail("deveria lançar")
        } catch (expected: ApiException) {
            assertEquals(404, expected.code)
        }
    }

}
