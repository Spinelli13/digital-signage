package com.digitalsignage.player

import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.FetchedConfig
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.MediaDownloader
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.PlayerStatus
import com.digitalsignage.player.sync.ConfigStore
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
fun rawJson(config: PlayerConfig): String {
    val items = config.items.joinToString(",") {
        """{"mediaId":${it.mediaId},"name":"${it.name}","type":"${it.type}","url":"${it.url}",""" +
            """"checksum":${it.checksum?.let { c -> "\"$c\"" } ?: "null"},"fileSize":${it.fileSize},""" +
            """"position":${it.position},"durationSeconds":${it.durationSeconds}}"""
    }
    val playlist = config.playlistId?.toString() ?: "null"
    return """{"deviceId":${config.deviceId},"playlistId":$playlist,"configVersion":${config.configVersion},"items":[$items]}"""
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
    var registerCalls = 0

    override suspend fun register(token: String, playerVersion: String): PlayerStatus {
        registerCalls++
        return registerResponse()
    }

    override suspend fun status(token: String): PlayerStatus = statusResponse()

    override suspend fun heartbeat(token: String, playerVersion: String, syncedConfigVersion: Long): Desired {
        heartbeatsSeenVersions += syncedConfigVersion
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
