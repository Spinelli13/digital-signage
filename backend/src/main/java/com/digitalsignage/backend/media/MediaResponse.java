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
		/** Só nos widgets: o tipo e as escolhas feitas no painel. */
		WidgetInfo widget,
		Instant createdAt,
		Instant updatedAt) {

	public record WidgetInfo(String kind, java.util.Map<String, Object> settings) {
	}

	public static MediaResponse from(Media media) {
		return new MediaResponse(
				media.getId(),
				media.getName(),
				media.getType(),
				media.getMimeType(),
				media.isWeb() ? media.getWebUrl() : media.isWidget() ? null : "/api/v1/media/" + media.getId() + "/file",
				media.getFileSize(),
				media.getDurationSeconds(),
				media.getChecksum(),
				media.isWidget() ? new WidgetInfo(media.getWidgetKind(), media.getWidgetConfig()) : null,
				media.getCreatedAt(),
				media.getUpdatedAt());
	}

}
