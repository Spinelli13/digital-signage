package com.digitalsignage.player

import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.ConfigZone
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.FetchedConfig
import com.digitalsignage.player.data.Hardware
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.MediaDownloader
import com.digitalsignage.player.data.OfflinePlan
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.PlayerStatus
import com.digitalsignage.player.proof.PlayRecord
import com.digitalsignage.player.sync.ConfigStore
import com.digitalsignage.player.telemetry.HeartbeatReport
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Item de playlist cujo checksum/tamanho correspondem exatamente aos bytes informados. */
fun itemFor(id: Long, bytes: ByteArray, type: String = "IMAGE", seconds: Int = 10) = ConfigItem(
    mediaId = id,
    name = "midia$id.${if (type == "VIDEO") "mp4" else "png"}",
    type = type,
    url = "/api/v1/media/$id/file",
    checksum = sha256(bytes),
    fileSize = bytes.size.toLong(),
    position = (id - 1).toInt(),
    durationSeconds = seconds,
)

fun configOf(playlistId: Long?, version: Long, vararg items: ConfigItem) =
    PlayerConfig(deviceId = 1, playlistId = playlistId, configVersion = version, items = items.toList())

/** Serializa como o servidor faria, para exercitar também o parse e a persistência. */
private fun itemsJson(items: List<ConfigItem>) = items.joinToString(",") {
    """{"mediaId":${it.mediaId},"name":"${it.name}","type":"${it.type}","url":"${it.url}",""" +
        """"checksum":${it.checksum?.let { c -> "\"$c\"" } ?: "null"},"fileSize":${it.fileSize},""" +
        """"position":${it.position},"durationSeconds":${it.durationSeconds}""" +
        (it.widget?.let { w -> ""","widget":{"kind":"${w.kind}","settings":${w.settingsJson}}""" } ?: "") + "}"
}

fun rawJson(config: PlayerConfig): String {
    val playlist = config.playlistId?.toString() ?: "null"
    fun zonesJson(zones: List<ConfigZone>) = zones.joinToString(",", prefix = "[", postfix = "]") {
        """{"id":${it.id},"name":"${it.name}","x":${it.x},"y":${it.y},"width":${it.width},"height":${it.height},"main":${it.main},"fit":"${it.fit}","items":[${itemsJson(it.items)}]}"""
    }
    val zones = (if (config.zones.isEmpty()) "" else ""","zones":${zonesJson(config.zones)}""") +
        (if (config.portraitZones.isEmpty()) "" else ""","portraitZones":${zonesJson(config.portraitZones)}""")
    val campaign = ""","campaignId":${config.campaignId ?: "null"},"campaignName":${config.campaignName?.let { "\"$it\"" } ?: "null"}"""
    val plan = config.plan?.let { ""","offlinePlan":${planJson(it)}""" } ?: ""
    val orientation = if (config.orientation == "AUTO") "" else ""","orientation":"${config.orientation}""""
    return """{"deviceId":${config.deviceId},"playlistId":$playlist,"configVersion":${config.configVersion},"items":[${itemsJson(config.items)}]$zones$campaign$plan$orientation}"""
}

private fun planJson(plan: OfflinePlan): String {
    val segments = plan.segments.joinToString(",") {
        """{"fromMs":${it.fromMs},"toMs":${it.toMs},"campaignId":${it.campaignId ?: "null"},""" +
            """"campaignName":${it.campaignName?.let { n -> "\"$n\"" } ?: "null"},"playlistId":${it.playlistId ?: "null"},"configVersion":${it.configVersion}}"""
    }
    val playlists = plan.playlists.joinToString(",") { """{"playlistId":${it.playlistId},"items":[${itemsJson(it.items)}]}""" }
    return """{"version":${plan.version},"generatedAtMs":${plan.generatedAtMs},"untilMs":${plan.untilMs},"segments":[$segments],"playlists":[$playlists]}"""
}

class InMemoryStore(initial: PlayerConfig? = null) : ConfigStore {
    var raw: String? = initial?.let(::rawJson)
    override fun load(): PlayerConfig? = raw?.let(Json::parseConfig)
    override fun save(raw: String) {
        this.raw = raw
    }

    override fun clear() {
        raw = null
    }
}

class FakeDownloader : MediaDownloader {
    val files = mutableMapOf<String, ByteArray>()
    val requested = mutableListOf<String>()
    var failWith: Exception? = null

    override suspend fun <T> download(token: String, url: String, consume: (InputStream) -> T): T {
        requested += url
        failWith?.let { throw it }
        val bytes = files[url] ?: throw IOException("sem arquivo para $url")
        return consume(ByteArrayInputStream(bytes))
    }
}

/** API programável: cada propriedade controla o que o "servidor" responde. */
class FakePlayerApi : PlayerApi {
    var registerResponse: () -> PlayerStatus = { throw IOException("offline") }
    var statusResponse: () -> PlayerStatus = { throw IOException("offline") }
    var heartbeatResponse: () -> Desired = { throw IOException("offline") }
    var configResponse: () -> PlayerConfig = { throw IOException("offline") }

    val heartbeatsSeenVersions = mutableListOf<Long>()

    /** Os dados de widgets pedidos (o endereço de cada pedido) e o que o "servidor" responde. */
    val widgetCalls = mutableListOf<String>()
    var widgetResponse: (String) -> String = { throw IOException("offline") }

    override suspend fun widgetData(token: String, url: String): String {
        widgetCalls += url
        return widgetResponse(url)
    }

    /** Até quando a agenda offline guardada valia em cada heartbeat (0 = sem agenda). */
    val hardwareSeen = mutableListOf<Hardware?>()
    val planUntilSeen = mutableListOf<Long>()

    /** O relato que cada heartbeat levou (mesmo os que o "servidor" recusou). */
    val reportsSeen = mutableListOf<HeartbeatReport>()
    var registerCalls = 0

    override suspend fun register(token: String, playerVersion: String): PlayerStatus {
        registerCalls++
        return registerResponse()
    }

    override suspend fun status(token: String): PlayerStatus = statusResponse()

    /** Os lotes de registros de exibição entregues (e a hora da TV em cada um); o que o "servidor" responde é programável. */
    val playBatches = mutableListOf<List<PlayRecord>>()
    val playDeviceTimes = mutableListOf<Long>()
    var sendPlaysResponse: (List<PlayRecord>) -> Long = { it.last().seq }

    override suspend fun sendPlays(token: String, deviceTimeMs: Long, records: List<PlayRecord>): Long {
        val acknowledged = sendPlaysResponse(records)
        playBatches += records
        playDeviceTimes += deviceTimeMs
        return acknowledged
    }

    override suspend fun heartbeat(token: String, playerVersion: String, syncedConfigVersion: Long, report: HeartbeatReport, planUntilMs: Long, hardware: Hardware?): Desired {
        hardwareSeen += hardware
        heartbeatsSeenVersions += syncedConfigVersion
        planUntilSeen += planUntilMs
        reportsSeen += report
        return heartbeatResponse()
    }

    override suspend fun config(token: String): FetchedConfig {
        val config = configResponse()
        return FetchedConfig(config, rawJson(config))
    }
}

fun pending(code: String = "ABC123", pairingUrl: String? = null) = PlayerStatus(1, "PENDING", code, "Novo dispositivo", pairingUrl)
fun active() = PlayerStatus(1, "ACTIVE", null, "TV")
fun disabled() = PlayerStatus(1, "DISABLED", null, "TV")
