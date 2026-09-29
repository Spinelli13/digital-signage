package com.digitalsignage.backend.device;

import java.time.Duration;
import java.time.Instant;

public record DeviceResponse(
		Long id,
		String name,
		String location,
		String status,
		boolean online,
		Instant lastSeenAt,
		String playerVersion,
		Long currentPlaylistId,
		long configVersion,
		Instant createdAt,
		Instant updatedAt) {

	// Provisório até o intervalo real de heartbeat ser definido no passo 15.
	private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(2);

	public static DeviceResponse from(Device device) {
		boolean online = device.getLastSeenAt() != null
				&& Duration.between(device.getLastSeenAt(), Instant.now()).compareTo(ONLINE_THRESHOLD) <= 0;

		return new DeviceResponse(
				device.getId(),
				device.getName(),
				device.getLocation(),
				device.getStatus(),
				online,
				device.getLastSeenAt(),
				device.getPlayerVersion(),
				device.getCurrentPlaylistId(),
				device.getConfigVersion(),
				device.getCreatedAt(),
				device.getUpdatedAt());
	}

}
