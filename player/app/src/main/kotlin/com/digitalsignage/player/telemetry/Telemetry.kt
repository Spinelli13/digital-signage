package com.digitalsignage.player.telemetry

/** Gravidade de uma ocorrência relatada ao servidor (mesmos nomes da API). */
enum class Level { INFO, WARN, ERROR }

/** Uma ocorrência (erro, aviso, início do Player) com o instante em que aconteceu. [seq] só serve para saber o que já foi entregue. */
data class ReportedEvent(val seq: Long, val level: Level, val message: String, val atMillis: Long)

/** Quantas vezes uma mídia foi exibida até o fim e por quantos segundos no total. */
data class PlayCount(val mediaId: Long, val count: Int, val seconds: Long)

/** O que o heartbeat leva ao servidor além da versão: a mídia na tela, as ocorrências e as exibições pendentes. */
data class HeartbeatReport(
    val currentMediaId: Long?,
    val events: List<ReportedEvent>,
    val plays: List<PlayCount>,
) {
    val isEmpty get() = events.isEmpty() && plays.isEmpty()

    companion object {
        val EMPTY = HeartbeatReport(null, emptyList(), emptyList())
    }
}

/**
 * Tudo que o Player conta ao servidor sobre si mesmo. Fica na memória: sem internet as ocorrências esperam (as
 * mais antigas saem se passarem de [maxQueued]) e vão no primeiro heartbeat que o servidor aceitar.
 *
 * O envio é em duas etapas, para nada se perder se o heartbeat falhar: [snapshot] copia o que há e só
 * [acknowledge] (depois da resposta do servidor) tira da fila exatamente o que foi enviado — o que chegou
 * no meio do caminho continua pendente.
 */
class Telemetry(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxQueued: Int = MAX_QUEUED,
    private val dedupeMs: Long = DEDUPE_MS,
    private val maxEventsPerReport: Int = MAX_EVENTS_PER_REPORT,
    private val maxPlaysPerReport: Int = MAX_PLAYS_PER_REPORT,
) {

    private val queue = ArrayDeque<ReportedEvent>()
    private val lastSeen = HashMap<String, Long>()
    private val plays = LinkedHashMap<Long, LongArray>() // mediaId -> [exibições, segundos]
    private var nextSeq = 1L
    private var currentMediaId: Long? = null

    /** Registra uma ocorrência. A mesma mensagem não se repete dentro de [dedupeMs] (internet fora gera erro a cada tentativa). */
    @Synchronized
    fun event(level: Level, message: String) {
        val text = message.trim().take(MAX_MESSAGE)
        if (text.isEmpty()) return
        val now = clock()
        val key = "${level.name}|$text"
        val last = lastSeen[key]
        if (last != null && now - last < dedupeMs) return
        lastSeen[key] = now
        if (lastSeen.size > MAX_REMEMBERED) lastSeen.entries.removeAll { now - it.value >= dedupeMs }

        queue.addLast(ReportedEvent(nextSeq++, level, text, now))
        while (queue.size > maxQueued) queue.removeFirst()
    }

    /** A mídia que está na tela agora (nula quando não há exibição). */
    @Synchronized
    fun mediaShown(mediaId: Long?) {
        currentMediaId = mediaId
    }

    /** Uma exibição completa de [mediaId], que durou [seconds]. */
    @Synchronized
    fun played(mediaId: Long, seconds: Long) {
        val entry = plays.getOrPut(mediaId) { LongArray(2) }
        entry[0] += 1
        entry[1] += seconds.coerceAtLeast(0)
    }

    /** Cópia do que está pendente (sem alterar nada): as ocorrências mais antigas primeiro, dentro do limite do servidor. */
    @Synchronized
    fun snapshot(): HeartbeatReport = HeartbeatReport(
        currentMediaId = currentMediaId,
        events = queue.take(maxEventsPerReport),
        plays = plays.entries.take(maxPlaysPerReport).map { (id, v) -> PlayCount(id, v[0].toInt(), v[1]) },
    )

    /** O servidor recebeu [report]: tira da fila só o que ele continha. */
    @Synchronized
    fun acknowledge(report: HeartbeatReport) {
        val lastSeq = report.events.maxOfOrNull { it.seq }
        if (lastSeq != null) queue.removeAll { it.seq <= lastSeq }
        for (sent in report.plays) {
            val entry = plays[sent.mediaId] ?: continue
            entry[0] -= sent.count
            entry[1] -= sent.seconds
            if (entry[0] <= 0) plays.remove(sent.mediaId)
        }
    }

    @Synchronized
    fun pendingEvents(): Int = queue.size

    companion object {
        const val MAX_QUEUED = 100
        const val DEDUPE_MS = 5 * 60_000L

        /** Limites do servidor (um relato maior é recusado inteiro, com HTTP 400). */
        const val MAX_EVENTS_PER_REPORT = 30
        const val MAX_PLAYS_PER_REPORT = 100
        const val MAX_MESSAGE = 500

        private const val MAX_REMEMBERED = 200

        /** Instância do processo, compartilhada pela Activity, pelo motor e pelos receivers. */
        val shared = Telemetry()
    }

}
