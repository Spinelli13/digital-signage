package com.digitalsignage.backend.playlist;

public record PlaylistItemResponse(
		Long mediaId,
		String mediaName,
		String mediaType,
		int position,
		int durationSeconds) {

	static PlaylistItemResponse from(PlaylistItem item) {
		return new PlaylistItemResponse(
				item.getMedia().getId(),
				item.getMedia().getName(),
				item.getMedia().getType(),
				item.getPosition(),
				item.getDurationSeconds());
	}

}
