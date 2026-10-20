package com.digitalsignage.player.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateIntegrityException(message: String) : IOException(message)

/**
 * Baixa o APK para um .part e só o promove a arquivo final depois de conferir SHA-256 e tamanho.
 * Um APK corrompido ou adulterado nunca chega ao instalador.
 */
class ApkDownloader(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun download(release: Release, target: File): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.delete()

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L

            http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("APK respondeu HTTP ${response.code}")
                response.body!!.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                            total += read
                        }
                    }
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(release.sha256, ignoreCase = true)) {
                throw UpdateIntegrityException("SHA-256 do APK não confere com o manifesto")
            }
            if (release.size > 0 && release.size != total) {
                throw UpdateIntegrityException("Tamanho do APK não confere com o manifesto")
            }

            target.delete()
            if (!part.renameTo(target)) throw IOException("Não foi possível gravar ${target.name}")
            target
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

}
