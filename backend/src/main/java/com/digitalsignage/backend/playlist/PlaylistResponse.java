package com.digitalsignage.backend.playlist;

import java.time.Instant;
import java.util.List;

public record PlaylistResponse(
		Long id,
		String name,
		String description,
		long configVersion,
		List<PlaylistItemResponse> items,
		Instant createdAt,
		Instant updatedAt) {

	public static PlaylistResponse from(Playlist playlist) {
		return new PlaylistResponse(
				playlist.getId(),
				playlist.getName(),
				playlist.getDescription(),
				playlist.getConfigVersion(),
				playlist.getItems().stream().map(PlaylistItemResponse::from).toList(),
				playlist.getCreatedAt(),
				playlist.getUpdatedAt());
	}

}
