package com.digitalsignage.player.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** O que o Player sabe de si mesmo neste instante (colhido pelo [DiagnosticsCollector]; nos testes, valores fixos). */
data class DiagnosticsInput(
    val version: String,
    val model: String,
    val androidRelease: String,
    val ramMb: Long,
    val serverUrl: String,
    val internet: Boolean,
    /** Há quantos segundos o servidor respondeu pela última vez (nulo = nunca, desde que o Player abriu). */
    val contactAgoSec: Long?,
    val freeMb: Long,
    val cacheMb: Long,
    /** Quando a configuração foi sincronizada pela última vez (0 = ainda não, desde que o Player abriu). */
    val lastSyncAtMs: Long,
    val nowMs: Long,
    val missingFiles: Int,
    val missingMb: Long,
    val bootOk: Boolean,
    val overlayOk: Boolean,
    val installOk: Boolean,
)

/** Um item do relatório: o que foi conferido, o resultado em texto e se está tudo certo. */
data class DiagnosticLine(val label: String, val value: String, val ok: Boolean)

/** O relatório pronto: as linhas para a tela e o texto curto que vai para o suporte. */
class DiagnosticsReport(val header: String, val lines: List<DiagnosticLine>) {

    /** Quantos itens estão com problema (0 = tudo certo). */
    val issues: Int get() = lines.count { !it.ok }

    fun text(): String = header + "\n" + lines.joinToString("\n") { "${if (it.ok) "OK" else "!!"} ${it.label}: ${it.value}" }
}

object Diagnostics {

    /** Abaixo disto o aparelho corre o risco de não conseguir baixar o conteúdo novo. */
    const val LOW_SPACE_MB = 200L

    /** O Player fala com o servidor a cada 30 s: mais que isto sem resposta é problema. */
    const val CONTACT_STALE_SEC = 120L

    fun build(input: DiagnosticsInput, zone: TimeZone = TimeZone.getDefault()): DiagnosticsReport {
        val format = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).apply { timeZone = zone }
        val header = "Vyzzu Player ${input.version} | Android ${input.androidRelease} | ${input.model} | RAM ${input.ramMb} MB\n" +
            "Servidor: ${input.serverUrl}\n" +
            "Gerado em ${format.format(Date(input.nowMs))}"

        val lines = mutableListOf<DiagnosticLine>()
        lines += DiagnosticLine("Internet", if (input.internet) "conectada" else "SEM CONEXÃO (confira o Wi-Fi ou o cabo)", input.internet)

        val contact = input.contactAgoSec
        lines += when {
            contact == null -> DiagnosticLine("Servidor", "NÃO RESPONDEU desde que o Player abriu", false)
            contact <= CONTACT_STALE_SEC -> DiagnosticLine("Servidor", "respondeu há ${duration(contact)}", true)
            else -> DiagnosticLine("Servidor", "SEM RESPOSTA há ${duration(contact)}", false)
        }

        val lowSpace = input.freeMb < LOW_SPACE_MB
        lines += DiagnosticLine(
            "Espaço livre",
            "${input.freeMb} MB (conteúdo guardado: ${input.cacheMb} MB)" + if (lowSpace) " POUCO ESPAÇO" else "",
            !lowSpace,
        )

        lines += DiagnosticLine(
            "Última sincronização",
            if (input.lastSyncAtMs <= 0) "nenhuma desde que o Player abriu" else "${format.format(Date(input.lastSyncAtMs))} (há ${duration((input.nowMs - input.lastSyncAtMs) / 1000)})",
            true,
        )

        lines += if (input.missingFiles == 0) {
            DiagnosticLine("A baixar", "nada: tudo está no aparelho", true)
        } else {
            DiagnosticLine("A baixar", "FALTAM ${input.missingFiles} arquivo(s), ${input.missingMb} MB", false)
        }

        lines += permission("Iniciar junto com a TV", input.bootOk, "o Player não volta sozinho depois de a TV ser religada")
        lines += permission("Sobrepor outros apps", input.overlayOk, "o Player pode não voltar à tela sozinho; ative em Configurações > Apps > Vyzzu")
        lines += permission("Instalar apps (atualização)", input.installOk, "o Player não consegue se atualizar; ative em Configurações > Apps > Vyzzu > Instalar apps desconhecidos")
        return DiagnosticsReport(header, lines)
    }

    private fun permission(label: String, granted: Boolean, consequence: String) =
        DiagnosticLine(label, if (granted) "permitido" else "FALTA: $consequence", granted)

    /** "45 s", "12 min", "3 h 20 min", "2 dias". */
    fun duration(seconds: Long): String = when {
        seconds < 90 -> "${seconds.coerceAtLeast(0)} s"
        seconds < 3_600 -> "${seconds / 60} min"
        seconds < 86_400 -> "${seconds / 3_600} h ${(seconds % 3_600) / 60} min"
        else -> "${seconds / 86_400} dia(s)"
    }
}
