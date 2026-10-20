package com.digitalsignage.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash do token do dispositivo. SHA-256 sem salt é suficiente aqui porque o token é um segredo
 * aleatório de 256 bits gerado pelo próprio Player (não é uma senha escolhida por humano).
 */
public final class TokenHasher {

	private TokenHasher() {
	}

	public static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
