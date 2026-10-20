package com.digitalsignage.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigItem
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.errors.ErrorMessages
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Toca a playlist em loop: imagens pela duração configurada, vídeos até o fim.
 * Item corrompido ou ausente é pulado; se nada puder ser exibido, espera e tenta de novo.
 */
class Slideshow(
    private val scope: LifecycleCoroutineScope,
    private val cache: MediaCache,
    private val imageView: ImageView,
    private val playerView: PlayerView,
    /** Avisa o usuário (por 10 s na tela) quando uma mídia não pode ser exibida. */
    private val onError: (String) -> Unit = {},
) {

    private var job: Job? = null
    private var player: ExoPlayer? = null
    private var shownImage: File? = null
    private var lastFailure: String? = null

    fun start(config: PlayerConfig) {
        stop()
        // Exceção inesperada na reprodução: avisa na tela em vez de matar o app.
        val handler = kotlinx.coroutines.CoroutineExceptionHandler { _, e -> onError(ErrorMessages.describe(e)) }
        job = scope.launch(handler) { loop(config.items) }
    }

    fun stop() {
        job?.cancel()
        job = null
        player?.stop()
        shownImage = null
        imageView.visibility = View.INVISIBLE
        playerView.visibility = View.INVISIBLE
    }

    fun release() {
        stop()
        player?.release()
        player = null
    }

    private suspend fun loop(items: List<ConfigItem>) {
        val playable = items.filter { cache.isCached(it) }
        if (playable.isEmpty()) return

        var index = 0
        var consecutiveFailures = 0
        while (true) {
            val item = playable[index % playable.size]
            lastFailure = null
            val shown = try {
                if (item.isVideo) playVideo(item, loop = playable.size == 1) else showImage(item)
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
                onError("Não foi possível exibir \"${item.name}\"${lastFailure?.let { " ($it)" } ?: ""}. Passando para o próximo item.")
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
            val metrics = imageView.resources.displayMetrics
            val bitmap = withContext(Dispatchers.IO) {
                decodeScaled(file, metrics.widthPixels, metrics.heightPixels)
            }
            if (bitmap == null) {
                lastFailure = "imagem inválida, corrompida ou grande demais"
                return false
            }

            imageView.setImageBitmap(bitmap)
            shownImage = file
        }
        player?.stop()
        playerView.visibility = View.INVISIBLE
        imageView.visibility = View.VISIBLE

        delay(item.durationSeconds.coerceAtLeast(1) * 1000L)
        return true
    }

    private suspend fun playVideo(item: ConfigItem, loop: Boolean): Boolean {
        val exo = player ?: ExoPlayer.Builder(playerView.context).build().also {
            player = it
            playerView.player = it
        }
        val done = CompletableDeferred<Boolean>()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) done.complete(true)
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
            imageView.visibility = View.INVISIBLE
            playerView.visibility = View.VISIBLE

            // Um vídeo único repete sozinho (REPEAT_MODE_ONE) e só termina por erro ou cancelamento.
            val timeoutMs = (item.durationSeconds.coerceAtLeast(1) + VIDEO_GRACE_SECONDS) * 1000L
            return if (loop) {
                done.await()
            } else {
                withTimeoutOrNull(timeoutMs) { done.await() }
                    ?: run {
                        lastFailure = "o vídeo não terminou no tempo esperado"
                        false
                    }
            }
        } finally {
            exo.removeListener(listener)
        }
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
    }

}
