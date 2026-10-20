package com.digitalsignage.player.update

import java.net.URI
import org.json.JSONException
import org.json.JSONObject

/** Uma versão publicada do Player, como descrita no latest.json do site de downloads. */
data class Release(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val minSdk: Int,
    val notes: String?,
)

object ReleaseJson {

    /**
     * [manifestUrl] serve para resolver um apkUrl relativo (ex.: "player-0.3.0.apk") junto ao manifesto,
     * assim o mesmo latest.json funciona em qualquer domínio.
     */
    fun parse(raw: String, manifestUrl: String): Release {
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw IllegalArgumentException("latest.json inválido", e)
        }

        val apkUrl = json.optString("apkUrl")
        require(apkUrl.isNotBlank()) { "latest.json sem apkUrl" }
        val sha256 = json.optString("sha256").lowercase()
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "latest.json com sha256 inválido" }
        val versionCode = json.optLong("versionCode", -1)
        require(versionCode > 0) { "latest.json sem versionCode" }

        return Release(
            versionCode = versionCode,
            versionName = json.optString("versionName", versionCode.toString()),
            apkUrl = URI(manifestUrl).resolve(apkUrl).toString(),
            sha256 = sha256,
            size = json.optLong("size", 0),
            minSdk = json.optInt("minSdk", 21),
            notes = json.optString("notes").ifBlank { null },
        )
    }

}
