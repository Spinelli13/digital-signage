package com.digitalsignage.player.data

import com.digitalsignage.player.telemetry.HeartbeatReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

data class PlayerStatus(
    val deviceId: Long,
    val status: String,
    val activationCode: String?,
    val deviceName: String?,
) {
    val isActive get() = status == "ACTIVE"
    val isPending get() = status == "PENDING"
}

data class ConfigItem(
    val mediaId: Long,
    val name: String,
    val type: String,
    val url: String,
    val checksum: String?,
    val fileSize: Long,
    val position: Int,
    val durationSeconds: Int,
) {
    val isVideo get() = type == "VIDEO"
}

data class PlayerConfig(
    val deviceId: Long,
    val playlistId: Long?,
    val configVersion: Long,
    val items: List<ConfigItem>,
)

/** O que o servidor quer que o Player exiba agora (resposta do heartbeat). */
/**
 * [updateCheckToken] muda quando o administrador pede, no painel, que a TV verifique atualização do Player
 * (0 = nunca pediu). A TV só reage a valores que ainda não tratou.
 */
data class Desired(val playlistId: Long?, val configVersion: Long, val updateCheckToken: Long = 0)

/** Config junto com o JSON original, que é o que fica persistido no aparelho. */
data class FetchedConfig(val config: PlayerConfig, val raw: String)

object Json {

    fun parseStatus(raw: String): PlayerStatus {
        val json = JSONObject(raw)
        return PlayerStatus(
            deviceId = json.optLong("deviceId"),
            status = json.getString("status"),
            activationCode = json.optStringOrNull("activationCode"),
            deviceName = json.optStringOrNull("deviceName"),
        )
    }

    fun parseConfig(raw: String): PlayerConfig {
        val json = JSONObject(raw)
        val array = json.getJSONArray("items")
        val items = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ConfigItem(
                mediaId = item.getLong("mediaId"),
                name = item.optString("name"),
                type = item.getString("type"),
                url = item.getString("url"),
                checksum = item.optStringOrNull("checksum"),
                fileSize = item.optLong("fileSize"),
                position = item.optInt("position"),
                durationSeconds = item.optInt("durationSeconds", 10),
            )
        }
        return PlayerConfig(
            deviceId = json.optLong("deviceId"),
            playlistId = if (json.isNull("playlistId")) null else json.getLong("playlistId"),
            configVersion = json.optLong("configVersion"),
            items = items.sortedBy { it.position },
        )
    }

    /**
     * Corpo do heartbeat. Os campos de monitoramento só entram quando há o que dizer: um servidor mais antigo
     * ignora o que não conhece, e o heartbeat de uma TV sem novidades continua do tamanho de sempre.
     */
    fun heartbeatBody(playerVersion: String, syncedConfigVersion: Long, report: HeartbeatReport): JSONObject {
        val body = JSONObject().put("playerVersion", playerVersion).put("configVersion", syncedConfigVersion)
        report.currentMediaId?.let { body.put("currentMediaId", it) }
        if (report.events.isNotEmpty()) {
            val events = JSONArray()
            for (event in report.events) {
                events.put(
                    JSONObject()
                        .put("level", event.level.name)
                        .put("message", event.message)
                        .put("at", isoUtc(event.atMillis)),
                )
            }
            body.put("events", events)
        }
        if (report.plays.isNotEmpty()) {
            val plays = JSONArray()
            for (play in report.plays) {
                plays.put(JSONObject().put("mediaId", play.mediaId).put("count", play.count).put("seconds", play.seconds))
            }
            body.put("plays", plays)
        }
        return body
    }

    /** 2026-11-05T12:00:00Z. SimpleDateFormat porque java.time só existe a partir do Android 8 e o Player vai até o 5. */
    fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    fun parseDesired(raw: String): Desired {
        val json = JSONObject(raw)
        return Desired(
            playlistId = if (json.isNull("playlistId")) null else json.getLong("playlistId"),
            configVersion = json.optLong("configVersion"),
            updateCheckToken = json.optLong("updateCheckToken", 0),
        )
    }

    // JSONObject.optString devolve "null" (texto) para nulo JSON; aqui nulo vira null de verdade.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key).ifBlank { null }

}
