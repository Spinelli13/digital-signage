package com.digitalsignage.backend.monitoring;

public enum AlertType {
	/** A TV está sem contato há mais tempo que o limite configurado. */
	OFFLINE,
	/** A TV foi ativada e nunca falou com o servidor. */
	NEVER_CONNECTED,
	/** O Player relatou vários erros em pouco tempo. */
	PLAYER_ERRORS
}
