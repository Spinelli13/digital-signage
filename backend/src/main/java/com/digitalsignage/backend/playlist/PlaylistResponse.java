package com.digitalsignage.backend.playlist;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PlaylistResponse(
		Long id,
		String name,
		String description,
		long configVersion,
		List<PlaylistItemResponse> items,
		Instant createdAt,
		Instant updatedAt) {

	/** {@code today}: o dia de hoje no fuso da validade (ver {@link ItemValidity#today}), para marcar o que está valendo. */
	public static PlaylistResponse from(Playlist playlist, LocalDate today) {
		return new PlaylistResponse(
				playlist.getId(),
				playlist.getName(),
				playlist.getDescription(),
				playlist.getConfigVersion(),
				playlist.getItems().stream().map(item -> PlaylistItemResponse.from(item, today)).toList(),
				playlist.getCreatedAt(),
				playlist.getUpdatedAt());
	}

}
