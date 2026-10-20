package com.digitalsignage.backend.device;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Escolha exatamente UM alvo: {@code deviceIds} (TVs marcadas), {@code targetGroupId} (todas de um grupo) ou
 * {@code targetLocationId} (todas de um local). Os campos {@code playlistId}, {@code locationId} e
 * {@code groupId} são os parâmetros da ação.
 */
public record BulkDeviceRequest(
		@NotNull BulkAction action,
		List<Long> deviceIds,
		Long targetGroupId,
		Long targetLocationId,
		Long playlistId,
		Long locationId,
		Long groupId) {
}
