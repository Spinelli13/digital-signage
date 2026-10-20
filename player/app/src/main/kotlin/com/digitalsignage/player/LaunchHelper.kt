package com.digitalsignage.player

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * Abre telas a partir do segundo plano (boot, depois de uma atualização, ao voltar de uma instalação).
 *
 * Desde o Android 10 um app não pode abrir uma tela a partir do segundo plano. Ter a permissão "exibir sobre
 * outros apps" NÃO basta: o sistema só libera se o app tiver uma janela de overlay *visível* naquele momento
 * (medido no Android 14: o log mostra BAL_BLOCK mesmo com a permissão concedida). Por isso criamos um overlay
 * invisível de 1 px, abrimos a Activity e removemos o overlay. Sem a permissão ainda tentamos abrir, o que
 * funciona em versões antigas (por exemplo, Fire OS baseado em Android 9).
 */
object LaunchHelper {

    private const val OVERLAY_SETTLE_MS = 500L
    private const val OVERLAY_LIFETIME_MS = 4_000L

    /** Abre o Player. [onFinished] roda quando o overlay já foi removido. */
    fun launchPlayer(context: Context, onFinished: () -> Unit = {}) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val overlay = addInvisibleOverlay(appContext)

        handler.postDelayed({
            try {
                appContext.startActivity(
                    Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                // Bloqueado pelo sistema: nada mais a fazer aqui; o usuário abre o app manualmente.
            }
            handler.postDelayed({
                overlay?.let { removeOverlay(appContext, it) }
                onFinished()
            }, OVERLAY_LIFETIME_MS)
        }, OVERLAY_SETTLE_MS)
    }

    private fun addInvisibleOverlay(context: Context): View? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return null

        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                1,
                1,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            )
            View(context).also { windowManager.addView(it, params) }
        } catch (e: Exception) {
            null
        }
    }

    private fun removeOverlay(context: Context, view: View) {
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) {
            // já removido
        }
    }

}
