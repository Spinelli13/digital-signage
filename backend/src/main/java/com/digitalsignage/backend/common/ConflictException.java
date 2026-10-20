package com.digitalsignage.backend.common;

/** Conflito com o estado atual (ex.: nome já usado). Vira HTTP 409 com a mensagem para o usuário. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
