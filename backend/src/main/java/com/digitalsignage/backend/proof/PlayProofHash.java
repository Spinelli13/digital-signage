package com.digitalsignage.backend.proof;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A corrente de hashes da comprovação de exibição. Cada registro leva o hash do anterior ({@code prevHash}); o hash dele é o
 * SHA-256 dos campos abaixo, separados pelo caractere 0x1F (que não aparece em nomes). O Player calcula exatamente a mesma
 * conta (ver PlayLog.kt): apagar, trocar ou reordenar um registro quebra todos os que vêm depois.
 */
public final class PlayProofHash {

	/** O "anterior" do primeiro registro de uma TV. */
	public static final String GENESIS = "0".repeat(64);

	private static final char SEPARATOR = '\u001F';

	private PlayProofHash() {
	}

	public static String compute(String prevHash, long seq, long deviceId, Long mediaId, String mediaName, String mediaType,
			Long playlistId, Long campaignId, String zoneName, long startedAtMs, long durationMs, Long plannedMs, boolean completed) {
		String canonical = String.join(String.valueOf(SEPARATOR),
				prevHash,
				Long.toString(seq),
				Long.toString(deviceId),
				mediaId == null ? "" : mediaId.toString(),
				clean(mediaName),
				clean(mediaType),
				playlistId == null ? "" : playlistId.toString(),
				campaignId == null ? "" : campaignId.toString(),
				clean(zoneName),
				Long.toString(startedAtMs),
				Long.toString(durationMs),
				plannedMs == null ? "" : plannedMs.toString(),
				completed ? "1" : "0");
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** SHA-256 hexadecimal de um texto (resumo do período). */
	public static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String clean(String text) {
		return text == null ? "" : text.replace(SEPARATOR, ' ');
	}

	public static boolean isHash(String text) {
		return text != null && text.length() == 64 && text.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
	}

}
