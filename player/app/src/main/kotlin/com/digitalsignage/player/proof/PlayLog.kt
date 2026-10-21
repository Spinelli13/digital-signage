package com.digitalsignage.player.proof

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.json.JSONObject

/** Uma exibição medida pelo Player (a "prova"): hora de início e duração em milissegundos, e o elo da corrente de hashes. */
data class PlayRecord(
    val seq: Long,
    val mediaId: Long?,
    val mediaName: String,
    val mediaType: String,
    val playlistId: Long?,
    val campaignId: Long?,
    val campaignName: String?,
    val zoneName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val plannedMs: Long?,
    val completed: Boolean,
    val prevHash: String,
    val hash: String,
)

/** O que o ZonePlayer entrega quando uma mídia sai da tela (o número de ordem e o hash são dados pelo [PlayLog]). */
data class PlayInfo(
    val deviceId: Long,
    val mediaId: Long?,
    val mediaName: String,
    val mediaType: String,
    val playlistId: Long?,
    val campaignId: Long?,
    val campaignName: String?,
    val zoneName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val plannedMs: Long?,
    val completed: Boolean,
)

fun interface PlayRecorder {
    fun record(play: PlayInfo)

    companion object {
        val NONE = PlayRecorder { }
    }
}

/**
 * A mesma conta do servidor (PlayProofHash.java): SHA-256 dos campos separados por 0x1F. Mexer em qualquer campo de um
 * registro, ou apagar/reordenar registros, quebra o hash deste e de todos os seguintes.
 */
object PlayHash {

    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"
    private const val SEPARATOR = '\u001F'

