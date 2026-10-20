package com.digitalsignage.player.update

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    data class Available(val release: Release) : UpdateCheck

    /** Existe versão nova, mas este aparelho tem Android antigo demais para ela. */
    data class Incompatible(val release: Release, val deviceSdk: Int) : UpdateCheck
}

class UpdateChecker(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val deviceSdk: Int,
) {

    /** O manifesto é público: nenhuma credencial do aparelho é enviada a este servidor. */
    suspend fun check(manifestUrl: String, currentVersionCode: Long): UpdateCheck = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(manifestUrl).header("Cache-Control", "no-cache").build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("latest.json respondeu HTTP ${response.code}")
            response.body?.string().orEmpty()
        }

        val release = ReleaseJson.parse(body, manifestUrl)
        when {
            release.versionCode <= currentVersionCode -> UpdateCheck.UpToDate
            release.minSdk > deviceSdk -> UpdateCheck.Incompatible(release, deviceSdk)
            else -> UpdateCheck.Available(release)
        }
    }

}
