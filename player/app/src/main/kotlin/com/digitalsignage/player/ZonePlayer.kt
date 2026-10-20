package com.digitalsignage.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.telemetry.Telemetry
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Toca a lista de UMA zona em loop: imagens pela duração configurada, vídeos até o fim e páginas da web pelo tempo
 * escolhido. Item corrompido ou ausente é pulado; se nada puder ser exibido, espera e tenta de novo.
 */
class ZonePlayer(
    private val scope: LifecycleCoroutineScope,
    private val cache: MediaCache,
    private val views: ZoneViews,
    private val telemetry: Telemetry,
    /** Só a zona principal diz ao servidor "o que está na tela" (a TV tem uma mídia atual só). */
    private val reportsCurrentMedia: Boolean = true,
    /** Zonas secundárias não tocam áudio (dois sons ao mesmo tempo seriam um caos). */
    private val muted: Boolean = false,
    /** Nome da zona, para as mensagens de erro quando há várias na tela. */
    private val label: String? = null,
    /** Avisa o usuário (por 10 s na tela) quando uma mídia não pode ser exibida. */
    private val onError: (String) -> Unit = {},
) {

    private var job: Job? = null
    private var player: ExoPlayer? = null
    private var shownImage: File? = null
    private var lastFailure: String? = null

    fun start(items: List<ConfigItem>) {
        stop()
        // Exceção inesperada na reprodução: avisa na tela em vez de matar o app.
        val handler = kotlinx.coroutines.CoroutineExceptionHandler { _, e -> onError(prefixed(ErrorMessages.describe(e))) }
        job = scope.launch(handler) { loop(items) }
    }

    fun stop() {
        job?.cancel()
        job = null
        player?.stop()
        shownImage = null
        if (reportsCurrentMedia) telemetry.mediaShown(null)
        views.hideAll()
        views.releaseWeb()
    }

    fun release() {
        stop()
        player?.release()
        player = null
    }

    private fun prefixed(message: String) = if (label == null) message else "Zona \"$label\": $message"

    private suspend fun loop(items: List<ConfigItem>) {
        val playable = items.filter { cache.isCached(it) }
        if (playable.isEmpty()) return

        var index = 0
        var consecutiveFailures = 0
        while (true) {
            val item = playable[index % playable.size]
            lastFailure = null
            val shown = try {
                when {
                    item.isWeb -> showWeb(item)
                    item.isVideo -> playVideo(item, loop = playable.size == 1)
                    else -> showImage(item)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = ErrorMessages.describe(e)
                false
            }

            if (shown) {
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
                onError(prefixed("Não foi possível exibir \"${item.name}\"${lastFailure?.let { " ($it)" } ?: ""}. Passando para o próximo item."))
                delay(FAILURE_PAUSE_MS)
                // Todos os itens falharam seguidos: evita um laço apertado, espera um pouco mais.
                if (consecutiveFailures >= playable.size) delay(ALL_FAILED_PAUSE_MS)
            }
            index++
        }
    }

    private suspend fun showImage(item: ConfigItem): Boolean {
        val file = cache.fileFor(item)
        if (shownImage != file) {
            val metrics = views.imageView.resources.displayMetrics
            val maxWidth = views.width.takeIf { it > 0 } ?: metrics.widthPixels
            val maxHeight = views.height.takeIf { it > 0 } ?: metrics.heightPixels
            val bitmap = withContext(Dispatchers.IO) { decodeScaled(file, maxWidth, maxHeight) }
            if (bitmap == null) {
                lastFailure = "imagem inválida, corrompida ou grande demais"
                return false
            }

            views.imageView.setImageBitmap(bitmap)
            shownImage = file
        }
        player?.stop()
        views.showImage()
        if (reportsCurrentMedia) telemetry.mediaShown(item.mediaId)

        val seconds = item.durationSeconds.coerceAtLeast(1)
        delay(seconds * 1000L)
        // Só conta a exibição que foi até o fim (parar no meio cancela a corrotina antes desta linha).
        telemetry.played(item.mediaId, seconds.toLong())
        return true
    }

    private suspend fun playVideo(item: ConfigItem, loop: Boolean): Boolean {
        val exo = player ?: ExoPlayer.Builder(views.playerView.context).build().also {
            player = it
            views.playerView.player = it
        }
        exo.volume = if (muted) 0f else 1f
        val done = CompletableDeferred<Boolean>()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) done.complete(true)
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // Vídeo único em repetição nunca "termina": cada volta ao início é uma exibição completa.
                if (loop && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    telemetry.played(item.mediaId, oldPosition.positionMs / 1000)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                lastFailure = "falha ao reproduzir o vídeo: ${error.errorCodeName}"
                done.complete(false)
            }
        }

        exo.addListener(listener)
        try {
            exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(cache.fileFor(item))))
            exo.prepare()
            exo.playWhenReady = true

            shownImage = null
            views.showVideo()
            if (reportsCurrentMedia) telemetry.mediaShown(item.mediaId)
            val startedAt = SystemClock.elapsedRealtime()

            // Um vídeo único repete sozinho (REPEAT_MODE_ONE) e só termina por erro ou cancelamento.
            val timeoutMs = (item.durationSeconds.coerceAtLeast(1) + VIDEO_GRACE_SECONDS) * 1000L
            return if (loop) {
                done.await()
            } else {
                val finished = withTimeoutOrNull(timeoutMs) { done.await() }
                if (finished == null) lastFailure = "o vídeo não terminou no tempo esperado"
                if (finished == true) {
                    telemetry.played(item.mediaId, (SystemClock.elapsedRealtime() - startedAt) / 1000)
                }
                finished == true
            }
        } finally {
            exo.removeListener(listener)
        }
    }

    /** Abre a página e a exibe pelo tempo escolhido. Sem internet, ou se a página falhar, o item é pulado. */
    @Suppress("DEPRECATION")
    private suspend fun showWeb(item: ConfigItem): Boolean {
        val web = views.web(views.playerView.context)
        val loaded = CompletableDeferred<Boolean>()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // TV não tem ponteiro: sem isto o primeiro link da página aparece com o anel de foco do controle remoto.
                view.clearFocus()
                view.evaluateJavascript(HIDE_FOCUS_RING, null)
                loaded.complete(true)
            }

            // Chamado para erros da página principal em qualquer versão do Android (a partir do 6 o método novo repassa para este).
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                lastFailure = "não foi possível abrir a página${description?.let { " ($it)" } ?: ""}"
                loaded.complete(false)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    lastFailure = "a página respondeu com erro ${errorResponse.statusCode}"
                    loaded.complete(false)
                }
            }
        }

        views.hideAll()
        player?.stop()
        shownImage = null
        web.loadUrl(item.url)
        val opened = withTimeoutOrNull(WEB_LOAD_TIMEOUT_MS) { loaded.await() }
        if (opened != true) {
            if (opened == null) lastFailure = "a página demorou demais para abrir"
            web.stopLoading()
            web.loadUrl("about:blank")
            return false
        }

        views.showWeb()
        if (reportsCurrentMedia) telemetry.mediaShown(item.mediaId)
        val seconds = item.durationSeconds.coerceAtLeast(1)
        delay(seconds * 1000L)
        telemetry.played(item.mediaId, seconds.toLong())
        return true
    }

    // Decodifica já reduzido: uma foto 4K inteira em memória estoura a RAM de TV boxes baratas.
    private fun decodeScaled(file: File, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val FAILURE_PAUSE_MS = 1_000L
        const val ALL_FAILED_PAUSE_MS = 5_000L
        const val VIDEO_GRACE_SECONDS = 15
        const val WEB_LOAD_TIMEOUT_MS = 30_000L

        /** Tira o anel de foco que o navegador desenha no primeiro link (ninguém aponta nada numa tela de sinalização). */
        const val HIDE_FOCUS_RING =
            "(function(){var s=document.createElement('style');s.textContent='*:focus{outline:none!important}';" +
                "document.head.appendChild(s);if(document.activeElement&&document.activeElement.blur){document.activeElement.blur();}})()"
    }

}
