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
		List<PlayerConfigZone> zones,
		/** A campanha que está no ar agora (nula = a playlist da própria TV): entra na comprovação de exibição. */
		Long campaignId,
		String campaignName,
		/** A agenda dos próximos dias, para a TV continuar certa se a internet cair (nula = nenhuma campanha vale para a TV). */
		OfflinePlan offlinePlan,
		/** As zonas da versão do layout para TV em pé (vazio = o layout não tem; a TV em pé usa {@code zones}). */
		List<PlayerConfigZone> portraitZones,
		/** Como a TV foi instalada (AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT ou PORTRAIT_TOP_LEFT): o Player gira o conteúdo. */
		String orientation) {

	public PlayerConfigResponse(Long deviceId, Long playlistId, long configVersion, List<PlayerConfigMediaItem> items) {
		this(deviceId, playlistId, configVersion, items, List.of(), null, null, null, List.of(), Orientation.AUTO.name());
	}

	public PlayerConfigResponse(Long deviceId, Long playlistId, long configVersion, List<PlayerConfigMediaItem> items, List<PlayerConfigZone> zones) {
		this(deviceId, playlistId, configVersion, items, zones, null, null, null, List.of(), Orientation.AUTO.name());
	}

	public PlayerConfigResponse(Long deviceId, Long playlistId, long configVersion, List<PlayerConfigMediaItem> items, List<PlayerConfigZone> zones,
			List<PlayerConfigZone> portraitZones) {
		this(deviceId, playlistId, configVersion, items, zones, null, null, null, portraitZones, Orientation.AUTO.name());
	}

	public PlayerConfigResponse withCampaign(Long id, String name) {
		return new PlayerConfigResponse(deviceId, playlistId, configVersion, items, zones, id, name, offlinePlan, portraitZones, orientation);
	}

	public PlayerConfigResponse withPlan(OfflinePlan plan) {
		return new PlayerConfigResponse(deviceId, playlistId, configVersion, items, zones, campaignId, campaignName, plan, portraitZones, orientation);
	}

	public PlayerConfigResponse withOrientation(Orientation value) {
		return new PlayerConfigResponse(deviceId, playlistId, configVersion, items, zones, campaignId, campaignName, offlinePlan, portraitZones, value.name());
	}

}
