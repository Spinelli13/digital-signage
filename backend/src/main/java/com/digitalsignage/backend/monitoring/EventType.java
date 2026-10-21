package com.digitalsignage.backend.monitoring;

/** Tipos de evento da linha do tempo de uma TV. */
public enum EventType {
	/** A TV foi vinculada no painel. */
	ACTIVATED,
	/** A TV voltou a falar com o servidor (ou falou pela primeira vez). */
	ONLINE,
	/** A TV parou de falar com o servidor. */
	OFFLINE,
	/** A TV foi desativada ou reativada no painel. */
	DISABLED,
	ENABLED,
	/** O conteúdo que a TV exibe mudou (playlist base ou campanha). */
	PLAYLIST_CHANGED,
	LAYOUT_CHANGED,
	/** O administrador disse como a TV foi instalada (horizontal, vertical...). */
	ORIENTATION_CHANGED,
	/** O administrador pediu que a TV verifique atualização do Player. */
	UPDATE_REQUESTED,
	/** A versão do Player da TV mudou. */
	PLAYER_UPDATED,
	/** Mensagem relatada pelo próprio Player (erros e avisos). */
	PLAYER_LOG
}
