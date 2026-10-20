package com.digitalsignage.backend.device;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * O local pode vir por id ({@code locationId}) ou por nome ({@code location}): com um nome que ainda não existe,
 * o local é criado na hora (o fluxo de ativação digita "Loja Centro" e pronto).
 */
public record DeviceCreateRequest(
		@NotBlank String name,
		String location,
		Long locationId,
		List<Long> groupIds) {
}
