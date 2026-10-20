package com.digitalsignage.backend.campaign;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CampaignResponse(
		Long id,
		String name,
		String description,
		Long playlistId,
		String playlistName,
		int priority,
		boolean enabled,
		LocalDate startDate,
		LocalDate endDate,
		LocalTime startTime,
		LocalTime endTime,
		List<Integer> daysOfWeek,
		String timezone,
		List<TargetInfo> targets,
		/** Situação neste instante (ativa, aguardando horário, agendada, encerrada, pausada). */
		CampaignState state,
		/** Quantas TVs a campanha alcança (qualquer situação). */
		long deviceCount,
		Instant createdAt,
		Instant updatedAt) {

	/** {@code name} é o nome da TV, do local ou do grupo (vazio para "todas as TVs"). */
	public record TargetInfo(TargetType type, Long id, String name) {
	}

}
