package com.digitalsignage.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * As telas de uma zona: uma imagem, um vídeo e (só quando a zona tem uma página da web) um WebView. Só uma delas
 * fica visível por vez. Sem layout a zona única usa as views da própria tela; com layout cada zona tem as suas.
 */
class ZoneViews(
    val imageView: ImageView,
    val playerView: PlayerView,
    /** Onde o WebView é colocado quando a zona precisa dele. */
    private val webContainer: ViewGroup,
) {

    /** Tamanho da zona em pixels, para decodificar imagens já no tamanho certo (0 = o da tela). */
    var width = 0
    var height = 0

    var webView: WebView? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun web(context: Context): WebView =
        webView ?: WebView(context).also { web ->
            web.setBackgroundColor(Color.BLACK)
            web.isFocusable = false
            web.isFocusableInTouchMode = false
            web.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            web.isHorizontalScrollBarEnabled = false
            web.isVerticalScrollBarEnabled = false
            web.settings.apply {
                // É a página do próprio administrador (painéis, cardápios, previsão do tempo): precisa de JavaScript.
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                // Nada de ler arquivos do aparelho a partir da página.
                allowFileAccess = false
                allowContentAccess = false
                setSupportZoom(false)
                builtInZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            web.visibility = View.INVISIBLE
            webContainer.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            webView = web
        }

    /** Como a mídia ocupa a zona: cabe inteira (faixas pretas se a proporção for outra) ou preenche cortando as bordas. */
    fun applyFit(covers: Boolean) {
        imageView.scaleType = if (covers) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        playerView.resizeMode = if (covers) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    fun showImage() {
        playerView.visibility = View.INVISIBLE
        hideWeb()
        imageView.visibility = View.VISIBLE
    }

    fun showVideo() {
        imageView.visibility = View.INVISIBLE
        hideWeb()
        playerView.visibility = View.VISIBLE
    }

    fun showWeb() {
        imageView.visibility = View.INVISIBLE
        playerView.visibility = View.INVISIBLE
        webView?.visibility = View.VISIBLE
    }

    fun hideWeb() {
        webView?.let {
            it.visibility = View.INVISIBLE
        }
    }

    fun hideAll() {
        imageView.visibility = View.INVISIBLE
        playerView.visibility = View.INVISIBLE
        hideWeb()
    }

    /** Descarta o WebView (a página para de carregar e a memória volta). Um novo é criado se precisar. */
    fun releaseWeb() {
        webView?.let { web ->
            web.stopLoading()
            web.loadUrl("about:blank")
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        webView = null
    }

}
