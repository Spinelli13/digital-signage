package com.digitalsignage.player

import android.app.Application
import com.digitalsignage.player.data.Settings
import com.digitalsignage.player.errors.ErrorReporter
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.Telemetry

/**
 * Registra a causa de quedas inesperadas. O processo morre logo depois, então só dá para gravar o motivo
 * (de forma síncrona) e mostrá-lo na tela quando o Player for aberto de novo.
 */
class PlayerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Todo erro mostrado na tela também vai para o histórico da TV no painel (no próximo heartbeat).
        ErrorReporter.shared.sink = { Telemetry.shared.event(Level.ERROR, it) }
        Telemetry.shared.event(Level.INFO, "Player iniciado (versão ${BuildConfig.VERSION_NAME})")
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
