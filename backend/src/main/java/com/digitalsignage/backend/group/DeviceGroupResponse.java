package com.digitalsignage.backend.group;

import java.time.Instant;
import java.util.List;

public record DeviceGroupResponse(
		Long id,
		String name,
		String description,
		long deviceCount,
		long onlineCount,
		List<Long> deviceIds,
		Instant createdAt,
		Instant updatedAt) {

	public static DeviceGroupResponse from(DeviceGroup group, List<Long> deviceIds, long onlineCount) {
		return new DeviceGroupResponse(
				group.getId(),
				group.getName(),
				group.getDescription(),
				deviceIds.size(),
				onlineCount,
				deviceIds,
				group.getCreatedAt(),
				group.getUpdatedAt());
	}

}
