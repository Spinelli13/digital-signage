package com.digitalsignage.backend.promo;

import java.util.Locale;

/**
 * De onde veio o QR Code escaneado. O painel gera um QR Code por TV com {@code ?o=tv-12} (o id da TV) e dá para marcar outros
 * lugares com um rótulo livre ({@code ?o=balcao}, {@code ?o=panfleto}). Vem de um endereço público, então só entra o que cabe numa
 * etiqueta simples: letras minúsculas, números e hífen, até 24 caracteres; qualquer outra coisa vira "sem origem" (vazio).
 */
public final class ScanOrigin {

	public static final int MAX_LENGTH = 24;

	private ScanOrigin() {
	}

	/** A origem limpa, ou vazio quando não veio ou não é uma etiqueta válida. */
	public static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		String value = raw.strip().toLowerCase(Locale.ROOT);
		return value.matches("[a-z0-9][a-z0-9-]{0," + (MAX_LENGTH - 1) + "}") ? value : "";
	}

	/** O id da TV de uma origem "tv-12" (12); nulo se não é uma origem de TV. */
	public static Long deviceOf(String origin) {
		if (origin == null || !origin.matches("tv-[0-9]{1,12}")) {
			return null;
		}
		return Long.parseLong(origin.substring(3));
	}
}
