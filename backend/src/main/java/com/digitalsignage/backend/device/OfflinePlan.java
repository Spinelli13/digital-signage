package com.digitalsignage.backend.device;

import java.util.List;

/**
 * A agenda que a TV leva consigo para os próximos dias: o que exibir em cada intervalo, já decidido pelo servidor
 * (campanha vencedora, prioridade, alvo, fuso, janelas que passam da meia-noite). Se a internet cair, o Player só precisa
 * olhar a hora e escolher o intervalo: nenhuma regra de campanha é repetida no aparelho, então os dois lados nunca divergem.
 *
 * <p>{@code version} muda quando algo que altera a agenda muda (campanha, alvo, playlist, layout); não muda com a passagem
 * do tempo. A janela ({@code generatedAtMs} até {@code untilMs}) é renovada pelo Player enquanto ele tem internet.
 */
public record OfflinePlan(
		long version,
		long generatedAtMs,
		long untilMs,
		List<Segment> segments,
		List<PlanPlaylist> playlists) {

	/** De {@code fromMs} (inclusive) até {@code toMs} (exclusive), em milissegundos desde 1970, a TV exibe esta playlist. */
	public record Segment(long fromMs, long toMs, Long campaignId, String campaignName, Long playlistId, long configVersion) {
	}

	/** O conteúdo de uma playlist usada em algum intervalo (a mesma playlist em vários intervalos vem uma vez). */
	public record PlanPlaylist(Long playlistId, List<PlayerConfigMediaItem> items) {
	}

}
