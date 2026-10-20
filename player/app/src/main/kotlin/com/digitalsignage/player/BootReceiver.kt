package com.digitalsignage.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Abre o Player sozinho quando a TV liga e depois de uma atualização do próprio app.
 * A parte difícil (o Android bloqueia abrir telas do segundo plano) está em [LaunchHelper].
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // MY_PACKAGE_REPLACED: depois de uma atualização o Android encerra o app e não o reabre sozinho.
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!relevant) return

        val pending = goAsync()
        LaunchHelper.launchPlayer(context) { pending.finish() }
    }

}
