package com.digitalsignage.backend.device;

import java.time.Duration;
import java.time.Instant;

/**
 * Situação de comunicação de uma TV, calculada pelo tempo desde o último heartbeat (o Player fala a cada 30 s).
 * O painel e a API usam sempre esta mesma regra.
 */
public enum DeviceConnection {

	/** Ativa e falou com o servidor nos últimos 2 minutos. */
	ONLINE,
	/** Ativa, mas sem contato há mais de 2 minutos. */
	OFFLINE,
	/** Ativa, mas nunca falou com o servidor. */
	NEVER,
	/** Mostrou o código na TV e ainda não foi ativada no painel. */
	PENDING,
	/** Desativada pelo administrador. */
	DISABLED;

	public static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);

	public static boolean isRecent(Instant lastSeenAt, Instant now) {
		return lastSeenAt != null && Duration.between(lastSeenAt, now).compareTo(ONLINE_WINDOW) <= 0;
	}

	public static DeviceConnection of(Device device, Instant now) {
		if (DeviceStatus.PENDING.name().equals(device.getStatus())) {
			return PENDING;
		}
		if (DeviceStatus.DISABLED.name().equals(device.getStatus())) {
			return DISABLED;
		}
		if (device.getLastSeenAt() == null) {
			return NEVER;
		}
		return isRecent(device.getLastSeenAt(), now) ? ONLINE : OFFLINE;
	}

}
