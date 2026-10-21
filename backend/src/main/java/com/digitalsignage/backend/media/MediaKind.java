package com.digitalsignage.backend.media;

import java.util.Locale;
import java.util.Set;

public enum MediaKind {
	IMAGE,
	VIDEO,
	/** Página da web (não vem de arquivo: ver Media.web). */
	WEB;

	private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	/**
	 * Vídeos aceitos: MP4 e QuickTime (.mov, o que o iPhone grava, em H.264 ou HEVC) e o .m4v do iTunes/Apple. Se o
	 * decodificador da TV não souber o codec (HEVC em aparelho antigo, 4K), o Player avisa na tela e no histórico.
	 */
	private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/x-m4v", "video/m4v");

	/** Navegadores e celulares às vezes mandam "arquivo qualquer": nesse caso vale a extensão. */
	private static final Set<String> GENERIC_TYPES = Set.of("", "application/octet-stream", "binary/octet-stream");

	static MediaKind fromMimeType(String mimeType) {
		return of(mimeType, null);
	}

	/** O tipo pelo Content-Type declarado e, quando ele é genérico, pela extensão do nome do arquivo. */
	static MediaKind of(String mimeType, String fileName) {
		if (mimeType == null) {
			throw new IllegalArgumentException("Tipo de mídia não informado");
		}
		String type = normalize(mimeType);
		if (IMAGE_TYPES.contains(type)) {
			return IMAGE;
		}
		if (VIDEO_TYPES.contains(type)) {
			return VIDEO;
		}
		if (GENERIC_TYPES.contains(type)) {
			String extension = extensionOf(fileName);
			switch (extension) {
				case "jpg", "jpeg", "png", "webp" -> {
					return IMAGE;
				}
				case "mp4", "mov", "m4v" -> {
					return VIDEO;
				}
				default -> {
				}
			}
		}
		throw new IllegalArgumentException("Tipo de mídia não suportado: " + mimeType
				+ ". Use imagens JPG, PNG ou WEBP e vídeos MP4 ou MOV (o que o iPhone grava).");
	}

	/** O Content-Type que fica guardado e é devolvido ao baixar (nunca o genérico nem o "x-m4v"). */
	static String canonicalMimeType(MediaKind kind, String declared, String fileName) {
		String type = normalize(declared == null ? "" : declared);
		String extension = extensionOf(fileName);
		return switch (kind) {
			case VIDEO -> "mov".equals(extension) || "video/quicktime".equals(type) ? "video/quicktime" : "video/mp4";
			case IMAGE -> IMAGE_TYPES.contains(type) ? type : switch (extension) {
				case "png" -> "image/png";
				case "webp" -> "image/webp";
				default -> "image/jpeg";
			};
			case WEB -> type;
		};
	}

	private static String normalize(String mimeType) {
		int semicolon = mimeType.indexOf(';');
		return (semicolon >= 0 ? mimeType.substring(0, semicolon) : mimeType).trim().toLowerCase(Locale.ROOT);
	}

	private static String extensionOf(String fileName) {
		if (fileName == null || !fileName.contains(".")) {
			return "";
		}
		return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

}
