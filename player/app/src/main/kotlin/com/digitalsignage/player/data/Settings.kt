package com.digitalsignage.player.data

import android.content.Context
import com.digitalsignage.player.BuildConfig
import com.digitalsignage.player.sync.ConfigStore
import java.security.SecureRandom

/** Preferências do aparelho: servidor, token do dispositivo e a última configuração sincronizada. */
class Settings(context: Context) : ConfigStore, com.digitalsignage.player.proof.PlayLogState, com.digitalsignage.player.time.ClockOffsetStore {

    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, null) ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) {
            prefs.edit().putString(KEY_SERVER, value.trim().trimEnd('/')).commit()
        }

    /**
     * Segredo de 256 bits gerado no próprio aparelho na primeira execução. Só o hash dele chega a ser
     * guardado no servidor; este valor autentica todas as chamadas do Player.
     */
    val deviceToken: String
        @Synchronized get() {
            prefs.getString(KEY_TOKEN, null)?.let { return it }
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val token = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_TOKEN, token).commit()
            return token
        }

    /** Endereço do latest.json do site de downloads, de onde o Player busca versões novas dele mesmo. */
    var updateUrl: String
        get() = prefs.getString(KEY_UPDATE_URL, null) ?: BuildConfig.DEFAULT_UPDATE_URL
        set(value) {
            prefs.edit().putString(KEY_UPDATE_URL, value.trim()).commit()
        }

    /** Ligada por padrão no APK de release; nas builds de desenvolvimento (debug) começa desligada. */
    var autoUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, !BuildConfig.DEBUG)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).commit()
        }

    /** "HH:mm-HH:mm"; vazio = a qualquer hora. Ver UpdateWindow. */
    var updateWindow: String
        get() = prefs.getString(KEY_UPDATE_WINDOW, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_UPDATE_WINDOW, value.trim()).commit()
        }

    /** Última queda inesperada do app (gravada antes de o processo morrer; exibida na próxima abertura). */
    var lastCrash: String?
        get() = prefs.getString(KEY_LAST_CRASH, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_CRASH, value).commit()
        }

    /** A última diferença conhecida entre a hora do servidor e a do relógio da TV (para a agenda offline após reiniciar). */
    override var clockOffsetMs: Long
        get() = prefs.getLong(KEY_CLOCK_OFFSET, 0)
        set(value) {
            prefs.edit().putLong(KEY_CLOCK_OFFSET, value).apply()
        }

    /** O próximo número de ordem do diário de exibição (nunca volta, mesmo depois de o servidor confirmar e o arquivo esvaziar). */
    override var nextSeq: Long
        get() = prefs.getLong(KEY_PLAY_SEQ, 1)
        set(value) {
            prefs.edit().putLong(KEY_PLAY_SEQ, value).commit()
        }

    /** O hash do último registro do diário: o próximo registro aponta para ele. */
    override var lastHash: String
        get() = prefs.getString(KEY_PLAY_HASH, null) ?: com.digitalsignage.player.proof.PlayHash.GENESIS
        set(value) {
            prefs.edit().putString(KEY_PLAY_HASH, value).commit()
        }

    /** "Reabrir sozinho" (depois de uma queda e pelo vigia). Ligado por padrão; ver restart/Restarter. */
    var autoRestart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESTART, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_RESTART, value).commit()
        }

    /** Último sinal de vida da tela do Player (ms desde 1970); 0 = nunca. Gravado sem esperar o disco. */
    var lastAliveAt: Long
        get() = prefs.getLong(KEY_ALIVE_AT, 0)
        set(value) {
            prefs.edit().putLong(KEY_ALIVE_AT, value).apply()
        }

    /** Até quando o vigia respeita uma saída de propósito (Voltar/Início). */
    var userExitUntil: Long
        get() = prefs.getLong(KEY_USER_EXIT_UNTIL, 0)
        set(value) {
            prefs.edit().putLong(KEY_USER_EXIT_UNTIL, value).commit()
        }

    /** Uma queda agendou a reabertura e ela ainda não aconteceu (a tela ao abrir apaga isto). */
    var pendingCrashRestart: Boolean
        get() = prefs.getBoolean(KEY_PENDING_RESTART, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PENDING_RESTART, value).commit()
        }

    /** Instantes das últimas quedas, para a espera crescente quando o app cai em sequência. */
    var crashTimes: List<Long>
        get() = prefs.getString(KEY_CRASH_TIMES, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        set(value) {
            prefs.edit().putString(KEY_CRASH_TIMES, value.takeLast(10).joinToString(",")).commit()
        }

    /** Por que o Player foi reaberto sozinho da última vez (contado ao servidor na próxima abertura). */
    var lastRestartReason: String?
        get() = prefs.getString(KEY_RESTART_REASON, null)
        set(value) {
            prefs.edit().putString(KEY_RESTART_REASON, value).commit()
        }

    /** Último pedido de diagnóstico (vindo do painel) que este aparelho já tratou. */
    var lastDiagnosticToken: Long
        get() = prefs.getLong(KEY_DIAGNOSTIC_TOKEN, 0)
        set(value) {
            prefs.edit().putLong(KEY_DIAGNOSTIC_TOKEN, value).commit()
        }

    /** Último pedido de verificação de atualização (vindo do painel) que este aparelho já tratou. */
    var lastUpdateToken: Long
        get() = prefs.getLong(KEY_UPDATE_TOKEN, 0)
        set(value) {
            prefs.edit().putLong(KEY_UPDATE_TOKEN, value).commit()
        }

    /** Último resultado da verificação/instalação de atualização (para diagnóstico). */
    var lastUpdateStatus: String?
        get() = prefs.getString(KEY_UPDATE_STATUS, null)
        set(value) {
            prefs.edit().putString(KEY_UPDATE_STATUS, value).apply()
        }

    override fun load(): PlayerConfig? =
        prefs.getString(KEY_CONFIG, null)?.let { raw ->
            try {
                Json.parseConfig(raw)
            } catch (e: Exception) {
                null
            }
        }

    // commit() (síncrono): a configuração precisa estar em disco se a TV for desligada da tomada em seguida.
    override fun save(raw: String) {
        prefs.edit().putString(KEY_CONFIG, raw).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_CONFIG).commit()
    }

    /** Volta ao estado de fábrica do Player: novo token, novo código de ativação. */
    fun resetAll() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_CONFIG).commit()
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_TOKEN = "device_token"
        const val KEY_CONFIG = "config_json"
        const val KEY_UPDATE_URL = "update_url"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_UPDATE_WINDOW = "update_window"
        const val KEY_UPDATE_STATUS = "update_status"
        const val KEY_UPDATE_TOKEN = "update_token"
        const val KEY_DIAGNOSTIC_TOKEN = "diagnostic_token"
        const val KEY_LAST_CRASH = "last_crash"
        const val KEY_CLOCK_OFFSET = "clock_offset_ms"
        const val KEY_PLAY_SEQ = "play_seq"
        const val KEY_PLAY_HASH = "play_hash"
        const val KEY_AUTO_RESTART = "auto_restart"
        const val KEY_ALIVE_AT = "alive_at"
        const val KEY_USER_EXIT_UNTIL = "user_exit_until"
        const val KEY_PENDING_RESTART = "pending_restart"
        const val KEY_CRASH_TIMES = "crash_times"
        const val KEY_RESTART_REASON = "restart_reason"
    }

}
