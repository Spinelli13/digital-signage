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
		/** Layout da tela; ausente não muda (para voltar à tela cheia use DELETE /devices/{id}/layout). */
		Long layoutId,
		List<Long> groupIds,
		/** Como a TV foi instalada (AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT ou PORTRAIT_TOP_LEFT); ausente não muda. */
		String orientation) {
}
