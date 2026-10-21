package com.digitalsignage.player.engine

import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.PlayerStatus
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.plan.OfflinePlanner
import com.digitalsignage.player.proof.PlayLog
import com.digitalsignage.player.sync.ConfigStore
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.Telemetry
import com.digitalsignage.player.time.TrustedClock
import com.digitalsignage.player.widget.WidgetRefresher
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface EngineState {
    /** Ainda não sabemos de nada (primeira abertura, sem cache). */
    data object Starting : EngineState

    /** Sem rede e sem nada em cache para exibir. */
    data object ConnectionError : EngineState

    /** Mostrar o código na TV para o administrador digitar no painel. */
    data class WaitingActivation(val code: String) : EngineState

    data object Disabled : EngineState

    /** Ativo, mas a playlist está vazia ou ainda não foi escolhida no painel. */
    data object NoContent : EngineState

    data class Playing(val config: PlayerConfig) : EngineState
}

data class Timing(
    val activationPollMs: Long = 5_000,
    val heartbeatMs: Long = 30_000,
    val retryMs: Long = 10_000,
    val disabledPollMs: Long = 30_000,
    /** De quanto em quanto tempo o modo offline confere a agenda. */
    val offlineCheckMs: Long = 15_000,
    /** Sem contato com o servidor por tanto tempo, a TV passa a seguir a agenda guardada. */
    val offlineAfterMs: Long = 75_000,
    /** De quanto em quanto tempo a TV pede ao servidor os dados novos dos widgets (clima, notícias, cotações). */
    val widgetRefreshMs: Long = 10 * 60_000,
)

/**
 * Máquina de estados do Player: registro → aguarda ativação → heartbeat + sincronização.
 * Prioriza continuar exibindo o que já está em cache: falhas de rede nunca derrubam a reprodução.
 */
