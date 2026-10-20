package com.digitalsignage.player.data

import android.content.Context
import com.digitalsignage.player.BuildConfig
import com.digitalsignage.player.sync.ConfigStore
import java.security.SecureRandom

/** Preferências do aparelho: servidor, token do dispositivo e a última configuração sincronizada. */
class Settings(context: Context) : ConfigStore {

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
        const val KEY_LAST_CRASH = "last_crash"
    }

}
