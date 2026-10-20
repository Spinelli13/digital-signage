package com.digitalsignage.backend.device;

import java.time.Instant;
import java.util.List;

public record DeviceResponse(
		Long id,
		String name,
		/** Nome do local (vazio se a TV está sem local). Mantido para quem já lia o texto do local. */
		String location,
		Long locationId,
		String status,
		DeviceConnection connection,
		boolean online,
		Instant lastSeenAt,
		String playerVersion,
		Long currentPlaylistId,
		long configVersion,
		List<Long> groupIds,
		Instant createdAt,
		Instant updatedAt) {

	public static DeviceResponse from(Device device) {
		Instant now = Instant.now();

		return new DeviceResponse(
				device.getId(),
				device.getName(),
				device.getLocation() == null ? null : device.getLocation().getName(),
				device.getLocation() == null ? null : device.getLocation().getId(),
				device.getStatus(),
				DeviceConnection.of(device, now),
				DeviceConnection.isRecent(device.getLastSeenAt(), now),
				device.getLastSeenAt(),
				device.getPlayerVersion(),
				device.getCurrentPlaylistId(),
				device.getConfigVersion(),
				device.getGroupIds().stream().sorted().toList(),
				device.getCreatedAt(),
				device.getUpdatedAt());
	}

}
