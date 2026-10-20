package com.digitalsignage.backend.campaign;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Datas e horários vazios significam "sem limite" / "o dia todo". Um fim de horário menor que o início atravessa
 * a meia-noite. {@code daysOfWeek} usa 1 = segunda ... 7 = domingo (vazio = todos os dias).
 */
public record CampaignRequest(
		@NotBlank String name,
		String description,
		@NotNull Long playlistId,
		Integer priority,
		Boolean enabled,
		LocalDate startDate,
		LocalDate endDate,
		LocalTime startTime,
		LocalTime endTime,
		List<Integer> daysOfWeek,
		String timezone,
		@Valid List<TargetRequest> targets) {

	/** {@code id} é o da TV, do local ou do grupo; fica vazio para "todas as TVs". */
	public record TargetRequest(@NotNull TargetType type, Long id) {
	}

}