    fun compute(
        prevHash: String,
        seq: Long,
        deviceId: Long,
        mediaId: Long?,
        mediaName: String,
        mediaType: String,
        playlistId: Long?,
        campaignId: Long?,
        zoneName: String,
        startedAtMs: Long,
        durationMs: Long,
        plannedMs: Long?,
        completed: Boolean,
    ): String {
        val canonical = listOf(
            prevHash,
            seq.toString(),
            deviceId.toString(),
            mediaId?.toString().orEmpty(),
            clean(mediaName),
            clean(mediaType),
            playlistId?.toString().orEmpty(),
            campaignId?.toString().orEmpty(),
            clean(zoneName),
            startedAtMs.toString(),
            durationMs.toString(),
            plannedMs?.toString().orEmpty(),
            if (completed) "1" else "0",
        ).joinToString(SEPARATOR.toString())
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun clean(text: String) = text.replace(SEPARATOR, ' ')

}

/** Onde o Player guarda "qual é o próximo número" e "qual foi o último hash" (precisa sobreviver a reinício). */
interface PlayLogState {
    var nextSeq: Long
    var lastHash: String
}

class MemoryPlayLogState : PlayLogState {
    override var nextSeq = 1L
    override var lastHash = PlayHash.GENESIS
}

/**
 * O diário de exibição do aparelho. Cada exibição vira uma linha num arquivo (uma por linha, JSON) que só sai de lá
 * depois que o servidor confirma o recebimento: sem internet o diário apenas cresce (até [maxPending] linhas; se passar,
 * as mais antigas saem e o servidor verá o salto na numeração).
 *
 * Gravar não bloqueia a tela: [record] só enfileira; quem calcula o número de ordem e o hash (sempre na ordem de chegada)
 * e escreve no disco é uma única linha de execução, e o disco é forçado a gravar de verdade a cada registro (a TV costuma ser
 * desligada da tomada).
 */
class PlayLog(
    private val file: File,
    private val state: PlayLogState,
    private val maxPending: Int = MAX_PENDING,
    private val onDropped: (Int) -> Unit = {},
    private val executor: Executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "play-log").apply { isDaemon = true } },
) : PlayRecorder {

    private val lock = Any()
    private var cached: MutableList<PlayRecord>? = null

    override fun record(play: PlayInfo) {
        executor.execute { append(play) }
    }

    /** Os registros ainda não entregues, do mais antigo para o mais novo. */
    fun pending(limit: Int): List<PlayRecord> = synchronized(lock) { load().take(limit) }

    fun pendingCount(): Int = synchronized(lock) { load().size }

    /** O servidor já tem tudo até [seq]: apaga do aparelho. */
    fun acknowledge(seq: Long) {
        synchronized(lock) {
            val kept = load().filter { it.seq > seq }
            cached = kept.toMutableList()
            rewrite(kept)
        }
    }

    private fun append(play: PlayInfo) {
        synchronized(lock) {
            val records = load()
            val seq = state.nextSeq
            val prev = state.lastHash
            val hash = PlayHash.compute(
                prev, seq, play.deviceId, play.mediaId, play.mediaName, play.mediaType, play.playlistId, play.campaignId, play.zoneName,
                play.startedAtMs, play.durationMs, play.plannedMs, play.completed,
            )
            val record = PlayRecord(
                seq, play.mediaId, play.mediaName, play.mediaType, play.playlistId, play.campaignId, play.campaignName, play.zoneName,
                play.startedAtMs, play.durationMs, play.plannedMs, play.completed, prev, hash,
            )
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).use { out ->
                out.write((toJson(record).toString() + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            records += record
            state.nextSeq = seq + 1
            state.lastHash = hash

            if (records.size > maxPending) {
                val excess = records.size - maxPending
                repeat(excess) { records.removeAt(0) }
                rewrite(records)
                onDropped(excess)
            }
        }
    }

    /** Lê o arquivo uma vez; depois trabalha na memória. Uma linha quebrada (desligou no meio) é ignorada. */
    private fun load(): MutableList<PlayRecord> {
        cached?.let { return it }
        val records = mutableListOf<PlayRecord>()
        if (file.isFile) {
            file.forEachLine(Charsets.UTF_8) { line ->
                if (line.isNotBlank()) {
                    try {
                        records += fromJson(JSONObject(line))
                    } catch (e: Exception) {
                        // linha truncada pela queda de energia
                    }
                }
            }
        }
        // O estado guardado pode estar atrasado em relação ao arquivo (queda entre os dois): o arquivo manda.
        records.lastOrNull()?.let { last ->
            if (last.seq >= state.nextSeq) {
                state.nextSeq = last.seq + 1
                state.lastHash = last.hash
            }
        }
        cached = records
        return records
    }

    private fun rewrite(records: List<PlayRecord>) {
        val temp = File(file.path + ".tmp")
        FileOutputStream(temp).use { out ->
            for (record in records) out.write((toJson(record).toString() + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    companion object {
        const val MAX_PENDING = 20_000
        const val BATCH = 200

        fun toJson(r: PlayRecord): JSONObject = JSONObject()
            .put("seq", r.seq)
            .put("mediaId", r.mediaId ?: JSONObject.NULL)
            .put("mediaName", r.mediaName)
            .put("mediaType", r.mediaType)
            .put("playlistId", r.playlistId ?: JSONObject.NULL)
            .put("campaignId", r.campaignId ?: JSONObject.NULL)
            .put("campaignName", r.campaignName ?: JSONObject.NULL)
            .put("zoneName", r.zoneName)
            .put("startedAtMs", r.startedAtMs)
            .put("durationMs", r.durationMs)
            .put("plannedMs", r.plannedMs ?: JSONObject.NULL)
            .put("completed", r.completed)
            .put("prevHash", r.prevHash)
            .put("hash", r.hash)

        fun fromJson(j: JSONObject) = PlayRecord(
            seq = j.getLong("seq"),
            mediaId = if (j.isNull("mediaId")) null else j.getLong("mediaId"),
            mediaName = j.getString("mediaName"),
            mediaType = j.getString("mediaType"),
            playlistId = if (j.isNull("playlistId")) null else j.getLong("playlistId"),
            campaignId = if (j.isNull("campaignId")) null else j.getLong("campaignId"),
            campaignName = if (j.isNull("campaignName")) null else j.getString("campaignName"),
            zoneName = j.getString("zoneName"),
            startedAtMs = j.getLong("startedAtMs"),
            durationMs = j.getLong("durationMs"),
            plannedMs = if (j.isNull("plannedMs")) null else j.getLong("plannedMs"),
            completed = j.getBoolean("completed"),
            prevHash = j.getString("prevHash"),
            hash = j.getString("hash"),
        )
    }

}
