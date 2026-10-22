package com.digitalsignage.backend.player;

import com.digitalsignage.backend.device.DeviceDiagnosticService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O relatório curto de diagnóstico da TV: texto pronto para o suporte e quantos itens estão com problema. */
public record PlayerDiagnosticRequest(
		@NotBlank @Size(max = 8_000) String report,
		Integer issues) {

	public PlayerDiagnosticRequest {
		if (issues != null && (issues < 0 || issues > DeviceDiagnosticService.MAX_ISSUES)) {
			throw new IllegalArgumentException("Quantidade de problemas inválida");
		}
	}
}
