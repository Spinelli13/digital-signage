package com.digitalsignage.player

import android.app.Application
import com.digitalsignage.player.data.Settings

/**
 * Registra a causa de quedas inesperadas. O processo morre logo depois, então só dá para gravar o motivo
 * (de forma síncrona) e mostrá-lo na tela quando o Player for aberto de novo.
 */
class PlayerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                Settings(this).lastCrash = "${error.javaClass.simpleName}${error.message?.let { ": $it" } ?: ""}".take(300)
            } catch (ignored: Throwable) {
                // não pode falhar de novo dentro do tratador de falhas
            }
            previous?.uncaughtException(thread, error)
        }
    }

}
