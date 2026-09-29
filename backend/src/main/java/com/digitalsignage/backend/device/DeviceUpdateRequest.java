package com.digitalsignage.backend.device;

import jakarta.validation.constraints.NotBlank;

public record DeviceUpdateRequest(
		@NotBlank String name,
		String location,
		String status) {
}
