package com.digitalsignage.backend.queue;

import java.security.SecureRandom;

/** Segredos curtos e imprevisíveis (128 bits em 32 caracteres hexadecimais) para os endereços públicos da fila. */
public final class Tokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private Tokens() {
	}

	public static String random() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		char[] out = new char[32];
		for (int i = 0; i < 16; i++) {
			out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
			out[i * 2 + 1] = HEX[bytes[i] & 0xF];
		}
		return new String(out);
	}

}
