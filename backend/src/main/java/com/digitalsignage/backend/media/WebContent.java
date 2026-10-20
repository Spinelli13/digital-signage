package com.digitalsignage.backend.media;

import java.net.URI;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Conteúdo web: uma página que o Player abre e exibe pelo tempo escolhido. */
public final class WebContent {

	public static final int DEFAULT_SECONDS = 30;
	public static final int MAX_URL_LENGTH = 2000;

	public record Request(
			@Size(max = 255) String name,
			@NotBlank String url,
			@NotNull @Min(5) @Max(3600) Integer durationSeconds) {
	}

	private WebContent() {
	}

	/**
	 * Só https (o Player de produção não abre http) e sem usuário/senha na URL (apareceria no painel e nos logs).
	 * Devolve o endereço limpo. Quem decide se a página é segura de exibir é o administrador.
	 */
	public static URI validate(String url) {
		String text = url == null ? "" : url.trim();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("Informe o endereço da página");
		}
		if (text.length() > MAX_URL_LENGTH) {
			throw new IllegalArgumentException("O endereço é longo demais (máximo " + MAX_URL_LENGTH + " caracteres)");
		}
		URI uri;
		try {
			uri = URI.create(text);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("O endereço da página não é válido");
		}
		if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
			throw new IllegalArgumentException("O endereço da página precisa começar com https://");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException("O endereço da página não é válido");
		}
		if (uri.getUserInfo() != null) {
			throw new IllegalArgumentException("O endereço da página não pode conter usuário e senha");
		}
		return uri;
	}

	/** Sem nome informado, usa o endereço do site. */
	public static String nameFor(String name, URI uri) {
		return name == null || name.isBlank() ? uri.getHost() : name.trim();
	}

}
