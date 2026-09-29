package com.digitalsignage.backend.device;

import jakarta.validation.constraints.NotBlank;

public record DeviceCreateRequest(
		@NotBlank String name,
		String location) {
}
