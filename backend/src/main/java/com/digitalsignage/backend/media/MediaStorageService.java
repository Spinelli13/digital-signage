package com.digitalsignage.backend.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageService {

	private final Path basePath;

	public MediaStorageService(@Value("${app.storage.local-path}") String localPath) throws IOException {
		this.basePath = Path.of(localPath).toAbsolutePath().normalize();
		Files.createDirectories(basePath);
	}

	public StoredFile store(MultipartFile file) throws IOException {
		String storageKey = UUID.randomUUID() + "." + resolveExtension(file);
		Path target = basePath.resolve(storageKey);

		String checksum;
		try (InputStream in = file.getInputStream()) {
			checksum = copyAndHash(in, target);
		}

		return new StoredFile(storageKey, checksum, file.getSize());
	}

	public Resource load(String storageKey) {
		return new FileSystemResource(resolvePath(storageKey));
	}

	public void delete(String storageKey) throws IOException {
		Files.deleteIfExists(resolvePath(storageKey));
	}

	private Path resolvePath(String storageKey) {
		Path file = basePath.resolve(storageKey).normalize();
		if (!file.startsWith(basePath)) {
			throw new SecurityException("Caminho de armazenamento inválido: " + storageKey);
		}
		return file;
	}

	private String resolveExtension(MultipartFile file) {
		String original = file.getOriginalFilename();
		if (original != null && original.contains(".")) {
			return original.substring(original.lastIndexOf('.') + 1).toLowerCase();
		}

		return switch (file.getContentType()) {
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			case "video/mp4" -> "mp4";
			default -> "bin";
		};
	}

	private String copyAndHash(InputStream in, Path target) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream digestIn = new DigestInputStream(in, digest);
					OutputStream out = Files.newOutputStream(target)) {
				digestIn.transferTo(out);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
