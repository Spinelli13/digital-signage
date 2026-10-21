package com.digitalsignage.player.restart

/**
 * As regras (sem Android) de quando o Player deve se reabrir sozinho:
 *  - depois de uma queda: logo em seguida, com espera crescente se ele cair em sequência (evita um laço de quedas);
 *  - o "vigia" confere de tempos em tempos: se o app não deu sinal de vida (o sistema o matou por falta de memória, por
 *    exemplo), reabre; se a pessoa saiu de propósito (Voltar ou Início), espera um tempo antes de trazê-lo de volta.
 */
object RestartPolicy {

    /** De quanto em quanto tempo o vigia confere. */
    const val WATCHDOG_INTERVAL_MS = 120_000L

    /** A tela do Player marca "estou vivo" a cada [ALIVE_EVERY_MS]; passado [STALE_AFTER_MS] sem marca ele é dado como fechado. */
    const val ALIVE_EVERY_MS = 30_000L
    const val STALE_AFTER_MS = 150_000L

    /** Depois que a pessoa sai de propósito, o vigia deixa em paz por este tempo (dá para mexer nas configurações da TV). */
    const val USER_EXIT_GRACE_MS = 10 * 60_000L

    /** Quedas mais antigas que isto não contam para a espera crescente. */
    const val CRASH_WINDOW_MS = 15 * 60_000L

    private val CRASH_DELAYS_MS = longArrayOf(3_000, 10_000, 30_000, 60_000, 300_000)

    /** Quedas recentes (incluindo a de agora) para decidir a espera. */
    fun recentCrashes(history: List<Long>, now: Long): List<Long> =
        history.filter { now - it in 0..CRASH_WINDOW_MS } + now

    /** Quanto esperar antes de reabrir depois da queda de número [recentCount] (1 = a primeira da série). */
    fun crashDelayMs(recentCount: Int): Long =
        CRASH_DELAYS_MS[(recentCount - 1).coerceIn(0, CRASH_DELAYS_MS.size - 1)]

    /**
     * O vigia deve reabrir o Player agora?
     * @param enabled "reabrir sozinho" está ligado
     * @param lastAliveAt o último sinal de vida da tela (0 = nunca)
     * @param userExitUntil até quando respeitar uma saída de propósito (0 = nenhuma)
     */
    fun shouldRelaunch(now: Long, enabled: Boolean, lastAliveAt: Long, userExitUntil: Long): Boolean {
        if (!enabled) return false
        if (now < userExitUntil) return false
        return lastAliveAt <= 0 || now - lastAliveAt > STALE_AFTER_MS
    }

}
