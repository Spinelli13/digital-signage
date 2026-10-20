package com.digitalsignage.backend.player;

import java.time.Instant;

/**
 * playlistId + configVersion identificam o conteúdo que o Player deve exibir. As duas coisas importam:
 * trocar a playlist do dispositivo por outra que tenha o mesmo configVersion também exige ressincronizar.
 *
 * updateCheckToken muda (milissegundos do pedido) quando o administrador manda a TV verificar atualização do
 * Player; 0 = nunca pediu. A TV só reage a valores que ainda não tratou.
 */
public record PlayerHeartbeatResponse(
		Long playlistId,
		long configVersion,
		Instant serverTime,
		long updateCheckToken) {
}
