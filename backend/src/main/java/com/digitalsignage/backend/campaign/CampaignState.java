package com.digitalsignage.backend.campaign;

/** Situação de uma campanha neste instante, para o painel mostrar sem o usuário precisar calcular. */
public enum CampaignState {
	/** Está valendo agora. */
	ACTIVE,
	/** Dentro do período, mas fora do dia da semana ou do horário. */
	WAITING,
	/** O período ainda não começou. */
	SCHEDULED,
	/** O período já terminou. */
	ENDED,
	/** Pausada pelo administrador. */
	PAUSED
}
