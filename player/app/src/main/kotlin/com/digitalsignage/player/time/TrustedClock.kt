package com.digitalsignage.player.time

/** Guarda a diferença entre a hora do servidor e a do relógio da TV (sobrevive a reinício do Player). */
interface ClockOffsetStore {
    var clockOffsetMs: Long
}

/**
 * A hora de verdade, mesmo sem internet. Relógio de TV de verdade erra (sem pilha, sem rede no boot, fuso errado): como a
 * agenda offline depende da hora, a TV anota a hora do SERVIDOR a cada contato e, daí em diante, conta o tempo pelo
 * relógio monotônico (que não pula quando alguém ajusta a hora). Depois de reiniciar o Player (sem o âncora na memória),
 * usa o relógio do aparelho corrigido pela última diferença conhecida.
 */
class TrustedClock(
    private val wall: () -> Long = { System.currentTimeMillis() },
    /** Milissegundos desde o boot, contando o sono do aparelho (no Android, SystemClock.elapsedRealtime). */
    val uptime: () -> Long = { System.nanoTime() / 1_000_000 },
    private val store: ClockOffsetStore? = null,
) {

    private var anchorServerMs = 0L
    private var anchorUptimeMs = 0L
    private var anchored = false

    /** Chamado a cada contato com o servidor, com a hora que ele informou. 0 (servidor antigo) é ignorado. */
    @Synchronized
    fun sync(serverMs: Long) {
        if (serverMs <= 0) return
        anchorServerMs = serverMs
        anchorUptimeMs = uptime()
        anchored = true
        store?.clockOffsetMs = serverMs - wall()
    }

    /** Agora, em milissegundos desde 1970. */
    @Synchronized
    fun now(): Long =
        if (anchored) anchorServerMs + (uptime() - anchorUptimeMs) else wall() + (store?.clockOffsetMs ?: 0L)

    /** Já se falou com o servidor desde que o Player abriu (a hora é a do servidor, e não só a do aparelho)? */
    @Synchronized
    fun isSynced(): Boolean = anchored
}
