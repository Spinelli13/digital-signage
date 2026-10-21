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
    /** Só nos widgets: o tipo (CLOCK, WEATHER...) e as escolhas do painel. */
    val widget: WidgetSpec? = null,
) {
    val isVideo get() = type == "VIDEO"

    /** Página da web: não há arquivo; [url] é o endereço que o Player abre direto. */
    val isWeb get() = type == "WEB"

    /** Widget (relógio, clima, notícias...): não há arquivo; o Player desenha com as escolhas e os dados guardados. */
    val isWidget get() = type == "WIDGET"

    /** Há um arquivo para baixar e guardar no cache? (página da web e widget não têm.) */
    val hasFile get() = !isWeb && !isWidget

    /** Desenhado dentro do WebView (página da web ou widget). */
    val usesWebView get() = isWeb || isWidget
}

/** As escolhas de um widget, do jeito que o servidor mandou ([settingsJson] é um objeto JSON em texto). */
data class WidgetSpec(val kind: String, val settingsJson: String) {
    /** Busca dados de fora (o servidor entrega); os demais se desenham só com as escolhas. */
    val needsData get() = kind == "WEATHER" || kind == "NEWS" || kind == "RATES"
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
    /** A campanha que está no ar (nula = a playlist da própria TV): entra na comprovação de exibição. */
    val campaignId: Long? = null,
    val campaignName: String? = null,
    /** A agenda dos próximos dias (modo offline); nula quando nenhuma campanha vale para a TV. */
    val plan: OfflinePlan? = null,
) {

    /**
     * O que a tela precisa. A agenda fica de fora: ela só serve ao motor, e mudar só a agenda (renovação diária) não pode
     * reiniciar a exibição que está no ar.
     */
    fun forScreen(): PlayerConfig = if (plan == null) this else copy(plan = null)

    /** Toda mídia que precisa ficar no aparelho: a da configuração e a da agenda. */
    fun retainable(): List<ConfigItem> = (allItems() + (plan?.allItems() ?: emptyList())).distinctBy { it.mediaId }

    /** As zonas a exibir: as do layout ou, sem layout, uma zona principal em tela cheia com [items]. */
    fun zonesOrDefault(): List<ConfigZone> =
        zones.ifEmpty { listOf(ConfigZone(0, "Principal", 0, 0, 100, 100, true, items)) }

    /** Toda mídia da configuração (de todas as zonas), sem repetir a que aparece em mais de uma. */
    fun allItems(): List<ConfigItem> = (items + zones.flatMap { it.items }).distinctBy { it.mediaId }

    /** Há algo para exibir em alguma zona? */
    fun hasContent(): Boolean = items.isNotEmpty() || zones.any { it.items.isNotEmpty() }
}

/** De [fromMs] (inclusive) até [toMs] (exclusive), a TV exibe [playlistId] (da campanha [campaignId], se houver). */
data class PlanSegment(
    val fromMs: Long,
    val toMs: Long,
    val campaignId: Long?,
    val campaignName: String?,
    val playlistId: Long?,
    val configVersion: Long,
)

data class PlanPlaylist(val playlistId: Long, val items: List<ConfigItem>)

/**
 * A agenda que o servidor monta para os próximos dias, já com o vencedor de cada intervalo. O Player só olha a hora; nenhuma
 * regra de campanha (prioridade, alvo, fuso, janela que passa da meia-noite) é repetida aqui.
 */
data class OfflinePlan(
    val version: Long,
    val generatedAtMs: Long,
    val untilMs: Long,
    val segments: List<PlanSegment>,
    val playlists: List<PlanPlaylist>,
) {
    fun segmentAt(nowMs: Long): PlanSegment? = segments.firstOrNull { nowMs >= it.fromMs && nowMs < it.toMs }

    fun itemsOf(playlistId: Long?): List<ConfigItem> = playlists.firstOrNull { it.playlistId == playlistId }?.items ?: emptyList()

    /** Toda mídia da agenda, na ordem em que vai ao ar (a mais próxima primeiro), sem repetir. */
    fun allItems(): List<ConfigItem> = playlists.flatMap { it.items }.distinctBy { it.mediaId }
}

