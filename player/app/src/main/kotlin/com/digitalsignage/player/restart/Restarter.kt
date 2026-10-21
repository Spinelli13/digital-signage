package com.digitalsignage.player.restart

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.digitalsignage.player.LaunchHelper
import com.digitalsignage.player.data.Settings

/**
 * Reabre o Player sozinho (reabrir após queda e o "vigia"). Os alarmes são do AlarmManager, que sobrevive à morte do
 * processo; quem abre a tela é o [LaunchHelper] (o Android 10+ só deixa abrir telas do segundo plano com o truque do
 * overlay invisível, ver lá). O vigia reinicia junto com o Player: toda abertura o agenda de novo.
 */
object Restarter {

    const val ACTION_CRASH = "com.digitalsignage.player.RESTART_AFTER_CRASH"
    const val ACTION_WATCHDOG = "com.digitalsignage.player.WATCHDOG"
    private const val REQUEST_CRASH = 7101
    private const val REQUEST_WATCHDOG = 7102

    /** Marca que a tela está viva e à vista (chamado a cada [RestartPolicy.ALIVE_EVERY_MS] enquanto ela está aberta). */
    fun stampAlive(settings: Settings, now: Long = System.currentTimeMillis()) {
        settings.lastAliveAt = now
    }

    /** A pessoa saiu de propósito (Voltar/Início): o vigia espera um tempo antes de trazer o Player de volta. */
    fun userLeft(settings: Settings, now: Long = System.currentTimeMillis()) {
        settings.userExitUntil = now + RestartPolicy.USER_EXIT_GRACE_MS
    }

    /** A tela voltou a ser aberta: vale de novo o sinal de vida e a saída de propósito é esquecida. */
    fun opened(settings: Settings, now: Long = System.currentTimeMillis()) {
        settings.userExitUntil = 0
        settings.pendingCrashRestart = false
        stampAlive(settings, now)
    }

    /**
     * O app caiu: grava o motivo e agenda a reabertura (chamado do tratador de falhas, que não pode falhar de novo).
     * Devolve a espera escolhida, em ms.
     */
    fun scheduleAfterCrash(context: Context, settings: Settings, reason: String, now: Long = System.currentTimeMillis()): Long {
        val crashes = RestartPolicy.recentCrashes(settings.crashTimes, now)
        settings.crashTimes = crashes
        settings.lastRestartReason = reason
        settings.pendingCrashRestart = true
        val delay = RestartPolicy.crashDelayMs(crashes.size)
        if (settings.autoRestart) {
            setAlarm(context, delay, ACTION_CRASH, REQUEST_CRASH)
        }
        return delay
    }

    /** Agenda (ou reagenda) o vigia. Seguro chamar a cada abertura: o mesmo alarme é substituído. */
    fun scheduleWatchdog(context: Context) {
        setAlarm(context, RestartPolicy.WATCHDOG_INTERVAL_MS, ACTION_WATCHDOG, REQUEST_WATCHDOG)
    }

    private fun setAlarm(context: Context, delayMs: Long, action: String, requestCode: Int) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, RestartReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getBroadcast(app, requestCode, intent, flags)
        val at = SystemClock.elapsedRealtime() + delayMs
        try {
            when {
                // Alarme exato só onde o Android permite sem pedir autorização à pessoa.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms() ->
                    alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)

                else -> alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
    }

}

/** Recebe os alarmes do [Restarter]: reabertura após queda e a conferência do vigia (que se reagenda a cada vez). */
class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)
        val now = System.currentTimeMillis()
        when (intent.action) {
            Restarter.ACTION_CRASH -> reopen(context, settings, now, force = true)
            Restarter.ACTION_WATCHDOG -> {
                // O vigia sempre volta a se agendar, mesmo que o Player esteja aberto agora.
                Restarter.scheduleWatchdog(context)
                reopen(context, settings, now, force = false)
            }
        }
    }

    private fun reopen(context: Context, settings: Settings, now: Long, force: Boolean) {
        val needed = if (force) {
            // Depois de uma queda: reabre, a não ser que a tela já tenha voltado por outro caminho (isso apaga o marcador).
            settings.autoRestart && settings.pendingCrashRestart
        } else {
            RestartPolicy.shouldRelaunch(now, settings.autoRestart, settings.lastAliveAt, settings.userExitUntil)
        }
        if (!needed) return
        if (!force) settings.lastRestartReason = "o Player não estava aberto (fechado pelo sistema ou por alguém)"
        val pending = goAsync()
        LaunchHelper.launchPlayer(context) { pending.finish() }
    }

}
