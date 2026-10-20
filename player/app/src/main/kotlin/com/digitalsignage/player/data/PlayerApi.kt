package com.digitalsignage.player.data

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** O servidor não reconhece (ou desativou) o token deste aparelho. */
class UnauthorizedException : IOException("Token do dispositivo não aceito pelo servidor")

class ApiException(val code: Int, message: String) : IOException("HTTP $code: $message")

interface PlayerApi {
    suspend fun register(token: String, playerVersion: String): PlayerStatus
    suspend fun status(token: String): PlayerStatus
    suspend fun config(token: String): FetchedConfig
    suspend fun heartbeat(token: String, playerVersion: String, syncedConfigVersion: Long): Desired
}

interface MediaDownloader {
    /** Abre o arquivo (seguindo redirecionamentos) e entrega o stream para [consume]. */
    suspend fun <T> download(token: String, url: String, consume: (InputStream) -> T): T
}

class HttpPlayerApi(
    private val baseUrl: () -> String,
    private val http: OkHttpClient = defaultClient(),
) : PlayerApi, MediaDownloader {

    override suspend fun register(token: String, playerVersion: String): PlayerStatus {
        val body = JSONObject().put("deviceToken", token).put("playerVersion", playerVersion)
        return Json.parseStatus(send(post("/api/v1/player/register", body)))
    }

    override suspend fun status(token: String): PlayerStatus =
        Json.parseStatus(send(get("/api/v1/player/status", token)))

    override suspend fun config(token: String): FetchedConfig {
        val raw = send(get("/api/v1/player/config", token))
        return FetchedConfig(Json.parseConfig(raw), raw)
    }

    override suspend fun heartbeat(token: String, playerVersion: String, syncedConfigVersion: Long): Desired {
        val body = JSONObject().put("playerVersion", playerVersion).put("configVersion", syncedConfigVersion)
        return Json.parseDesired(send(post("/api/v1/player/heartbeat", body, token)))
    }

    override suspend fun <T> download(token: String, url: String, consume: (InputStream) -> T): T =
        withContext(Dispatchers.IO) {
            val base = baseUrl().trimEnd('/').toHttpUrl()
            var target: HttpUrl = resolve(base, url)
            var hops = 0

            while (true) {
                // O token só vai para o servidor do próprio sistema. Se a mídia estiver num armazenamento
                // externo (URL assinada em outro host), enviar o token vazaria a credencial do aparelho.
                val builder = Request.Builder().url(target)
                if (sameOrigin(base, target)) builder.header(TOKEN_HEADER, token)

                val response = http.newCall(builder.build()).execute()
                if (response.isRedirect) {
                    val location = response.header("Location")
                    response.close()
                    check(++hops <= MAX_REDIRECTS) { "Redirecionamentos demais ao baixar $url" }
                    target = target.resolve(location ?: throw IOException("Redirecionamento sem Location"))
                        ?: throw IOException("Location inválido")
                    continue
                }

                return@withContext response.use {
                    when {
                        it.code == 401 -> throw UnauthorizedException()
                        !it.isSuccessful -> throw ApiException(it.code, "download de $url")
                        else -> consume(it.body!!.byteStream())
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("inalcançável")
        }

    private fun get(path: String, token: String): Request =
        Request.Builder().url(resolve(baseUrl().trimEnd('/').toHttpUrl(), path))
            .header(TOKEN_HEADER, token).get().build()

    private fun post(path: String, body: JSONObject, token: String? = null): Request {
        val builder = Request.Builder().url(resolve(baseUrl().trimEnd('/').toHttpUrl(), path))
            .post(body.toString().toRequestBody(JSON))
        if (token != null) builder.header(TOKEN_HEADER, token)
        return builder.build()
    }

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when {
                response.code == 401 -> throw UnauthorizedException()
                !response.isSuccessful -> throw ApiException(response.code, text.take(200))
                else -> text
            }
        }
    }

    private fun resolve(base: HttpUrl, pathOrUrl: String): HttpUrl =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl.toHttpUrl()
        } else {
            base.resolve(pathOrUrl) ?: throw IOException("URL inválida: $pathOrUrl")
        }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl) =
        a.scheme == b.scheme && a.host == b.host && a.port == b.port


    companion object {
        const val TOKEN_HEADER = "X-Device-Token"
        private const val MAX_REDIRECTS = 5
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Redirecionamentos são tratados à mão (ver download) para não vazar o token.
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

}
