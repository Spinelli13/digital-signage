package com.digitalsignage.player.errors

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Ponto único por onde todo erro do Player chega ao usuário. A tela mostra cada mensagem por 10 segundos
 * (em fila, uma depois da outra).
 *
 * A mesma mensagem não se repete dentro de [dedupeMs]: com a internet fora, o Player tenta de novo a cada
 * poucos segundos, e sem isso a tarja de erro ficaria permanentemente sobre as propagandas.
 */
class ErrorReporter(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val dedupeMs: Long = DEFAULT_DEDUPE_MS,
) {

    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = flow

    private val lastShown = HashMap<String, Long>()

    /** Chamado com cada mensagem aceita (não repetida): o Player a registra também para o servidor. */
    @Volatile
    var sink: ((String) -> Unit)? = null

    /** Devolve true se a mensagem foi aceita para exibição, false se foi suprimida por repetição recente. */
    @Synchronized
    fun report(message: String, repeatAfterMs: Long = dedupeMs): Boolean {
        val now = clock()
        val last = lastShown[message]
        if (last != null && now - last < repeatAfterMs) return false
        lastShown[message] = now
        flow.tryEmit(message)
        sink?.invoke(message)
        return true
    }

    companion object {
        const val DEFAULT_DEDUPE_MS = 5 * 60_000L
        const val DISPLAY_MS = 10_000L

        /** Instância do processo, usada também pelos receivers (que não têm acesso à Activity). */
        val shared = ErrorReporter()
    }

}
