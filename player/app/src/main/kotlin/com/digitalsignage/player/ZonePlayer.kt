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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.memory.MemoryProfile
import com.digitalsignage.player.proof.PlayContext
import com.digitalsignage.player.proof.PlayInfo
import com.digitalsignage.player.proof.PlayRecorder
import com.digitalsignage.player.telemetry.Telemetry
import com.digitalsignage.player.widget.WidgetHost
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
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
    /** Quanto o aparelho aguenta (ver MemoryProfile): imagens menores, menos coisas abertas ao mesmo tempo. */
    private val profile: MemoryProfile = MemoryProfile.NORMAL,
    /** Falso nas zonas secundárias de aparelhos com pouca memória: vídeos dessa zona são pulados. */
    private val allowVideo: Boolean = true,
    /** Recebe cada exibição, medida em milissegundos (comprovação de exibição). */
    private val recorder: PlayRecorder = PlayRecorder.NONE,
    /** O nome da zona nos registros de exibição (a zona única da tela é a "Principal"). */
    private val zoneName: String = "Principal",
    /** Desenha os widgets (relógio, clima, notícias...); sem ele a zona pula esse tipo de item. */
    private val widgets: WidgetHost? = null,
) {

    private var job: Job? = null
    private var warnedSkippedVideo = false
    private var context = PlayContext.NONE
    private var player: ExoPlayer? = null
    private var shownImage: File? = null
    private var lastFailure: String? = null

    fun start(items: List<ConfigItem>, context: PlayContext = PlayContext.NONE) {
        stop()
        this.context = context
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

    /**
     * O Android avisou que falta memória: solta o que não está à vista (o decodificador de vídeo parado, a página da web
     * fechada). O que está na tela continua; a próxima mídia que precisar recria o que faltar.
     */
    fun trim(current: ConfigItem? = shownItem) {
        if (current?.isVideo != true) {
            player?.release()
            player = null
        }
        if (current?.usesWebView != true) views.releaseWeb()
    }

    @Volatile
    private var shownItem: ConfigItem? = null

    private suspend fun loop(items: List<ConfigItem>) {
        val playable = items.filter { cache.isCached(it) && (allowVideo || !it.isVideo) }
        if (!allowVideo && !warnedSkippedVideo && items.any { it.isVideo }) {
            warnedSkippedVideo = true
            onError(prefixed("Vídeos ignorados nesta zona: o aparelho tem pouca memória e só toca um vídeo por vez (na zona principal)."))
        }
        if (playable.isEmpty()) return

        var index = 0
        var consecutiveFailures = 0
        while (true) {
            val item = playable[index % playable.size]
            shownItem = item
            lastFailure = null
            val shown = try {
                when {
                    item.isWidget -> showWidget(item)
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
            if (profile.releaseAfterUse) releaseUnusedBefore(playable[index % playable.size])
        }
    }

    /** Em aparelhos com pouca memória, solta o decodificador/WebView que o próximo item não vai usar. */
    private fun releaseUnusedBefore(next: ConfigItem) {
        if (!next.isVideo) {
            player?.release()
            player = null
        }
        if (!next.usesWebView) views.releaseWeb()
    }

    private suspend fun showImage(item: ConfigItem): Boolean {
        val file = cache.fileFor(item)
        if (shownImage != file) {
            val metrics = views.imageView.resources.displayMetrics
            var maxWidth = views.width.takeIf { it > 0 } ?: metrics.widthPixels
            var maxHeight = views.height.takeIf { it > 0 } ?: metrics.heightPixels
            if (profile.maxImageWidth > 0) maxWidth = minOf(maxWidth, profile.maxImageWidth)
            if (profile.maxImageHeight > 0) maxHeight = minOf(maxHeight, profile.maxImageHeight)
            val bitmap = withContext(Dispatchers.IO) { decodeScaled(file, maxWidth, maxHeight) }
            if (bitmap == null) {
                lastFailure = "imagem inválida, corrompida ou grande demais"
                return false
            }

            val previous = (views.imageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            views.imageView.setImageBitmap(bitmap)
            // A imagem anterior já saiu da tela: devolve a memória dela na hora (em vez de esperar o coletor de lixo).
            if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
            shownImage = file
        }
        player?.stop()
        views.showImage()
        if (reportsCurrentMedia) telemetry.mediaShown(item.mediaId)

        val seconds = item.durationSeconds.coerceAtLeast(1)
        val watch = Stopwatch()
        var finished = false
        try {
            delay(seconds * 1000L)
            finished = true
        } finally {
            // Até a exibição interrompida (troca de playlist, tela fechada) fica registrada, com o tempo que de fato durou.
            report(item, watch, finished)
        }
        // Só conta a exibição que foi até o fim (parar no meio cancela a corrotina antes desta linha).
        telemetry.played(item.mediaId, seconds.toLong())
        return true
    }

    private suspend fun playVideo(item: ConfigItem, loop: Boolean): Boolean {
        val exo = player ?: buildPlayer().also {
            player = it
            views.playerView.player = it
        }
        exo.volume = if (muted) 0f else 1f
        val done = CompletableDeferred<Boolean>()
        // A exibição começa quando o primeiro quadro aparece de fato (não quando mandamos tocar).
        var watch: Stopwatch? = null
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (watch == null) watch = Stopwatch()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) done.complete(true)
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // Vídeo único em repetição nunca "termina": cada volta ao início é uma exibição completa.
                if (loop && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    telemetry.played(item.mediaId, oldPosition.positionMs / 1000)
                    watch?.let { report(item, it, true) }
                    watch = Stopwatch()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                lastFailure = ErrorMessages.playbackFailure(error.errorCode, error.errorCodeName)
                done.complete(false)
            }
        }

        exo.addListener(listener)
        var completedVideo = false
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
                completedVideo = finished == true
                finished == true
            }
        } finally {
            exo.removeListener(listener)
            // Se o primeiro quadro chegou a aparecer, a exibição (inteira ou interrompida) fica registrada.
            watch?.let { report(item, it, completedVideo) }
        }
    }

    /** Uma medição de exibição: o relógio de parede marca o INÍCIO (para o relatório) e o relógio monotônico mede a DURAÇÃO. */
    private class Stopwatch {
        val wallMs: Long = System.currentTimeMillis()
        private val startedMono: Long = SystemClock.elapsedRealtime()
        fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedMono
    }

    private fun report(item: ConfigItem, watch: Stopwatch, completed: Boolean) {
        recorder.record(
            PlayInfo(
                deviceId = context.deviceId,
                mediaId = item.mediaId,
                mediaName = item.name,
                mediaType = item.type,
                playlistId = context.playlistId,
                campaignId = context.campaignId,
                campaignName = context.campaignName,
                zoneName = zoneName,
                startedAtMs = watch.wallMs,
                durationMs = watch.elapsedMs(),
                plannedMs = item.durationSeconds.coerceAtLeast(1) * 1000L,
                completed = completed,
            ),
        )
    }

    /** Abre a página e a exibe pelo tempo escolhido. Sem internet, ou se a página falhar, o item é pulado. */
    @Suppress("DEPRECATION")
    /**
     * Widget: a página é montada aqui mesmo (modelo + escolhas + últimos dados + hora de verdade) e não acessa a internet.
     * Qualquer pedido de rede feito pela página é bloqueado: o que o widget precisa já veio dentro dela.
     */
    private suspend fun showWidget(item: ConfigItem): Boolean {
        val host = widgets ?: throw IOException("este Player não sabe desenhar widgets")
        val web = views.web(views.playerView.context)
        val loaded = CompletableDeferred<Boolean>()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                loaded.complete(true)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val scheme = request.url.scheme
                return if (scheme == "http" || scheme == "https") WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                lastFailure = "não foi possível desenhar o widget${description?.let { " ($it)" } ?: ""}"
                loaded.complete(false)
            }
        }

        views.hideAll()
        player?.stop()
        shownImage = null
        web.loadDataWithBaseURL(WIDGET_BASE_URL, host.html(item), "text/html", "UTF-8", null)
        val opened = withTimeoutOrNull(WEB_LOAD_TIMEOUT_MS) { loaded.await() }
        if (opened != true) {
            if (opened == null) lastFailure = "o widget demorou demais para abrir"
            web.stopLoading()
            web.loadUrl("about:blank")
            return false
        }

        views.showWeb()
        if (reportsCurrentMedia) telemetry.mediaShown(item.mediaId)
        val seconds = item.durationSeconds.coerceAtLeast(1)
        val watch = Stopwatch()
        var finished = false
        try {
            delay(seconds * 1000L)
            finished = true
        } finally {
            report(item, watch, finished)
        }
        telemetry.played(item.mediaId, seconds.toLong())
        return true
    }

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
        val watch = Stopwatch()
        var finished = false
        try {
            delay(seconds * 1000L)
            finished = true
        } finally {
            report(item, watch, finished)
        }
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
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (profile.rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        })
    }

    /**
     * ExoPlayer pronto para tocar arquivos do cache: buffer pequeno (o arquivo já está no disco) e, se o decodificador
     * de hardware preferido falhar, tenta os outros (inclusive o de software) antes de desistir do vídeo.
     */
    private fun buildPlayer(): ExoPlayer {
        val context = views.playerView.context
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(profile.bufferMinMs, profile.bufferMaxMs, BUFFER_FOR_PLAYBACK_MS, BUFFER_AFTER_REBUFFER_MS)
            .setTargetBufferBytes(profile.bufferBytes)
            .build()
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context, renderers).setLoadControl(loadControl).build()
    }

    private companion object {
        const val FAILURE_PAUSE_MS = 1_000L
        const val ALL_FAILED_PAUSE_MS = 5_000L
        const val VIDEO_GRACE_SECONDS = 15
        const val WEB_LOAD_TIMEOUT_MS = 30_000L

        /** A "origem" da página de um widget (nunca é acessada: a página vem pronta, e o que ela pedir à rede é bloqueado). */
        const val WIDGET_BASE_URL = "https://widget.smartplay.invalid/"
        const val BUFFER_FOR_PLAYBACK_MS = 500
        const val BUFFER_AFTER_REBUFFER_MS = 1_000

        /** Tira o anel de foco que o navegador desenha no primeiro link (ninguém aponta nada numa tela de sinalização). */
        const val HIDE_FOCUS_RING =
            "(function(){var s=document.createElement('style');s.textContent='*:focus{outline:none!important}';" +
                "document.head.appendChild(s);if(document.activeElement&&document.activeElement.blur){document.activeElement.blur();}})()"
    }

}
