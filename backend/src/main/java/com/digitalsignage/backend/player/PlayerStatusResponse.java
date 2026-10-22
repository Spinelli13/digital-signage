package com.digitalsignage.backend.player;

import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceStatus;

/**
 * O que a TV sabe de si. {@code pairingUrl}: o endereço que o QR Code da tela de pareamento leva ao painel (só enquanto a TV
 * espera ser ativada); o código de 6 letras continua valendo para digitar.
 */
public record PlayerStatusResponse(
		Long deviceId,
		String status,
		String activationCode,
		String deviceName,
		String pairingUrl) {

	static PlayerStatusResponse from(Device device, PanelUrl panel) {
		boolean pending = DeviceStatus.PENDING.name().equals(device.getStatus());
		return new PlayerStatusResponse(
				device.getId(),
				device.getStatus(),
				device.getActivationCode(),
				device.getName(),
				pending ? panel.pairingUrl(device.getActivationCode()) : null);
	}

}
