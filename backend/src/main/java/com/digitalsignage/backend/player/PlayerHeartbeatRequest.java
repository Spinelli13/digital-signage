package com.digitalsignage.backend.player;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * configVersion = versão da playlist que o Player de fato sincronizou (baixou e validou).
 *
 * <p>Os campos seguintes são opcionais (Players antigos não os enviam): a mídia que está na tela agora, o que
 * aconteceu desde o último heartbeat bem-sucedido (erros e avisos) e quantas vezes cada mídia foi exibida.
 */
public record PlayerHeartbeatRequest(
		@Size(max = 50) String playerVersion,
		long configVersion,
		Long currentMediaId,
		@Valid @Size(max = 30) List<PlayerEvent> events,
		@Valid @Size(max = 100) List<PlayRecord> plays,
		/** Até quando (milissegundos desde 1970) vale a agenda offline guardada na TV; ausente = a TV não tem agenda. */
		Long planUntilMs,
		/** O equipamento (inventário): modelo, versão do Android e memória em MB. Ausentes em Players antigos. */
		@Size(max = 80) String model,
		@Size(max = 30) String androidVersion,
		@jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1_000_000) Integer ramMb) {

	/** Uma ocorrência relatada pelo Player. {@code level}: INFO, WARN ou ERROR. {@code at}: quando aconteceu. */
	public record PlayerEvent(@Size(max = 10) String level, @Size(max = 500) String message, Instant at) {
	}

	/** Exibições de uma mídia desde o último heartbeat: quantas vezes e por quantos segundos no total. */
	public record PlayRecord(Long mediaId, int count, long seconds) {
	}

}
