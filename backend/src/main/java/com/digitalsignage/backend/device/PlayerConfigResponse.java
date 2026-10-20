package com.digitalsignage.backend.device;

import java.util.List;

/**
 * {@code items} é sempre o conteúdo da zona principal (ou da tela toda, sem layout): um Player que não conhece
 * layouts continua exibindo a zona principal em tela cheia. {@code zones} só vem preenchida quando a TV tem layout.
 */
public record PlayerConfigResponse(
		Long deviceId,
		Long playlistId,
		long configVersion,
		List<PlayerConfigMediaItem> items,
		List<PlayerConfigZone> zones) {

	public PlayerConfigResponse(Long deviceId, Long playlistId, long configVersion, List<PlayerConfigMediaItem> items) {
		this(deviceId, playlistId, configVersion, items, List.of());
	}

}
