package com.digitalsignage.backend.player;

import com.digitalsignage.backend.device.Device;

public record PlayerStatusResponse(
		Long deviceId,
		String status,
		String activationCode,
		String deviceName) {

	static PlayerStatusResponse from(Device device) {
		return new PlayerStatusResponse(
				device.getId(),
				device.getStatus(),
				device.getActivationCode(),
				device.getName());
	}

}
