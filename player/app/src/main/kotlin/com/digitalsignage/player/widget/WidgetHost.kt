package com.digitalsignage.player.widget

import android.content.Context
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.time.TrustedClock
import org.json.JSONObject

/** Os modelos visuais dos widgets (os mesmos que a pré-visualização do painel usa). */
interface WidgetAssets {
    /** A página inteira (HTML, CSS e JavaScript juntos), com o marcador [WidgetTemplate.START] onde a TV entrega os dados. */
    fun template(): String
}

/** Lê os modelos da pasta de assets do aplicativo. */
class AndroidWidgetAssets(private val context: Context) : WidgetAssets {
    private val built: String by lazy {
        fun read(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        WidgetTemplate.assemble(read("widget.html"), read("widget.css"), read("widget.js"))
    }

    override fun template(): String = built
}

object WidgetTemplate {
    const val CSS = "/*__CSS__*/"
    const val JS = "/*__JS__*/"
    const val START = "/*__START__*/"

    /** O replace de texto do Kotlin é literal (o "R$" do JavaScript não é confundido com um padrão). */
    fun assemble(html: String, css: String, js: String): String =
        html.replace(CSS, css).replace(JS, js + "\n" + START)
}

/**
 * Monta a página de cada widget: o modelo, as escolhas do painel, os últimos dados guardados e a hora de verdade. Texto de
 * terceiros (manchetes) só entra como dado JSON; o modelo o coloca na tela como texto puro.
 */
class WidgetHost(
    private val assets: WidgetAssets,
    private val store: WidgetStore,
    private val clock: TrustedClock,
) {

    fun html(item: ConfigItem): String {
        val spec = item.widget ?: throw IllegalStateException("A mídia \"${item.name}\" não é um widget")
        val settings = try {
            JSONObject(spec.settingsJson)
        } catch (e: Exception) {
            JSONObject()
        }
        val stored = if (spec.needsData) store.load(item.mediaId) else null
        val init = JSONObject()
            .put("kind", spec.kind)
            .put("settings", settings)
            .put("data", stored?.data ?: JSONObject.NULL)
            .put("nowMs", clock.now())
        return assets.template().replace(WidgetTemplate.START, "Smartplay.start(" + safe(init.toString()) + ");")
    }

    /** Dentro de um script, o texto "barra-script" numa manchete fecharia a página, e U+2028/2029 quebram o JavaScript antigo. */
    private fun safe(json: String): String = safeScript(json)

    companion object {
        /** O mesmo cuidado para o JSON que o Player entrega à página já aberta (`Smartplay.update`). */
        fun safeScript(json: String): String = json.replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    }
}
