package com.digitalsignage.player.diagnostics

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.digitalsignage.player.BootReceiver
import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.sync.ConfigStore

/** Colhe, no aparelho, o que o relatório de diagnóstico precisa. Só lê: nunca altera nada. */
class DiagnosticsCollector(
    private val context: Context,
    private val versionName: String,
    private val serverUrl: () -> String,
    private val cache: MediaCache,
    private val store: ConfigStore,
    /** Há quantos segundos o servidor respondeu pela última vez (nulo = nunca nesta abertura). */
    private val contactAgoSec: () -> Long?,
    /** Quando a configuração foi sincronizada pela última vez, em milissegundos (0 = ainda não). */
    private val lastSyncAtMs: () -> Long,
    private val nowMs: () -> Long,
) {

    fun collect(): DiagnosticsInput {
        val config = store.load()
        val missing = config?.retainable()?.filter { it.hasFile && !cache.isCached(it) }.orEmpty()
        return DiagnosticsInput(
            version = versionName,
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidRelease = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            ramMb = totalRamMb(),
            serverUrl = serverUrl(),
            internet = hasInternet(),
            contactAgoSec = contactAgoSec(),
            freeMb = context.filesDir.usableSpace / MB,
            cacheMb = context.filesDir.resolve("media").walkTopDown().filter { it.isFile }.sumOf { it.length() } / MB,
            lastSyncAtMs = lastSyncAtMs(),
            nowMs = nowMs(),
            missingFiles = missing.size,
            missingMb = missing.sumOf { it.fileSize.coerceAtLeast(0) } / MB,
            bootOk = bootOk(),
            overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
            installOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls(),
        )
    }

    private fun hasInternet(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun totalRamMb(): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(info)
        return info.totalMem / MB
    }

    /** A permissão de iniciar com a TV é automática; o que pode faltar é o receptor estar desligado. */
    private fun bootOk(): Boolean {
        val granted = context.checkSelfPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED) == PackageManager.PERMISSION_GRANTED
        val receiver = context.packageManager.getComponentEnabledSetting(ComponentName(context, BootReceiver::class.java))
        return granted && receiver != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}
