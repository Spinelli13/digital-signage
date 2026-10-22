package com.digitalsignage.player.sync

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.Desired
import com.digitalsignage.player.data.MediaDownloader
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.time.TrustedClock
import java.io.IOException
import kotlinx.coroutines.CancellationException

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
    /** A hora (do servidor, quando se sabe): decide quando renovar a agenda offline. */
    private val clock: TrustedClock = TrustedClock(),
    /** Avisos para o histórico da TV no painel (ex.: sem espaço para guardar toda a agenda). */
    private val onNotice: (String) -> Unit = {},
) {

    private var lastSyncAt = 0L

    /** Quando a configuração foi sincronizada pela última vez (milissegundos; 0 = ainda não, desde que o Player abriu). */
    val lastSyncAtMs: Long get() = lastSyncAt
    private var prefetchRetryAt = 0L

    /**
     * A playlist e a versão identificam o conteúdo. Comparar só a versão não basta: duas playlists
     * diferentes podem estar ambas na versão 1.
     */
    fun needsSync(desired: Desired): Boolean {
        val current = store.load() ?: return true
        // A campanha também conta: a mesma playlist pode vir de uma campanha ou da própria TV, e a comprovação cita qual.
        if (current.playlistId != desired.playlistId || current.configVersion != desired.configVersion ||
            current.campaignId != desired.campaignId
        ) {
            return true
        }
        // A agenda offline mudou (campanha criada, editada, pausada, playlist trocada...), mesmo que o que está no ar seja igual.
        val plan = current.plan
        if ((plan?.version ?: 0L) != desired.planVersion) return true

        val now = clock.now()
        // Mídias da agenda que falharam no download: tenta de novo depois de alguns minutos.
        if (prefetchRetryAt != 0L && now >= prefetchRetryAt) return true
        // A agenda cobre 7 dias; renova quando restam menos de 5 (no máximo uma vez por hora, para nunca virar laço).
        return plan != null && plan.untilMs - now < PLAN_REFRESH_BELOW_MS && now - lastSyncAt > PLAN_REFRESH_EVERY_MS
    }

    /**
     * Baixa a configuração e todas as mídias que faltam. A configuração em uso só é trocada depois que
     * TUDO foi baixado e validado; se qualquer download falhar a exceção sobe e a playlist anterior
     * continua tocando.
     */
    suspend fun sync(token: String): PlayerConfig {
        val fetched = api.config(token)
        prefetchRetryAt = 0L

        // Todas as zonas (a mesma mídia em duas zonas baixa uma vez só); páginas da web não têm arquivo.
        val items = fetched.config.allItems()
        requireRoomFor(items)
        for (item in items) {
            if (item.hasFile && !cache.isCached(item)) {
                downloader.download(token, item.url) { input -> cache.store(item, input) }
            }
        }

        store.save(fetched.raw)
        // Guarda também o que a agenda offline já tinha no disco; o que não pertence a nada disso sai.
        cache.retainOnly(fetched.config.retainable())
        prefetchPlan(token, fetched.config)
        lastSyncAt = clock.now()
        return fetched.config
    }

    /**
     * Baixa as mídias das campanhas que vão ao ar nos próximos dias, a mais próxima primeiro, DEPOIS de o que está no ar estar
     * pronto: a agenda nunca atrasa nem derruba a troca de conteúdo. Sem espaço, deixa de fora o que não couber e avisa.
     */
    private suspend fun prefetchPlan(token: String, config: PlayerConfig) {
        val plan = config.plan ?: return
        val inUse = config.allItems().map { it.mediaId }.toSet()
        val missing = plan.allItems().filter { it.mediaId !in inUse && it.hasFile && !cache.isCached(it) }
        if (missing.isEmpty()) return

        var noSpace = 0
        var failed = 0
        for (item in missing) {
            if (freeBytes() - reserveBytes < item.fileSize.coerceAtLeast(0)) {
                noSpace++
                continue
            }
            try {
                downloader.download(token, item.url) { input -> cache.store(item, input) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnauthorizedException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        if (noSpace > 0) {
            onNotice("Sem espaço para guardar toda a agenda offline: $noSpace mídia(s) de campanhas futuras ficaram de fora. Libere espaço na TV ou use arquivos menores.")
        }
        if (failed > 0) {
            onNotice("Não foi possível guardar $failed mídia(s) da agenda offline; tentarei de novo em alguns minutos.")
            prefetchRetryAt = clock.now() + PREFETCH_RETRY_MS
        }
    }

    /**
     * O conteúdo novo precisa caber ANTES do que está tocando ser tocado: a troca é atômica, então por um instante os dois
     * conjuntos coexistem. Sem espaço, limpa o que não pertence a nenhum dos dois (downloads interrompidos) e, se ainda não
     * couber, falha com uma mensagem clara em vez de encher o disco no meio do download.
     */
    private fun requireRoomFor(items: List<ConfigItem>) {
        val needed = items.filter { it.hasFile && !cache.isCached(it) }.sumOf { it.fileSize.coerceAtLeast(0) }
        if (needed <= 0 || freeBytes() - reserveBytes >= needed) return

        cache.retainOnly(items + (store.load()?.allItems() ?: emptyList()))
        val free = freeBytes() - reserveBytes
        if (free < needed) throw InsufficientSpaceException(needed, free.coerceAtLeast(0))
    }

    /** Esquece tudo (dispositivo removido ou desativado no painel). */
    fun reset() {
        store.clear()
        cache.retainOnly(emptyList())
        prefetchRetryAt = 0L
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val PLAN_REFRESH_BELOW_MS = 5 * DAY_MS
        const val PLAN_REFRESH_EVERY_MS = 60L * 60 * 1000
        const val PREFETCH_RETRY_MS = 5L * 60 * 1000
    }

}
