package com.digitalsignage.backend.playlist;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Um item da playlist. {@code validFrom}, {@code validUntil} e {@code days} (1 = segunda a 7 = domingo) são a validade do item,
 * sem campanha; todos ausentes = vale sempre. As datas são inclusivas e contadas no fuso de São Paulo.
 */
public record PlaylistItemRequest(
		@NotNull Long mediaId,
		Integer durationSeconds,
		LocalDate validFrom,
		LocalDate validUntil,
		List<Integer> days) {

	/** Sem validade (o formato de antes: só a mídia e a duração). */
	public PlaylistItemRequest(Long mediaId, Integer durationSeconds) {
		this(mediaId, durationSeconds, null, null, null);
	}
}
