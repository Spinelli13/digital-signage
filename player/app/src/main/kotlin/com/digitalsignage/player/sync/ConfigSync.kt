package com.digitalsignage.player.sync

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.MediaDownloader
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.data.PlayerConfig
import java.io.IOException

/** Onde a configuração sincronizada fica guardada entre execuções (permite funcionar offline após reiniciar). */
interface ConfigStore {
    fun load(): PlayerConfig?
    fun save(raw: String)
    fun clear()
}

/** Não há espaço no aparelho para baixar o conteúdo novo (o que está tocando continua intacto). */
class InsufficientSpaceException(val neededBytes: Long, val freeBytes: Long) :
    IOException("Espaço insuficiente: faltam ${(neededBytes - freeBytes).coerceAtLeast(0) / MB} MB")

private const val MB = 1024L * 1024L

class ConfigSync(
    private val api: PlayerApi,
    private val downloader: MediaDownloader,
    private val cache: MediaCache,
    private val store: ConfigStore,
    /** Espaço livre no armazenamento do app, em bytes (nos testes, um valor fixo). */
    private val freeBytes: () -> Long = { Long.MAX_VALUE },
    /** Folga que sempre sobra livre para o Android e para o cache do sistema. */
    private val reserveBytes: Long = 50 * MB,
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
        requireRoomFor(items)
        for (item in items) {
            if (!item.isWeb && !cache.isCached(item)) {
                downloader.download(token, item.url) { input -> cache.store(item, input) }
            }
        }

        store.save(fetched.raw)
        cache.retainOnly(items)
        return fetched.config
    }

    /**
     * O conteúdo novo precisa caber ANTES do que está tocando ser tocado: a troca é atômica, então por um instante os dois
     * conjuntos coexistem. Sem espaço, limpa o que não pertence a nenhum dos dois (downloads interrompidos) e, se ainda não
     * couber, falha com uma mensagem clara em vez de encher o disco no meio do download.
     */
    private fun requireRoomFor(items: List<ConfigItem>) {
        val needed = items.filter { !it.isWeb && !cache.isCached(it) }.sumOf { it.fileSize.coerceAtLeast(0) }
        if (needed <= 0 || freeBytes() - reserveBytes >= needed) return

        cache.retainOnly(items + (store.load()?.allItems() ?: emptyList()))
        val free = freeBytes() - reserveBytes
        if (free < needed) throw InsufficientSpaceException(needed, free.coerceAtLeast(0))
    }

    /** Esquece tudo (dispositivo removido ou desativado no painel). */
    fun reset() {
        store.clear()
        cache.retainOnly(emptyList())
    }

}
