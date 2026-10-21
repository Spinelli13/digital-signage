package com.digitalsignage.backend.proof;

import java.time.Clock;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O Player entrega aqui, em lotes, os registros de exibição que guardou (também os de quando a TV ficou sem internet).
 * Repetir um lote é seguro: cada registro tem um número de ordem por TV e o repetido é ignorado. A resposta diz até que
 * número o servidor já tem; o Player apaga do aparelho o que já foi entregue.
 */
@RestController
@RequestMapping("/api/v1/player/plays")
public class PlayProofIngestController {

	public static final int MAX_BATCH = 500;

	public record EntryRequest(
			@NotNull Long seq, Long mediaId, @NotNull @Size(max = 500) String mediaName, @NotNull @Size(max = 10) String mediaType,
			Long playlistId, Long campaignId, @Size(max = 300) String campaignName, @Size(max = 300) String zoneName,
			@NotNull Long startedAtMs, @NotNull Long durationMs, Long plannedMs, boolean completed,
			@NotNull @Size(min = 64, max = 64) String prevHash, @NotNull @Size(min = 64, max = 64) String hash) {
	}

	/** {@code deviceTimeMs}: a hora que o relógio da TV marcava ao enviar (o servidor usa para corrigir um relógio desajustado). */
	public record BatchRequest(@NotNull Long deviceTimeMs, @NotNull @Size(max = MAX_BATCH) List<@Valid EntryRequest> entries) {
	}

	private final PlayProofService service;
	private final Clock clock;

	public PlayProofIngestController(PlayProofService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@PostMapping
	public PlayProofService.IngestResult ingest(@AuthenticationPrincipal Long deviceId, @Valid @RequestBody BatchRequest request) {
		List<PlayProofService.Entry> entries = request.entries().stream()
				.map(e -> new PlayProofService.Entry(e.seq(), e.mediaId(), e.mediaName(), e.mediaType(), e.playlistId(), e.campaignId(),
						e.campaignName(), e.zoneName(), e.startedAtMs(), e.durationMs(), e.plannedMs(), e.completed(), e.prevHash(), e.hash()))
				.toList();
		return service.ingest(deviceId, request.deviceTimeMs(), entries, clock.instant());
	}

}
