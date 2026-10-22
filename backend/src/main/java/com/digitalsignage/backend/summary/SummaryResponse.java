package com.digitalsignage.backend.summary;

import java.time.Instant;
import java.util.List;

/** Números do dashboard, calculados no servidor com a mesma regra de "online" usada em todo o sistema. */
public record SummaryResponse(
		DeviceCounts devices,
		long locations,
		long groups,
		long media,
		long playlists,
		CampaignCounts campaigns,
		/** Alertas abertos agora (TV offline, nunca conectou, erros repetidos). */
		long openAlerts,
		List<LocationSummary> byLocation,
		/** TVs que pedem atenção: offline, nunca conectadas ou aguardando ativação (as mais urgentes primeiro). */
		List<AttentionDevice> attention) {

	/** {@code activeNow}: campanhas valendo neste instante (não pausadas, dentro de período, dia e horário). */
	public record CampaignCounts(long total, long activeNow) {
	}

	/** {@code withContent}: TVs já ativadas com uma playlist ou um layout escolhido (o último passo do "primeiros passos" do painel). */
	public record DeviceCounts(long total, long online, long offline, long never, long pending, long disabled, long withContent) {
	}

	/** {@code id} nulo = TVs sem local. */
	public record LocationSummary(Long id, String name, long total, long online) {
	}

	public record AttentionDevice(Long id, String name, String location, String connection, Instant lastSeenAt) {
	}

}
