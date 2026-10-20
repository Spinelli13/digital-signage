package com.digitalsignage.player.update

import android.content.Context
import android.os.Build
import com.digitalsignage.player.BuildConfig
import com.digitalsignage.player.LaunchHelper
import com.digitalsignage.player.data.Settings
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.errors.ErrorReporter
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class UpdateOutcome { DISABLED, WAITING_FOR_WINDOW, NOT_ALLOWED, UP_TO_DATE, INCOMPATIBLE, INSTALLING, FAILED }

/** Espera [waitMs] pelo próximo ciclo, mas acorda na hora se chegar um pedido. Devolve true se foi um pedido. */
internal suspend fun waitForNextCheck(requests: ReceiveChannel<Unit>, waitMs: Long): Boolean =
    withTimeoutOrNull(waitMs) {
        requests.receive()
        true
    } ?: false

/**
 * Transforma o valor que vem no heartbeat em, no máximo, um pedido por pedido do painel: guarda o último tratado
 * para um reinício do Player (ou os heartbeats seguintes, de 30 em 30 s) não repetir a verificação.
 */
class UpdateRequestTrigger(
    private val lastHandled: () -> Long,
    private val remember: (Long) -> Unit,
    private val request: () -> Unit,
) {
    fun onHeartbeat(token: Long) {
        if (token <= 0 || token == lastHandled()) return
        remember(token)
        request()
    }
}

/**
 * Verifica periodicamente o latest.json do site de downloads e, havendo versão nova, baixa, confere o
 * SHA-256 e entrega ao instalador do Android. Falhas de rede nunca afetam a reprodução.
 */
class UpdateManager(
    private val context: Context,
    private val settings: Settings,
    private val checker: UpdateChecker = UpdateChecker(deviceSdk = Build.VERSION.SDK_INT),
    private val downloader: ApkDownloader = ApkDownloader(),
    private val installer: ApkInstaller = ApkInstaller(context),
    private val reporter: ErrorReporter = ErrorReporter.shared,
) {

    private val updatesDir get() = File(context.cacheDir, "updates")
    private val requests = Channel<Unit>(Channel.CONFLATED)

    /** O administrador pediu, no painel, que esta TV verifique atualização agora (ignora a janela de horário). */
    fun requestCheck() {
        requests.trySend(Unit)
    }

    suspend fun run() {
        delay(START_DELAY_MS) // deixa a TV começar a exibir antes de usar a rede para isso
        var requested = false
        while (true) {
            val outcome = checkOnce(ignoreWindow = requested)
            requested = waitForNextCheck(
                requests,
                when (outcome) {
                    UpdateOutcome.WAITING_FOR_WINDOW -> WINDOW_RETRY_MS
                    // Sem permissão também tenta de novo logo: o usuário pode liberar a qualquer momento.
                    UpdateOutcome.FAILED, UpdateOutcome.NOT_ALLOWED -> FAILURE_RETRY_MS
                    else -> CHECK_INTERVAL_MS
                },
            )
        }
    }

    suspend fun checkOnce(ignoreWindow: Boolean = false): UpdateOutcome {
        if (!settings.autoUpdate) return UpdateOutcome.DISABLED
        if (!ignoreWindow && !UpdateWindow.isOpen(settings.updateWindow, minuteOfDay())) return UpdateOutcome.WAITING_FOR_WINDOW

        return try {
            when (val result = checker.check(settings.updateUrl, BuildConfig.VERSION_CODE.toLong())) {
                UpdateCheck.UpToDate -> {
                    settings.lastUpdateStatus = null // some o aviso de falha anterior, se houver
                    UpdateOutcome.UP_TO_DATE
                }

                is UpdateCheck.Incompatible -> {
                    settings.lastUpdateStatus = "Versão ${result.release.versionName} exige Android mais novo"
                    reporter.report("Existe a versão ${result.release.versionName} do Player, mas ela exige um Android mais novo que o desta TV.", ONE_DAY_MS)
                    UpdateOutcome.INCOMPATIBLE
                }

                is UpdateCheck.Available -> install(result.release)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            settings.lastUpdateStatus = "Falha ao verificar atualização: ${e.message}"
            reporter.report(ErrorMessages.describe("Atualização do Player", e), SIX_HOURS_MS)
            UpdateOutcome.FAILED
        }
    }

    private suspend fun install(release: Release): UpdateOutcome {
        if (!installer.canInstall()) {
            // Sem a permissão não adianta baixar: o Android recusaria a instalação.
            settings.lastUpdateStatus =
                "Versão ${release.versionName} disponível, mas falta liberar \"instalar apps desconhecidos\" para o Player"
            reporter.report(
                "Há uma versão nova do Player (${release.versionName}), mas falta liberar \"instalar apps desconhecidos\" para ele. Veja o manual.",
                ONE_DAY_MS,
            )
            return UpdateOutcome.NOT_ALLOWED
        }

        cleanOldDownloads()
        val file = downloader.download(release, File(updatesDir, "player-${release.versionName}.apk"))
        if (!installer.isExpectedUpgrade(file, release.versionCode)) {
            file.delete()
            settings.lastUpdateStatus = "APK baixado não é a versão ${release.versionName} deste app: ignorado"
            reporter.report("A atualização baixada não é a versão ${release.versionName} do Player e foi descartada.", SIX_HOURS_MS)
            return UpdateOutcome.FAILED
        }

        settings.lastUpdateStatus = "Instalando versão ${release.versionName}…"
        // NonCancellable: a tela de confirmação do Android cobre o Player, que sai de "visível" e cancelaria esta
        // rotina antes do relógio de 10 minutos que traz o Player de volta.
        withContext(NonCancellable) {
            installer.install(file)
            restorePlayerIfNothingHappens()
        }
        return UpdateOutcome.INSTALLING
    }

    /**
     * Se ninguém confirmar (ou o Android recusar), a TV não pode ficar parada numa tela de instalação ou na tela
     * inicial: cancela a instalação pendente e traz o Player de volta. Se a instalação acontecer, o processo é
     * encerrado pelo sistema antes de chegar aqui e o BootReceiver reabre o Player.
     */
    private suspend fun restorePlayerIfNothingHappens() {
        delay(CONFIRMATION_TIMEOUT_MS)
        installer.abandonPending()
        settings.lastUpdateStatus = "Atualização não concluída (sem confirmação); tentarei de novo mais tarde"
        reporter.report("A atualização do Player não foi confirmada na TV e foi cancelada. Tentarei de novo mais tarde.", 0)
        LaunchHelper.launchPlayer(context)
    }

    private fun cleanOldDownloads() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    private fun minuteOfDay(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    private companion object {
        const val START_DELAY_MS = 90_000L
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
        const val WINDOW_RETRY_MS = 15 * 60 * 1000L
        const val FAILURE_RETRY_MS = 30 * 60 * 1000L
        const val SIX_HOURS_MS = 6 * 60 * 60 * 1000L
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        const val CONFIRMATION_TIMEOUT_MS = 10 * 60 * 1000L
    }

}
