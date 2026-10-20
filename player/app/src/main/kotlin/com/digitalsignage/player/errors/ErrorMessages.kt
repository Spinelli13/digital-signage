package com.digitalsignage.player.errors

import com.digitalsignage.player.cache.ChecksumMismatchException
import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.update.UpdateIntegrityException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONException

/** Traduz exceções técnicas em frases que quem está ao lado da TV consegue entender e agir. */
object ErrorMessages {

    fun describe(error: Throwable): String = when (error) {
        is UnknownHostException ->
            "Sem acesso à internet ou o endereço do servidor não foi encontrado. Verifique a rede da TV."

        is SocketTimeoutException ->
            "O servidor demorou demais para responder. Se ele estava inativo, aguarde cerca de 1 minuto."

        is ConnectException -> "Não foi possível conectar ao servidor. Verifique a internet da TV."

        // Causa comum em TVs: data e hora erradas fazem o certificado do servidor parecer inválido.
        is SSLException ->
            "Falha na conexão segura com o servidor. Confira se a data e a hora da TV estão corretas."

        is UnauthorizedException ->
            "Este aparelho não é mais reconhecido pelo servidor (removido ou desativado no painel)."

        is ApiException -> when {
            error.code >= 500 -> "O servidor encontrou um erro (HTTP ${error.code}). Tentarei de novo."
            error.code == 404 -> "Item não encontrado no servidor (HTTP 404)."
            else -> "O servidor recusou o pedido (HTTP ${error.code})."
        }

        is ChecksumMismatchException, is UpdateIntegrityException ->
            "Um arquivo chegou corrompido e foi descartado. Tentarei baixar de novo."

        is JSONException, is IllegalArgumentException -> "O servidor enviou uma resposta inesperada."

        is java.io.IOException -> "Falha de comunicação com o servidor: ${error.message ?: error.javaClass.simpleName}."

        else -> "Erro inesperado (${error.javaClass.simpleName}${error.message?.let { ": $it" } ?: ""})."
    }

    /** "Contexto: explicação". Ex.: "Atualização do conteúdo: Sem acesso à internet…" */
    fun describe(context: String, error: Throwable): String = "$context: ${describe(error)}"

}
