package com.digitalsignage.backend.common;

/** O usuário está autenticado, mas o papel dele não permite esta operação. Vira HTTP 403 com a mensagem. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}

}
