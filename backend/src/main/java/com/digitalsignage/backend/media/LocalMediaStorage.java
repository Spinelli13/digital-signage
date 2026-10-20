package com.digitalsignage.backend.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorage implements MediaStorage {

	private final Path basePath;

	public LocalMediaStorage(@Value("${app.storage.local-path}") String localPath) throws IOException {
		this.basePath = Path.of(localPath).toAbsolutePath().normalize();
		Files.createDirectories(basePath);
	}

	@Override
	public StoredFile store(MultipartFile file) throws IOException {
		String storageKey = UUID.randomUUID() + "." + MediaFileNames.extensionOf(file);
		Path target = basePath.resolve(storageKey);

		String checksum;
		try (InputStream in = file.getInputStream()) {
			checksum = copyAndHash(in, target);
		}

		return new StoredFile(storageKey, checksum, file.getSize());
	}

	@Override
	public Resource load(String storageKey) {
		return new FileSystemResource(resolvePath(storageKey));
	}

	@Override
	public Optional<URI> externalDownloadUrl(String storageKey, String mimeType) {
		return Optional.empty();
	}

	@Override
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
