package com.digitalsignage.player.widget

import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.UnauthorizedException
import kotlinx.coroutines.CancellationException

/**
 * Busca no servidor os dados dos widgets que a TV exibe (e dos que a agenda offline vai exibir) e guarda para uso sem
 * internet. Um widget que falha mantém o último dado; os outros não são afetados.
 */
class WidgetRefresher(private val api: PlayerApi, private val store: WidgetStore) {

    /** Devolve quantos widgets receberam dados novos. */
    suspend fun refresh(token: String, config: PlayerConfig): Int {
        val all = (config.allItems() + (config.plan?.allItems() ?: emptyList())).filter { it.widget != null }.distinctBy { it.mediaId }
        store.retainOnly(all.map { it.mediaId }.toSet())
        var updated = 0
        for (item in all.filter { it.widget!!.needsData }) {
            try {
                if (store.saveResponse(item.mediaId, api.widgetData(token, item.url))) updated++
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnauthorizedException) {
                throw e
            } catch (e: Exception) {
                // sem internet ou servidor ocupado: fica o último valor guardado
            }
        }
        return updated
    }
}
