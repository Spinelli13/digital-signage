package com.digitalsignage.backend.device;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ActivateDeviceRequest(
		@NotBlank String activationCode,
		@NotBlank String name,
		String location,
		Long locationId,
		List<Long> groupIds) {
}