class PlayerEngine(
    private val api: PlayerApi,
    private val sync: ConfigSync,
    private val store: ConfigStore,
    private val token: () -> String,
    private val playerVersion: String,
    private val timing: Timing = Timing(),
    /** Recebe a mensagem de cada falha para o usuário ver (ver ErrorReporter). Nunca interrompe a reprodução. */
    private val onError: (String) -> Unit = {},
    /** Chamado a cada heartbeat que traz um pedido de verificação de atualização (valor do pedido). */
    private val onUpdateCheckRequested: (Long) -> Unit = {},
    /** O que o Player conta ao servidor a cada heartbeat (ocorrências, mídia na tela, exibições). */
    private val telemetry: Telemetry = Telemetry.shared,
    /** O diário de exibição (comprovação): entregue ao servidor depois de cada heartbeat. */
    private val plays: PlayLog? = null,
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    /** A hora de verdade (do servidor), mesmo sem internet: a agenda offline depende dela. */
    private val clock: TrustedClock = TrustedClock(),
    /** Mantém os dados dos widgets em dia (e guardados para quando a internet cair). */
    private val widgets: WidgetRefresher? = null,
) {

    private val mutableState = MutableStateFlow<EngineState>(EngineState.Starting)
    val state: StateFlow<EngineState> = mutableState

    /** Quando (no relógio monotônico) o servidor respondeu pela última vez. */
    private var lastContactAt = 0L
    private var offline = false

    /** A tela mostra o que a agenda guardada mandou (não o que o servidor confirmou): ao reconectar, o servidor tem a palavra final. */
    private var showsLocalPlan = false
    private var lastPlanKey: Pair<Long?, Long?>? = null

    /** A configuração mudou desde a última atualização dos dados dos widgets: buscar já, sem esperar o intervalo. */
    private var widgetsDirty = true
    private var lastWidgetRefresh = 0L

    suspend fun run() {
        coroutineScope {
            lastContactAt = clock.uptime()
            // Offline-first: após reiniciar a TV, começa a tocar o cache sem esperar a rede, já no intervalo certo da agenda.
            store.load()?.let { stored ->
                val planned = OfflinePlanner.configAt(stored, clock.now())
                showsLocalPlan = planned != null
                publish(planned ?: stored)
            }
            val watcher = launch { watchOffline() }
            try {
                loop()
            } finally {
                watcher.cancel()
            }
        }
    }

    private suspend fun loop() {
        while (true) {
            try {
                session()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnauthorizedException) {
                // O servidor não conhece mais este token (dispositivo excluído): volta ao registro.
                onError(ErrorMessages.describe(e))
                sync.reset()
                mutableState.value = EngineState.Starting
                delay(UNAUTHORIZED_RETRY_MS)
            } catch (e: IOException) {
                onError(ErrorMessages.describe("Servidor", e))
                if (mutableState.value == EngineState.Starting) mutableState.value = EngineState.ConnectionError
                delay(timing.retryMs)
            } catch (e: Exception) {
                // Resposta inesperada do servidor: não derruba o Player, tenta de novo adiante.
                onError(ErrorMessages.describe("Servidor", e))
                delay(timing.retryMs)
            }
        }
    }

    /**
     * Modo offline inteligente: enquanto o servidor não responde, a TV segue a agenda que ele mandou (campanhas que começam e
     * terminam na hora certa), em vez de ficar parada no que estava no ar quando a internet caiu. Com internet, quem manda é o
     * servidor: a agenda só entra em cena depois de [Timing.offlineAfterMs] sem contato.
     */
    private suspend fun watchOffline() {
        while (true) {
            delay(timing.offlineCheckMs)
            if (clock.uptime() - lastContactAt < timing.offlineAfterMs) continue
            val stored = store.load() ?: continue
            if (!offline) {
                offline = true
                val until = stored.plan?.untilMs
                telemetry.event(
                    Level.INFO,
                    if (until == null) "Sem contato com o servidor: a TV continua exibindo o conteúdo guardado."
                    else "Sem contato com o servidor: a TV segue a agenda guardada.",
                )
            }
            val planned = OfflinePlanner.configAt(stored, clock.now()) ?: continue
            val key = planned.campaignId to planned.playlistId
            if (key != lastPlanKey) {
                lastPlanKey = key
                telemetry.event(Level.INFO, "Agenda offline: no ar ${planned.campaignName?.let { "a campanha \"$it\"" } ?: "a playlist da TV"}.")
            }
            showsLocalPlan = true
            publish(planned)
        }
    }

    private suspend fun session() {
        var status: PlayerStatus = api.register(token(), playerVersion)

        while (true) {
            when {
                status.isActive -> {
                    activeSession()
                    return
                }

                status.isPending -> {
                    sync.reset()
                    mutableState.value = EngineState.WaitingActivation(status.activationCode.orEmpty())
                    delay(timing.activationPollMs)
                    status = api.status(token())
                }

                else -> {
                    sync.reset()
                    mutableState.value = EngineState.Disabled
                    delay(timing.disabledPollMs)
                    status = api.register(token(), playerVersion)
                }
            }
        }
    }

    private suspend fun activeSession() = coroutineScope {
        var syncJob: Job? = null
        var widgetJob: Job? = null

        while (isActive) {
            val report = telemetry.snapshot()
            val stored = store.load()
            val desired = try {
                api.heartbeat(token(), playerVersion, stored?.configVersion ?: 0, report, stored?.plan?.untilMs ?: 0)
            } catch (e: ApiException) {
                // HTTP 400: o servidor recusou este relato. Sem descartá-lo, o mesmo relato seria reenviado para
                // sempre e nenhum heartbeat mais passaria (a TV pareceria offline). Outros erros mantêm o relato.
                if (e.code == 400 && !report.isEmpty) telemetry.acknowledge(report)
                throw e
            }
            telemetry.acknowledge(report)
            noteContact(desired.serverTimeMs)
            if (desired.updateCheckToken > 0) onUpdateCheckRequested(desired.updateCheckToken)
            uploadPlays()

            // A sincronização roda à parte para o heartbeat continuar mesmo durante downloads longos.
            val syncing = syncJob?.isActive == true
            if (!syncing && sync.needsSync(desired)) {
                syncJob = launch {
                    try {
                        publish(sync.sync(token()))
                        showsLocalPlan = false
                        widgetsDirty = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: UnauthorizedException) {
                        throw e
                    } catch (e: Exception) {
                        // Download falhou: segue com a playlist anterior e tenta no próximo ciclo.
                        onError(ErrorMessages.describe("Falha ao atualizar o conteúdo (continuo exibindo o anterior)", e))
                    }
                }
            } else if (!syncing && showsLocalPlan) {
                // O servidor voltou e confirma o que está guardado: a tela passa a seguir o servidor de novo.
                store.load()?.let { publish(it) }
                showsLocalPlan = false
            }

            // Os dados dos widgets (clima, notícias, cotações) ficam em dia à parte, sem atrasar o heartbeat.
            val refresher = widgets
            if (refresher != null && widgetJob?.isActive != true && !syncing &&
                (widgetsDirty || clock.uptime() - lastWidgetRefresh >= timing.widgetRefreshMs)
            ) {
                val config = store.load()
                if (config != null) {
                    widgetsDirty = false
                    lastWidgetRefresh = clock.uptime()
                    widgetJob = launch {
                        try {
                            refresher.refresh(token(), config)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: UnauthorizedException) {
                            throw e
                        } catch (e: Exception) {
                            // os widgets seguem com o último dado guardado
                        }
                    }
                }
            }

            delay(timing.heartbeatMs)
        }
    }

    /**
     * Entrega o diário de exibição em lotes. Falha de rede deixa tudo como está (tenta no próximo ciclo); um lote que o
     * servidor RECUSA (HTTP 400) é descartado e avisado no histórico, para nunca travar os envios seguintes.
     */
    private suspend fun uploadPlays() {
        val log = plays ?: return
        var rounds = 0
        while (rounds++ < MAX_PLAY_BATCHES_PER_CYCLE) {
            val batch = log.pending(PlayLog.BATCH)
            if (batch.isEmpty()) return
            try {
                val acknowledged = api.sendPlays(token(), wallClock(), batch)
                log.acknowledge(minOf(acknowledged, batch.last().seq))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.code != 400) return
                log.acknowledge(batch.last().seq)
                telemetry.event(Level.WARN, "O servidor recusou ${batch.size} registro(s) de exibição (descartados): ${e.message}")
            } catch (e: Exception) {
                return
            }
        }
    }

    /** O servidor respondeu: acerta o relógio confiável e, se a TV estava sem contato, registra a volta. */
    private fun noteContact(serverTimeMs: Long) {
        clock.sync(serverTimeMs)
        if (offline) {
            offline = false
            lastPlanKey = null
            telemetry.event(Level.INFO, "Contato com o servidor restabelecido.")
        }
        lastContactAt = clock.uptime()
    }

    private fun publish(config: PlayerConfig) {
        // A agenda é só do motor: a tela recebe a configuração sem ela (renovar a agenda não reinicia a exibição).
        mutableState.value = if (config.hasContent()) EngineState.Playing(config.forScreen()) else EngineState.NoContent
    }

    companion object {
        private const val UNAUTHORIZED_RETRY_MS = 2_000L
        private const val MAX_PLAY_BATCHES_PER_CYCLE = 10
    }

}
