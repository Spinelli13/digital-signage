package com.digitalsignage.backend.media;

import org.springframework.web.multipart.MultipartFile;

final class MediaFileNames {

	private MediaFileNames() {
	}

	static String extensionOf(MultipartFile file) {
		String original = file.getOriginalFilename();
		if (original != null && original.contains(".")) {
			return original.substring(original.lastIndexOf('.') + 1).toLowerCase();
		}

		return switch (String.valueOf(file.getContentType())) {
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			case "video/mp4" -> "mp4";
			default -> "bin";
		};
	}

}
