package com.digitalsignage.backend.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O Player gera o próprio token (segredo aleatório) e o envia aqui uma única vez; o servidor guarda só o hash.
 * Esse mesmo token autentica todas as chamadas seguintes via header X-Device-Token.
 */
public record PlayerRegisterRequest(
		@NotBlank @Size(min = 32, max = 128) String deviceToken,
		@Size(max = 50) String playerVersion) {
}
