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
    }

}
