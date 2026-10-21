package com.digitalsignage.backend.device;

public record PlayerConfigMediaItem(
		Long mediaId,
		String name,
		String type,
		String url,
		String checksum,
		long fileSize,
		int position,
		int durationSeconds,
		/** Só nos widgets: {@code kind} e {@code settings} (as escolhas do painel). Nulo nas demais mídias. */
		java.util.Map<String, Object> widget) {

	public PlayerConfigMediaItem(Long mediaId, String name, String type, String url, String checksum, long fileSize, int position,
			int durationSeconds) {
		this(mediaId, name, type, url, checksum, fileSize, position, durationSeconds, null);
	}
}
