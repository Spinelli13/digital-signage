package com.digitalsignage.backend.device;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * Campos ausentes significam "não mudar". Para tirar a TV do local, mande {@code location} vazio ("");
 * para tirar de todos os grupos, mande {@code groupIds} como lista vazia.
 */
public record DeviceUpdateRequest(
		@NotBlank String name,
		String location,
		Long locationId,
		String status,
		Long currentPlaylistId,
		List<Long> groupIds) {
}
