package com.digitalsignage.backend.tenant;

/** O plano do cliente não permite a operação (limite de TVs, de espaço ou de usuários). Vira HTTP 402 com a mensagem. */
public class PlanLimitException extends RuntimeException {

	public PlanLimitException(String message) {
		super(message);
	}

}
