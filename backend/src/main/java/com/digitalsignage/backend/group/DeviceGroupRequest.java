package com.digitalsignage.backend.group;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/** {@code deviceIds}, quando presente, define exatamente quem está no grupo; ausente = não mexe nos membros. */
public record DeviceGroupRequest(
		@NotBlank String name,
		String description,
		List<Long> deviceIds) {
}
