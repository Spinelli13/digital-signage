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

    /** Página da web: não há arquivo; [url] é o endereço que o Player abre direto. */
    val isWeb get() = type == "WEB"
}

/**
 * Uma zona da tela (retângulo em % da tela) com as mídias dela. A zona principal exibe a playlist da TV; a
 * posição e o tamanho vêm do layout escolhido no painel.
 */
data class ConfigZone(
    val id: Long,
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val main: Boolean,
    val items: List<ConfigItem>,
) {
    val isFullScreen get() = x == 0 && y == 0 && width == 100 && height == 100
}

/**
 * [items] é sempre o conteúdo da zona principal (ou da tela toda, quando não há layout). [zones] só vem preenchida
 * quando a TV tem um layout; um servidor antigo (sem layouts) não manda o campo.
 */
data class PlayerConfig(
    val deviceId: Long,
    val playlistId: Long?,
    val configVersion: Long,
    val items: List<ConfigItem>,
    val zones: List<ConfigZone> = emptyList(),
) {

    /** As zonas a exibir: as do layout ou, sem layout, uma zona principal em tela cheia com [items]. */
    fun zonesOrDefault(): List<ConfigZone> =
        zones.ifEmpty { listOf(ConfigZone(0, "Principal", 0, 0, 100, 100, true, items)) }

    /** Toda mídia da configuração (de todas as zonas), sem repetir a que aparece em mais de uma. */
    fun allItems(): List<ConfigItem> = (items + zones.flatMap { it.items }).distinctBy { it.mediaId }

    /** Há algo para exibir em alguma zona? */
    fun hasContent(): Boolean = items.isNotEmpty() || zones.any { it.items.isNotEmpty() }
}

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
        val zonesJson = json.optJSONArray("zones")
        val zones = if (zonesJson == null) emptyList() else (0 until zonesJson.length()).map { parseZone(zonesJson.getJSONObject(it)) }
        return PlayerConfig(
            deviceId = json.optLong("deviceId"),
            playlistId = if (json.isNull("playlistId")) null else json.getLong("playlistId"),
            configVersion = json.optLong("configVersion"),
            items = parseItems(json.getJSONArray("items")),
            zones = zones,
        )
    }

    private fun parseZone(json: JSONObject) = ConfigZone(
        id = json.optLong("id"),
        name = json.optString("name"),
        x = json.getInt("x"),
        y = json.getInt("y"),
        width = json.getInt("width"),
        height = json.getInt("height"),
        main = json.optBoolean("main"),
        items = parseItems(json.getJSONArray("items")),
    )

    private fun parseItems(array: JSONArray): List<ConfigItem> =
        (0 until array.length()).map { index ->
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
        }.sortedBy { it.position }

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
