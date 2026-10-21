package com.digitalsignage.backend.common;

/** Pedidos demais num tempo curto (ex.: senhas pelo celular). Vira HTTP 429 com a mensagem para a pessoa. */
public class RateLimitException extends RuntimeException {

	public RateLimitException(String message) {
		super(message);
	}

}
