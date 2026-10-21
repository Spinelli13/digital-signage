package com.digitalsignage.player.engine

import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.PlayerApi
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.PlayerStatus
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.proof.PlayLog
import com.digitalsignage.player.sync.ConfigStore
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.Telemetry
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
) {

    private val mutableState = MutableStateFlow<EngineState>(EngineState.Starting)
    val state: StateFlow<EngineState> = mutableState

    suspend fun run() {
        // Offline-first: após reiniciar a TV, começa a tocar o cache sem esperar a rede.
        store.load()?.let { publish(it) }

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

        while (isActive) {
            val report = telemetry.snapshot()
            val desired = try {
                api.heartbeat(token(), playerVersion, store.load()?.configVersion ?: 0, report)
            } catch (e: ApiException) {
                // HTTP 400: o servidor recusou este relato. Sem descartá-lo, o mesmo relato seria reenviado para
                // sempre e nenhum heartbeat mais passaria (a TV pareceria offline). Outros erros mantêm o relato.
                if (e.code == 400 && !report.isEmpty) telemetry.acknowledge(report)
                throw e
            }
            telemetry.acknowledge(report)
            if (desired.updateCheckToken > 0) onUpdateCheckRequested(desired.updateCheckToken)
            uploadPlays()

            // A sincronização roda à parte para o heartbeat continuar mesmo durante downloads longos.
            if (syncJob?.isActive != true && sync.needsSync(desired)) {
                syncJob = launch {
                    try {
                        publish(sync.sync(token()))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: UnauthorizedException) {
                        throw e
                    } catch (e: Exception) {
                        // Download falhou: segue com a playlist anterior e tenta no próximo ciclo.
                        onError(ErrorMessages.describe("Falha ao atualizar o conteúdo (continuo exibindo o anterior)", e))
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

    private fun publish(config: PlayerConfig) {
        mutableState.value = if (config.hasContent()) EngineState.Playing(config) else EngineState.NoContent
    }

    companion object {
        private const val UNAUTHORIZED_RETRY_MS = 2_000L
        private const val MAX_PLAY_BATCHES_PER_CYCLE = 10
    }

}
