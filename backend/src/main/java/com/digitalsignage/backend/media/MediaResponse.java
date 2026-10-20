package com.digitalsignage.backend.media;

import java.time.Instant;

public record MediaResponse(
		Long id,
		String name,
		String type,
		String mimeType,
		String url,
		long fileSize,
		Integer durationSeconds,
		String checksum,
		Instant createdAt,
		Instant updatedAt) {

	public static MediaResponse from(Media media) {
		return new MediaResponse(
				media.getId(),
				media.getName(),
				media.getType(),
				media.getMimeType(),
				media.isWeb() ? media.getWebUrl() : "/api/v1/media/" + media.getId() + "/file",
				media.getFileSize(),
				media.getDurationSeconds(),
				media.getChecksum(),
				media.getCreatedAt(),
				media.getUpdatedAt());
	}

}
