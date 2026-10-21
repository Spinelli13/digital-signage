package com.digitalsignage.backend.layout;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Posição e tamanho das zonas são em % da tela (0-100), a partir do canto superior esquerdo. Exatamente uma zona é a
 * principal (usa a playlist da TV); as outras podem ter uma playlist fixa.
 */
public record LayoutRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 500) String description,
		@NotNull @Valid List<ZoneRequest> zones,
		/** A versão do layout para TV em pé (opcional): as mesmas regras das zonas horizontais. Vazio = sem versão em pé. */
		@Valid List<ZoneRequest> portraitZones) {

	public record ZoneRequest(
			@NotBlank @Size(max = 50) String name,
			int x,
			int y,
			int width,
			int height,
			boolean main,
			Long playlistId,
			/** CONTAIN (cabe inteira) ou COVER (preenche, cortando); vazio = CONTAIN. */
			@Size(max = 10) String fit) {

		public ZoneRequest(String name, int x, int y, int width, int height, boolean main, Long playlistId) {
			this(name, x, y, width, height, main, playlistId, null);
		}
	}

	public LayoutRequest(String name, String description, List<ZoneRequest> zones) {
		this(name, description, zones, null);
	}

}
