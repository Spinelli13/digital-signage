package com.digitalsignage.backend.player;

import java.time.Instant;

/**
 * playlistId + configVersion identificam o conteúdo que o Player deve exibir. As duas coisas importam:
 * trocar a playlist do dispositivo por outra que tenha o mesmo configVersion também exige ressincronizar.
 */
public record PlayerHeartbeatResponse(
		Long playlistId,
		long configVersion,
		Instant serverTime) {
}
