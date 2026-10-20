package com.digitalsignage.player.sync

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.MediaDownloader
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.data.PlayerConfig

/** Onde a configuração sincronizada fica guardada entre execuções (permite funcionar offline após reiniciar). */
interface ConfigStore {
    fun load(): PlayerConfig?
    fun save(raw: String)
    fun clear()
}

class ConfigSync(
    private val api: PlayerApi,
    private val downloader: MediaDownloader,
    private val cache: MediaCache,
    private val store: ConfigStore,
) {

    /**
     * A playlist e a versão identificam o conteúdo. Comparar só a versão não basta: duas playlists
     * diferentes podem estar ambas na versão 1.
     */
    fun needsSync(desired: Desired): Boolean {
        val current = store.load() ?: return true
        return current.playlistId != desired.playlistId || current.configVersion != desired.configVersion
    }

    /**
     * Baixa a configuração e todas as mídias que faltam. A configuração em uso só é trocada depois que
     * TUDO foi baixado e validado; se qualquer download falhar a exceção sobe e a playlist anterior
     * continua tocando.
     */
    suspend fun sync(token: String): PlayerConfig {
        val fetched = api.config(token)

        // Todas as zonas (a mesma mídia em duas zonas baixa uma vez só); páginas da web não têm arquivo.
        val items = fetched.config.allItems()
        for (item in items) {
            if (!item.isWeb && !cache.isCached(item)) {
                downloader.download(token, item.url) { input -> cache.store(item, input) }
            }
        }

        store.save(fetched.raw)
        cache.retainOnly(items)
        return fetched.config
    }

    /** Esquece tudo (dispositivo removido ou desativado no painel). */
    fun reset() {
        store.clear()
        cache.retainOnly(emptyList())
    }

}
