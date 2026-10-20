package com.digitalsignage.player.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Instala o APK pelo PackageInstaller do próprio Android, sem Google Play.
 *
 * A assinatura é conferida pelo sistema: um APK assinado por outra chave é recusado na instalação, então
 * ninguém consegue trocar o Player por outro app mesmo que consiga publicar um arquivo no site de downloads.
 */
class ApkInstaller(private val context: Context) {

    /** Em Android 8+ o usuário precisa liberar "instalar apps desconhecidos" para o Player (uma vez). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Confere que o arquivo é mesmo uma versão deste app, com o versionCode esperado. */
    @Suppress("DEPRECATION")
    fun isExpectedUpgrade(file: File, expectedVersionCode: Long): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return info.packageName == context.packageName && code == expectedVersionCode
    }

    /** Cancela instalações que ficaram esperando confirmação (some a tela de confirmação, se estiver aberta). */
    fun abandonPending() {
        val installer = context.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
    }

    fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        // Sessões antigas abandonadas (ex.: confirmação nunca respondida) não devem se acumular.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(file.length())
            // Android 12+: se o Player já for o instalador deste app, a atualização pode ser silenciosa.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("player.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }

            val intent = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
            // O instalador precisa preencher os extras do resultado: o PendingIntent tem de ser mutável.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

}
