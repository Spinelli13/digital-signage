package com.digitalsignage.backend.media;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Onde os arquivos de mídia ficam guardados: disco local (dev) ou armazenamento S3-compatível (nuvem). */
public interface MediaStorage {

	StoredFile store(MultipartFile file) throws IOException;

	/** Conteúdo servido diretamente pelo backend (só faz sentido quando não há URL externa). */
	Resource load(String storageKey);

	/**
	 * URL temporária para baixar direto do armazenamento, sem passar pelo backend (poupa banda e memória
	 * em hosts gratuitos). Vazio quando o próprio backend serve o arquivo.
	 */
	Optional<URI> externalDownloadUrl(String storageKey, String mimeType);

	void delete(String storageKey) throws IOException;

}
