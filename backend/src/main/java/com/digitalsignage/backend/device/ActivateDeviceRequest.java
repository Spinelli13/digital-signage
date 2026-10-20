package com.digitalsignage.backend.device;

import jakarta.validation.constraints.NotBlank;

public record ActivateDeviceRequest(
		@NotBlank String activationCode,
		@NotBlank String name,
		String location) {
}
