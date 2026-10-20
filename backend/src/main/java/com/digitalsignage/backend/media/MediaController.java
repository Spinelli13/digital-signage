package com.digitalsignage.backend.media;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

	private final MediaRepository mediaRepository;
	private final MediaStorage storageService;

	public MediaController(MediaRepository mediaRepository, MediaStorage storageService) {
		this.mediaRepository = mediaRepository;
		this.storageService = storageService;
	}

	@GetMapping
	public List<MediaResponse> list() {
		return mediaRepository.findAll().stream().map(MediaResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MediaResponse upload(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) throws IOException {

		if (file.isEmpty()) {
			throw new IllegalArgumentException("Arquivo vazio");
		}

		MediaKind kind = MediaKind.fromMimeType(file.getContentType());
		StoredFile stored = storageService.store(file);

		String mediaName = (name == null || name.isBlank()) ? file.getOriginalFilename() : name;

		Media media = new Media(
				mediaName,
				kind.name(),
				file.getContentType(),
				stored.storageKey(),
				stored.size(),
				durationSeconds,
				stored.checksum());

		return MediaResponse.from(mediaRepository.save(media));
	}

	@GetMapping("/{id}")
	public MediaResponse get(@PathVariable Long id) {
		return MediaResponse.from(findOrThrow(id));
	}

	/**
	 * Com armazenamento externo (S3) responde 302 para uma URL assinada, para o arquivo não trafegar pelo
	 * backend. O Player deve seguir o redirecionamento SEM reenviar o X-Device-Token ao outro host.
	 */
	@GetMapping("/{id}/file")
	public ResponseEntity<Resource> download(@PathVariable Long id) {
		Media media = findOrThrow(id);

		Optional<URI> external = storageService.externalDownloadUrl(media.getStorageKey(), media.getMimeType());
		if (external.isPresent()) {
			return ResponseEntity.status(HttpStatus.FOUND).location(external.get()).build();
		}

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(media.getMimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getName() + "\"")
				.body(storageService.load(media.getStorageKey()));
	}

	/** Para o painel exibir pré-visualização: URL direta quando externa; senão o endpoint autenticado. */
	@GetMapping("/{id}/url")
	public MediaUrlResponse url(@PathVariable Long id) {
		Media media = findOrThrow(id);

		return storageService.externalDownloadUrl(media.getStorageKey(), media.getMimeType())
				.map(uri -> new MediaUrlResponse(uri.toString(), false))
				.orElseGet(() -> new MediaUrlResponse("/api/v1/media/" + media.getId() + "/file", true));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) throws IOException {
		Media media = findOrThrow(id);
		mediaRepository.delete(media);
		storageService.delete(media.getStorageKey());
	}

	private Media findOrThrow(Long id) {
		return mediaRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Mídia não encontrada: " + id));
	}

}
