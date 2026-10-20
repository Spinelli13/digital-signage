package com.digitalsignage.backend.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Armazenamento em qualquer serviço S3-compatível (Cloudflare R2, Backblaze B2, Supabase Storage, MinIO...).
 * O download não passa pelo backend: ele responde 302 para uma URL assinada de curta duração.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3MediaStorage implements MediaStorage {

	private final S3Client client;
	private final S3Presigner presigner;
	private final String bucket;
	private final Duration presignDuration;

	public S3MediaStorage(
			@Value("${app.storage.s3.bucket}") String bucket,
			@Value("${app.storage.s3.endpoint}") String endpoint,
			@Value("${app.storage.s3.region}") String region,
			@Value("${app.storage.s3.access-key}") String accessKey,
			@Value("${app.storage.s3.secret-key}") String secretKey,
			@Value("${app.storage.s3.presign-minutes}") long presignMinutes) {
		if (bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
			throw new IllegalStateException(
					"STORAGE_TYPE=s3 exige S3_BUCKET, S3_ACCESS_KEY e S3_SECRET_KEY (e S3_ENDPOINT fora da AWS)");
		}

		this.bucket = bucket;
		this.presignDuration = Duration.ofMinutes(presignMinutes);

		var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
		// Path-style é o que R2/B2/Supabase/MinIO esperam com endpoint customizado.
		var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

		var clientBuilder = S3Client.builder()
				.httpClient(UrlConnectionHttpClient.create())
				.region(Region.of(region))
				.credentialsProvider(credentials)
				.serviceConfiguration(s3Config)
				// Os checksums adicionais (CRC32 em trailer) que o SDK novo liga por padrão
				// quebram uploads em vários provedores S3-compatíveis.
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);

		var presignerBuilder = S3Presigner.builder()
				.region(Region.of(region))
				.credentialsProvider(credentials)
				.serviceConfiguration(s3Config);

		if (!endpoint.isBlank()) {
			clientBuilder.endpointOverride(URI.create(endpoint));
			presignerBuilder.endpointOverride(URI.create(endpoint));
		}

		this.client = clientBuilder.build();
		this.presigner = presignerBuilder.build();
	}

	@Override
	public StoredFile store(MultipartFile file) throws IOException {
		String storageKey = UUID.randomUUID() + "." + MediaFileNames.extensionOf(file);

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
				client.putObject(
						PutObjectRequest.builder()
								.bucket(bucket)
								.key(storageKey)
								.contentType(file.getContentType())
								.build(),
						RequestBody.fromInputStream(in, file.getSize()));
			}
			return new StoredFile(storageKey, HexFormat.of().formatHex(digest.digest()), file.getSize());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Resource load(String storageKey) {
		throw new UnsupportedOperationException("No modo S3 o download é feito por URL assinada");
	}

	@Override
	public Optional<URI> externalDownloadUrl(String storageKey, String mimeType) {
		GetObjectRequest getObject = GetObjectRequest.builder()
				.bucket(bucket)
				.key(storageKey)
				.responseContentType(mimeType)
				.build();

		var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(presignDuration)
				.getObjectRequest(getObject)
				.build());

		return Optional.of(URI.create(presigned.url().toString()));
	}

	@Override
	public void delete(String storageKey) {
		client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
	}

}