/** O que o servidor quer que o Player exiba agora (resposta do heartbeat). */
/**
 * [updateCheckToken] muda quando o administrador pede, no painel, que a TV verifique atualização do Player
 * (0 = nunca pediu). A TV só reage a valores que ainda não tratou.
 */
data class Desired(
    val playlistId: Long?,
    val configVersion: Long,
    val updateCheckToken: Long = 0,
    val campaignId: Long? = null,
    /** A versão da agenda offline (0 = sem agenda, ou servidor antigo): se for diferente da guardada, baixa a configuração de novo. */
    val planVersion: Long = 0,
    /** A hora do servidor neste contato (0 = servidor antigo): acerta o relógio confiável da TV. */
    val serverTimeMs: Long = 0,
)

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
            campaignId = if (json.isNull("campaignId")) null else json.optLong("campaignId"),
            campaignName = if (json.isNull("campaignName")) null else json.optString("campaignName"),
            plan = json.optJSONObject("offlinePlan")?.let(::parsePlan),
        )
    }

    private fun parsePlan(json: JSONObject): OfflinePlan {
        val segments = json.getJSONArray("segments")
        val playlists = json.getJSONArray("playlists")
        return OfflinePlan(
            version = json.optLong("version"),
            generatedAtMs = json.optLong("generatedAtMs"),
            untilMs = json.optLong("untilMs"),
            segments = (0 until segments.length()).map { index ->
                val segment = segments.getJSONObject(index)
                PlanSegment(
                    fromMs = segment.getLong("fromMs"),
                    toMs = segment.getLong("toMs"),
                    campaignId = if (segment.isNull("campaignId")) null else segment.getLong("campaignId"),
                    campaignName = segment.optStringOrNull("campaignName"),
                    playlistId = if (segment.isNull("playlistId")) null else segment.getLong("playlistId"),
                    configVersion = segment.optLong("configVersion"),
                )
            },
            playlists = (0 until playlists.length()).map { index ->
                val playlist = playlists.getJSONObject(index)
                PlanPlaylist(playlist.getLong("playlistId"), parseItems(playlist.getJSONArray("items")))
            },
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
                widget = item.optJSONObject("widget")?.let { spec ->
                    WidgetSpec(spec.getString("kind"), (spec.optJSONObject("settings") ?: JSONObject()).toString())
                },
            )
        }.sortedBy { it.position }

    /**
     * Corpo do heartbeat. Os campos de monitoramento só entram quando há o que dizer: um servidor mais antigo
     * ignora o que não conhece, e o heartbeat de uma TV sem novidades continua do tamanho de sempre.
     */
    fun heartbeatBody(playerVersion: String, syncedConfigVersion: Long, report: HeartbeatReport, planUntilMs: Long = 0): JSONObject {
        val body = JSONObject().put("playerVersion", playerVersion).put("configVersion", syncedConfigVersion)
        if (planUntilMs > 0) body.put("planUntilMs", planUntilMs)
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
            campaignId = if (json.isNull("campaignId")) null else json.optLong("campaignId"),
            planVersion = json.optLong("planVersion", 0),
            serverTimeMs = parseServerTime(json.opt("serverTime")),
        )
    }

    /** A hora do servidor: texto ISO em UTC ("2026-11-05T12:00:00.123456Z") ou número (segundos com fração). 0 se não entender. */
    fun parseServerTime(value: Any?): Long = when (value) {
        is Number -> (value.toDouble() * 1000).toLong()
        is String -> parseIsoUtc(value)
        else -> 0L
    }

    /** Só aceita UTC ("Z"): o servidor sempre manda assim, e converter outro fuso à mão arriscaria uma hora errada. */
    fun parseIsoUtc(text: String): Long {
        if (text.length < 20 || !text.endsWith("Z")) return 0L
        return try {
            val seconds = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(text.substring(0, 19))?.time ?: return 0L
            val fraction = if (text[19] == '.') text.substring(20, text.length - 1).take(3).padEnd(3, '0').toLong() else 0L
            seconds + fraction
        } catch (e: Exception) {
            0L
        }
    }

    // JSONObject.optString devolve "null" (texto) para nulo JSON; aqui nulo vira null de verdade.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key).ifBlank { null }

}
