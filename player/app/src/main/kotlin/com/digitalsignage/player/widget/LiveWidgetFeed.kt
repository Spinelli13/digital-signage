package com.digitalsignage.player.widget

import android.media.AudioManager
import android.media.ToneGenerator
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.time.TrustedClock
import org.json.JSONObject

/** O aviso sonoro de uma chamada nova (separado para os testes não precisarem de som). */
fun interface Beeper {
    fun beep()
}

/** Toca um duplo bip no volume de mídia da TV (o mesmo dos vídeos). */
class AndroidBeeper : Beeper {
    override fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 700)
            // A ToneGenerator precisa ser solta depois que o som acaba, senão acumulam e o aparelho para de tocar.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 1_500)
        } catch (e: RuntimeException) {
            // sem saída de áudio (ou o aparelho recusou): a chamada continua aparecendo na tela
        }
    }
}

/**
 * Os widgets "ao vivo" (hoje, o painel de chamadas da fila de atendimento) não podem esperar os 10 minutos dos outros: enquanto
 * estão na tela o Player pergunta ao servidor a cada poucos segundos e entrega o dado novo à página já aberta. Cada chamada
 * nova (senha diferente, ou a mesma chamada de novo) toca o aviso, menos na primeira consulta (abrir o painel não é uma chamada).
 */
class LiveWidgetFeed(
    private val api: PlayerApi,
    private val token: () -> String,
    private val store: WidgetStore,
    private val clock: TrustedClock,
    private val beeper: Beeper,
) {
    private val lastCall = HashMap<Long, String>()

    /** O JSON para a página (`Smartplay.update`), ou nulo se o servidor não trouxe dados. Erros de rede sobem para quem chamou. */
    suspend fun fetch(item: ConfigItem): String? {
        val response = api.widgetData(token(), item.url)
        if (!store.saveResponse(item.mediaId, response)) return null
        val root = JSONObject(response)
        val data = root.optJSONObject("data") ?: return null
        root.optLong("serverTimeMs").takeIf { it > 0 }?.let { clock.sync(it) }

        val first = data.optJSONArray("calls")?.optJSONObject(0)
        val key = first?.let { "${it.optString("label")}@${it.optLong("calledAtMs")}#${it.optInt("callCount")}" } ?: ""
        val previous = lastCall.put(item.mediaId, key)
        if (previous != null && key.isNotEmpty() && key != previous && soundOn(item)) beeper.beep()
        return JSONObject().put("data", data).put("nowMs", clock.now()).toString()
    }

    private fun soundOn(item: ConfigItem): Boolean = try {
        JSONObject(item.widget?.settingsJson ?: "{}").optBoolean("sound", true)
    } catch (e: Exception) {
        true
    }
}
