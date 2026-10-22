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
		long updateCheckToken,
		/** A campanha no ar (nula = playlist da TV): o Player refaz a configuração se ela mudar, para a comprovação citar a campanha certa. */
		Long campaignId,
		/** Muda quando a agenda offline da TV muda (0 = a TV não tem agenda). A TV baixa a configuração de novo se for diferente da que guardou. */
		long planVersion,
		/** Muda (milissegundos do pedido) quando o painel pede o diagnóstico da TV; 0 = nunca pediu. A TV só reage a valores que ainda não tratou. */
		long diagnosticToken) {
}
