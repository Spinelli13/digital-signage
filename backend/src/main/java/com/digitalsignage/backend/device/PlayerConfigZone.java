package com.digitalsignage.backend.device;

import java.util.List;

/** Uma zona da tela com as mídias dela. Posição e tamanho em % da tela. */
public record PlayerConfigZone(
		Long id,
		String name,
		int x,
		int y,
		int width,
		int height,
		boolean main,
		Long playlistId,
		List<PlayerConfigMediaItem> items) {
}
