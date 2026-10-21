package com.digitalsignage.player.widget

import java.io.File
import org.json.JSONObject

/** Os dados de um widget (clima, notícias, cotações) como o servidor entregou, e quando chegaram. */
class StoredWidget(val data: JSONObject, val receivedAtMs: Long)

/**
 * Guarda o último dado de cada widget no aparelho. É o que faz um widget continuar útil sem internet: a TV mostra o último
 * clima, as últimas manchetes e as últimas cotações (o widget avisa "atualizado há…" quando já estão velhos). Um erro na
 * hora de buscar nunca apaga o que já estava guardado.
 */
class WidgetStore(private val dir: File, private val wall: () -> Long = { System.currentTimeMillis() }) {

    init {
        dir.mkdirs()
    }

    /** Guarda a resposta do servidor se ela trouxe dados; devolve falso (e não mexe em nada) se não trouxe. */
    fun saveResponse(mediaId: Long, response: String): Boolean {
        val data = try {
            JSONObject(response).optJSONObject("data")
        } catch (e: Exception) {
            null
        } ?: return false
        val temp = File(dir, "$mediaId.json.tmp")
        temp.writeText(JSONObject().put("receivedAtMs", wall()).put("data", data).toString())
        val target = fileOf(mediaId)
        if (target.exists()) target.delete()
        return temp.renameTo(target)
    }

    fun load(mediaId: Long): StoredWidget? {
        val file = fileOf(mediaId)
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            StoredWidget(json.getJSONObject("data"), json.optLong("receivedAtMs"))
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** Apaga os dados dos widgets que saíram da configuração. */
    fun retainOnly(mediaIds: Set<Long>) {
        dir.listFiles()?.forEach { file ->
            val id = file.name.substringBefore('.').toLongOrNull()
            if (file.isFile && (id == null || id !in mediaIds)) file.delete()
        }
    }

    private fun fileOf(mediaId: Long) = File(dir, "$mediaId.json")
}
