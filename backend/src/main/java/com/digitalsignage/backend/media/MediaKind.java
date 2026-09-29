package com.digitalsignage.backend.media;

public enum MediaKind {
	IMAGE,
	VIDEO;

	static MediaKind fromMimeType(String mimeType) {
		if (mimeType == null) {
			throw new IllegalArgumentException("Tipo de mídia não informado");
		}

		return switch (mimeType) {
			case "image/jpeg", "image/png", "image/webp" -> IMAGE;
			case "video/mp4" -> VIDEO;
			default -> throw new IllegalArgumentException("Tipo de mídia não suportado: " + mimeType);
		};
	}

}
