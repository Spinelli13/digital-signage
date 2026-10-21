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
		List<PlayerConfigMediaItem> items,
		/** CONTAIN (a mídia cabe inteira) ou COVER (preenche a zona, cortando as bordas). */
		String fit) {

	public PlayerConfigZone(Long id, String name, int x, int y, int width, int height, boolean main, Long playlistId, List<PlayerConfigMediaItem> items) {
		this(id, name, x, y, width, height, main, playlistId, items, "CONTAIN");
	}
}
