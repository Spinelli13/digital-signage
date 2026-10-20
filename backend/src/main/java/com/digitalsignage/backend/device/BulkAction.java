package com.digitalsignage.backend.device;

/** Ações que o painel pode aplicar a várias TVs de uma vez. */
public enum BulkAction {
	/** Escolhe a playlist (exige playlistId). */
	ASSIGN_PLAYLIST,
	/** Tira a playlist das TVs. */
	REMOVE_PLAYLIST,
	/** Reativa as TVs desativadas. */
	ENABLE,
	/** Desativa as TVs ativas. */
	DISABLE,
	/** Pede que as TVs ativas verifiquem atualização do Player agora. */
	CHECK_UPDATE,
	/** Muda o local (exige locationId). */
	SET_LOCATION,
	/** Tira as TVs do local. */
	CLEAR_LOCATION,
	/** Coloca as TVs num grupo (exige groupId). */
	ADD_TO_GROUP,
	/** Tira as TVs de um grupo (exige groupId). */
	REMOVE_FROM_GROUP
}
