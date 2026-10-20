package com.digitalsignage.player.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.digitalsignage.player.data.Settings
import com.digitalsignage.player.errors.ErrorReporter

/** Recebe o resultado do instalador: abre a tela de confirmação do Android quando ela é exigida. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // O Android exige que alguém confirme (aperte OK no controle). A tela aparece sobre o Player.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                settings.lastUpdateStatus = "Aguardando confirmação na TV (aperte OK no controle)"
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> settings.lastUpdateStatus = "Atualização instalada"

            // Cancelada pelo próprio Player (ninguém confirmou a tempo) ou por quem estava na TV.
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                settings.lastUpdateStatus = "Atualização não concluída (sem confirmação); tentarei de novo mais tarde"

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                settings.lastUpdateStatus = "Falha ao instalar (código $status): $message"
                ErrorReporter.shared.report("Não foi possível instalar a atualização do Player (código $status). $message", 0)
            }
        }
    }

    companion object {
        const val ACTION = "com.digitalsignage.player.INSTALL_RESULT"
    }

}
