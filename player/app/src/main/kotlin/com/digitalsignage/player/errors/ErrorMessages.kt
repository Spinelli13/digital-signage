package com.digitalsignage.player.errors

import com.digitalsignage.player.cache.ChecksumMismatchException
import com.digitalsignage.player.data.ApiException
import com.digitalsignage.player.data.UnauthorizedException
import com.digitalsignage.player.sync.InsufficientSpaceException
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

        is InsufficientSpaceException ->
            "Não há espaço na TV para baixar o conteúdo novo (${error.message?.removePrefix("Espaço insuficiente: ") ?: "falta espaço"}). " +
                "Continuo exibindo o anterior; apague mídias grandes que não usa mais ou use arquivos menores."

        is ChecksumMismatchException, is UpdateIntegrityException ->
            "Um arquivo chegou corrompido e foi descartado. Tentarei baixar de novo."

        is JSONException, is IllegalArgumentException -> "O servidor enviou uma resposta inesperada."

        is java.io.IOException -> "Falha de comunicação com o servidor: ${error.message ?: error.javaClass.simpleName}."

        else -> "Erro inesperado (${error.javaClass.simpleName}${error.message?.let { ": $it" } ?: ""})."
    }

    /**
     * Por que um vídeo não tocou, pelo código do ExoPlayer (os números são os de androidx.media3.common.PlaybackException).
     * Os de decodificação quase sempre são formato que o aparelho não sabe (HEVC/H.265, 4K, HDR) e têm a saída óbvia:
     * reenviar o vídeo em H.264 de até 1080p.
     */
    fun playbackFailure(errorCode: Int, errorCodeName: String): String = when (errorCode) {
        DECODER_INIT_FAILED, DECODING_FAILED, DECODING_FORMAT_EXCEEDS_CAPABILITIES, DECODING_FORMAT_UNSUPPORTED ->
            "este aparelho não consegue tocar este vídeo (formato ou tamanho acima do que ele aguenta; por exemplo HEVC/H.265, 4K ou HDR). Reenvie em H.264 de até 1080p"

        PARSING_CONTAINER_UNSUPPORTED, PARSING_CONTAINER_MALFORMED, PARSING_MANIFEST_MALFORMED ->
            "o arquivo de vídeo está num formato que o aparelho não reconhece ou está incompleto. Reenvie o vídeo"

        IO_FILE_NOT_FOUND -> "o arquivo do vídeo não está mais no aparelho (será baixado de novo)"

        else -> "falha ao reproduzir o vídeo ($errorCodeName)"
    }

    private const val DECODER_INIT_FAILED = 4001
    private const val DECODING_FAILED = 4003
    private const val DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4002
    private const val DECODING_FORMAT_UNSUPPORTED = 4004
    private const val PARSING_CONTAINER_MALFORMED = 3001
    private const val PARSING_MANIFEST_MALFORMED = 3002
    private const val PARSING_CONTAINER_UNSUPPORTED = 3003
    private const val IO_FILE_NOT_FOUND = 2005

    /** "Contexto: explicação". Ex.: "Atualização do conteúdo: Sem acesso à internet…" */
    fun describe(context: String, error: Throwable): String = "$context: ${describe(error)}"

}
