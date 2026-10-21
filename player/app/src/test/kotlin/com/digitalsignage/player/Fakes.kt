package com.digitalsignage.player

import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.FetchedConfig
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.MediaDownloader
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
        """"position":${it.position},"durationSeconds":${it.durationSeconds}}"""
}

fun rawJson(config: PlayerConfig): String {
    val playlist = config.playlistId?.toString() ?: "null"
    val zones = if (config.zones.isEmpty()) "" else config.zones.joinToString(",", prefix = ""","zones":[""", postfix = "]") {
        """{"id":${it.id},"name":"${it.name}","x":${it.x},"y":${it.y},"width":${it.width},"height":${it.height},"main":${it.main},"items":[${itemsJson(it.items)}]}"""
    }
    return """{"deviceId":${config.deviceId},"playlistId":$playlist,"configVersion":${config.configVersion},"items":[${itemsJson(config.items)}]$zones}"""
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

    override suspend fun heartbeat(token: String, playerVersion: String, syncedConfigVersion: Long, report: HeartbeatReport): Desired {
        heartbeatsSeenVersions += syncedConfigVersion
        reportsSeen += report
        return heartbeatResponse()
    }

    override suspend fun config(token: String): FetchedConfig {
        val config = configResponse()
        return FetchedConfig(config, rawJson(config))
    }
}

fun pending(code: String = "ABC123") = PlayerStatus(1, "PENDING", code, "Novo dispositivo")
fun active() = PlayerStatus(1, "ACTIVE", null, "TV")
fun disabled() = PlayerStatus(1, "DISABLED", null, "TV")
