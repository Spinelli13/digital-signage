package com.digitalsignage.backend.device;

public record PlayerConfigMediaItem(
		Long mediaId,
		String name,
		String type,
		String url,
		String checksum,
		long fileSize,
		int position,
		int durationSeconds) {
}
