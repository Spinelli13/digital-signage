package com.digitalsignage.player

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.ui.PlayerView
import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigZone
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.ScreenOrientation
import com.digitalsignage.player.data.ZoneGeometry
import com.digitalsignage.player.memory.MemoryProfile
import com.digitalsignage.player.memory.TrimLevels
import com.digitalsignage.player.proof.PlayContext
import com.digitalsignage.player.proof.PlayRecorder
import com.digitalsignage.player.widget.WidgetHost
import com.digitalsignage.player.telemetry.Telemetry

/**
 * Exibe a configuração da TV. Sem layout (ou com uma zona só, em tela cheia) usa as views da própria tela, como
 * sempre; com layout monta uma área por zona e toca a lista de cada uma ao mesmo tempo.
 */
class Slideshow(
    private val scope: LifecycleCoroutineScope,
    private val cache: MediaCache,
    private val imageView: ImageView,
    private val playerView: PlayerView,
    /** Onde ficam as zonas de um layout (e o WebView da tela cheia). */
    private val zonesRoot: FrameLayout,
    /** Avisa o usuário (por 10 s na tela) quando uma mídia não pode ser exibida. */
    private val onError: (String) -> Unit = {},
    /** Conta ao servidor o que está na tela e quantas vezes cada mídia foi exibida. */
    private val telemetry: Telemetry = Telemetry.shared,
    /** Quanto o aparelho aguenta de memória (imagens menores, um vídeo por vez, etc.). */
    private val profile: MemoryProfile = MemoryProfile.NORMAL,
    /** Cada exibição medida em milissegundos vai para o diário (comprovação de exibição). */
    private val recorder: PlayRecorder = PlayRecorder.NONE,
    /** Desenha os widgets (relógio, clima, notícias, cotações, aviso, contagem regressiva). */
    private val widgets: WidgetHost? = null,
) {

    /** A zona única, que usa as views da tela (mantida entre as trocas de playlist: o ExoPlayer é reaproveitado). */
    private var single: ZonePlayer? = null
    private var singleViews: ZoneViews? = null
    private var zones: List<ZonePlayer> = emptyList()
    private var layoutListener: View.OnLayoutChangeListener? = null

    fun start(config: PlayerConfig) {
        stop()
        val context = PlayContext(config.deviceId, config.playlistId, config.campaignId, config.campaignName)
        // TV em pé (instalada assim, ou com a tela mais alta que larga) usa a versão do layout para TV em pé, se o layout tiver uma.
        val displayPortrait = isDisplayPortrait()
        val layout = config.zonesFor(ScreenOrientation.contentPortrait(config.orientation, displayPortrait))
        // Com o conteúdo girado o vídeo precisa ser TextureView (o SurfaceView da tela não gira): vai pelo caminho das zonas.
        val rotated = ScreenOrientation.rotationDegrees(config.orientation, displayPortrait) != 0
        if (layout.size == 1 && layout[0].isFullScreen && !rotated) {
            startSingle(layout[0], context)
        } else {
            startZones(layout, context, texture = rotated)
        }
    }

    private fun isDisplayPortrait(): Boolean {
        val metrics = zonesRoot.resources.displayMetrics
        return metrics.heightPixels > metrics.widthPixels
    }

    fun stop() {
        layoutListener?.let { zonesRoot.removeOnLayoutChangeListener(it) }
        layoutListener = null
        single?.stop()
        zones.forEach { it.release() }
        zones = emptyList()
        zonesRoot.removeAllViews()
        zonesRoot.visibility = View.GONE
        imageView.visibility = View.INVISIBLE
        playerView.visibility = View.INVISIBLE
    }

    /** O Android avisou que falta memória: cada zona solta o que não está à vista. */
    fun trimMemory(level: Int) {
        if (!TrimLevels.shouldRelease(level)) return
        single?.trim()
        zones.forEach { it.trim() }
    }

    fun release() {
        stop()
        single?.release()
        single = null
        singleViews = null
    }

    private fun startSingle(zone: ConfigZone, context: PlayContext) {
        val views = singleViews ?: ZoneViews(imageView, playerView, zonesRoot).also { singleViews = it }
        // O WebView da zona única fica dentro do zonesRoot (que é transparente e só aparece quando ele aparece).
        zonesRoot.visibility = View.VISIBLE
        views.applyFit(zone.covers)
        val player = single ?: ZonePlayer(scope, cache, views, telemetry, onError = onError, profile = profile, recorder = recorder, zoneName = zone.name.ifBlank { "Principal" }, widgets = widgets).also { single = it }
        player.start(zone.items, context)
    }

    private fun startZones(layout: List<ConfigZone>, context: PlayContext, texture: Boolean = false) {
        zonesRoot.visibility = View.VISIBLE
        val containers = layout.map { zone ->
            val container = FrameLayout(zonesRoot.context).apply { setBackgroundColor(Color.BLACK) }
            val image = ImageView(zonesRoot.context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
                visibility = View.INVISIBLE
            }
            val video = if (texture) {
                LayoutInflater.from(zonesRoot.context).inflate(R.layout.player_view_texture, null, false) as PlayerView
            } else {
                PlayerView(zonesRoot.context).apply {
                    useController = false
                    setShutterBackgroundColor(Color.BLACK)
                    visibility = View.INVISIBLE
                }
            }
            container.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            container.addView(video, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            zonesRoot.addView(container, FrameLayout.LayoutParams(1, 1))
            Triple(zone, container, ZoneViews(image, video, container).also { it.applyFit(zone.covers) })
        }

        // As posições dependem do tamanho da tela. As zonas só começam a tocar DEPOIS de dimensionadas: uma página da web
        // aberta num quadro de 1 pixel guarda a escala errada e fica ampliada mesmo depois de a zona crescer.
        val multi = layout.size > 1
        fun begin() {
            containers.forEach { (zone, container, views) ->
                val rect = ZoneGeometry.rect(zone, zonesRoot.width, zonesRoot.height)
                container.layoutParams = FrameLayout.LayoutParams(rect.width, rect.height).apply {
                    leftMargin = rect.left
                    topMargin = rect.top
                }
                views.width = rect.width
                views.height = rect.height
            }
            zones = containers.map { (zone, _, views) ->
                ZonePlayer(
                    scope, cache, views, telemetry,
                    reportsCurrentMedia = zone.main,
                    muted = !zone.main,
                    label = if (multi) zone.name else null,
                    onError = onError,
                    profile = profile,
                    allowVideo = zone.main || profile.secondaryVideo,
                    recorder = recorder,
                    zoneName = zone.name,
                    widgets = widgets,
                ).also { it.start(zone.items, context) }
            }
        }
        if (zonesRoot.width > 0 && zonesRoot.height > 0) {
            begin()
        } else {
            val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (zonesRoot.width > 0 && zonesRoot.height > 0) {
                    layoutListener?.let { zonesRoot.removeOnLayoutChangeListener(it) }
                    layoutListener = null
                    begin()
                }
            }
            layoutListener = listener
            zonesRoot.addOnLayoutChangeListener(listener)
        }
    }

}
