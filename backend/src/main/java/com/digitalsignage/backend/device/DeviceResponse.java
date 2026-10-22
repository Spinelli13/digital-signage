package com.digitalsignage.backend.device;

import java.time.Instant;
import java.util.List;

import com.digitalsignage.backend.campaign.CampaignService;

public record DeviceResponse(
		Long id,
		String name,
		/** Nome do local (vazio se a TV está sem local). Mantido para quem já lia o texto do local. */
		String location,
		Long locationId,
		String status,
		DeviceConnection connection,
		boolean online,
		Instant lastSeenAt,
		String playerVersion,
		Long currentPlaylistId,
		long configVersion,
		/** A playlist que a TV deve exibir AGORA: a de uma campanha ativa ou, sem campanha, a base ({@code currentPlaylistId}). */
		Long effectivePlaylistId,
		/** O que o Player diz estar na tela agora (id e nome da mídia). */
		Long currentMediaId,
		String currentMediaName,
		/** Campanha que está valendo para a TV agora (vazio = exibe a playlist base). */
		Long campaignId,
		String campaignName,
		/** Layout da tela (vazio = tela cheia). */
		Long layoutId,
		String layoutName,
		/** Como a TV foi instalada: AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT ou PORTRAIT_TOP_LEFT. */
		String orientation,
		/** A versão do conteúdo que a TV deveria ter sincronizado; diferente de {@code configVersion} = ainda sincronizando. */
		long desiredConfigVersion,
		List<Long> groupIds,
		/** Até quando a agenda offline guardada na TV vale (vazio = a TV não tem agenda). */
		Instant offlinePlanUntil,
		/** O equipamento que o Player informou (nulos até um Player novo contar). */
		String hardwareModel,
		String androidVersion,
		Integer ramMb,
		/** Número de série, data de instalação e observação de comodato: preenchidos à mão e só aparecem para o Administrador. */
		String serialNumber,
		java.time.LocalDate installedOn,
		String comodatoNote,
		Instant createdAt,
		Instant updatedAt) {

	public static DeviceResponse from(Device device, CampaignService.Effective effective, String currentMediaName, String layoutName, long desiredConfigVersion, Instant now, boolean inventoryVisible) {

		return new DeviceResponse(
				device.getId(),
				device.getName(),
				device.getLocation() == null ? null : device.getLocation().getName(),
				device.getLocation() == null ? null : device.getLocation().getId(),
				device.getStatus(),
				DeviceConnection.of(device, now),
				DeviceConnection.isRecent(device.getLastSeenAt(), now),
				device.getLastSeenAt(),
				device.getPlayerVersion(),
				device.getCurrentPlaylistId(),
				device.getConfigVersion(),
				effective.playlistId(),
				device.getCurrentMediaId(),
				currentMediaName,
				effective.fromCampaign() ? effective.campaign().getId() : null,
				effective.fromCampaign() ? effective.campaign().getName() : null,
				device.getLayoutId(),
				layoutName,
				device.getOrientation().name(),
				desiredConfigVersion,
				device.getGroupIds().stream().sorted().toList(),
				device.getOfflinePlanUntil(),
				device.getHardwareModel(),
				device.getAndroidVersion(),
				device.getRamMb(),
				inventoryVisible ? device.getSerialNumber() : null,
				inventoryVisible ? device.getInstalledOn() : null,
				inventoryVisible ? device.getComodatoNote() : null,
				device.getCreatedAt(),
				device.getUpdatedAt());
	}

}
