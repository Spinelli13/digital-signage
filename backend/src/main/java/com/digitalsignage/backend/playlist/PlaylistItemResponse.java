package com.digitalsignage.backend.playlist;

import java.time.LocalDate;
import java.util.List;

public record PlaylistItemResponse(
		Long mediaId,
		String mediaName,
		String mediaType,
		int position,
		int durationSeconds,
		/** Primeiro e último dia de validade (inclusive); nulos = sem limite. */
		LocalDate validFrom,
		LocalDate validUntil,
		/** Dias da semana em que vale (1 = segunda a 7 = domingo); nulo = todos os dias. */
		List<Integer> days,
		/** Este item está valendo hoje (no fuso de São Paulo)? Os sem validade sempre estão. */
		boolean validToday) {

	static PlaylistItemResponse from(PlaylistItem item, LocalDate today) {
		return new PlaylistItemResponse(
				item.getMedia().getId(),
				item.getMedia().getName(),
				item.getMedia().getType(),
				item.getPosition(),
				item.getDurationSeconds(),
				item.getValidFrom(),
				item.getValidUntil(),
				ItemValidity.daysOf(item.getDaysOfWeek()),
				item.isValidOn(today));
	}

}
