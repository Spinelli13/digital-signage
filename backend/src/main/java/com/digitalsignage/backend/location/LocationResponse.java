package com.digitalsignage.backend.location;

import java.time.Instant;

public record LocationResponse(
		Long id,
		String name,
		String description,
		/** Quantas TVs estão neste local (de qualquer status). */
		long deviceCount,
		/** Quantas delas falaram com o servidor nos últimos 2 minutos. */
		long onlineCount,
		Instant createdAt,
		Instant updatedAt) {

	public static LocationResponse from(Location location, long deviceCount, long onlineCount) {
		return new LocationResponse(
				location.getId(),
				location.getName(),
				location.getDescription(),
				deviceCount,
				onlineCount,
				location.getCreatedAt(),
				location.getUpdatedAt());
	}

}
