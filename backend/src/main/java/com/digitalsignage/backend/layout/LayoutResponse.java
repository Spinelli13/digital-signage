package com.digitalsignage.backend.layout;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LayoutResponse(
		Long id,
		String name,
		String description,
		long configVersion,
		List<ZoneResponse> zones,
		/** A versão para TV em pé (vazia = o layout não tem). */
		List<ZoneResponse> portraitZones,
		/** Quantas TVs usam este layout. */
		long deviceCount,
		Instant createdAt,
		Instant updatedAt) {

	public record ZoneResponse(
			Long id,
			String name,
			int x,
			int y,
			int width,
			int height,
			boolean main,
			Long playlistId,
			String playlistName,
			String fit) {
	}

	private static List<ZoneResponse> zonesOf(List<LayoutZone> zones, Map<Long, String> playlistNames) {
		return zones.stream()
				.map(zone -> new ZoneResponse(zone.getId(), zone.getName(), zone.getX(), zone.getY(), zone.getWidth(),
						zone.getHeight(), zone.isMain(), zone.getPlaylistId(),
						zone.getPlaylistId() == null ? null : playlistNames.get(zone.getPlaylistId()), zone.getFit()))
				.toList();
	}

	public static LayoutResponse from(Layout layout, Map<Long, String> playlistNames, long deviceCount) {
		return new LayoutResponse(
				layout.getId(),
				layout.getName(),
				layout.getDescription(),
				layout.getConfigVersion(),
				zonesOf(layout.getZones(), playlistNames),
				zonesOf(layout.getPortraitZones(), playlistNames),
				deviceCount,
				layout.getCreatedAt(),
				layout.getUpdatedAt());
	}

}
