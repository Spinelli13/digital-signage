package com.digitalsignage.backend.monitoring;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Regras para o endereço do webhook de alertas. O servidor faz uma chamada para este endereço, então ele não pode
 * apontar para a rede interna (proteção contra SSRF): só https e só endereços públicos. Os testes liberam isso
 * (app.monitoring.allow-insecure-webhooks) para falar com um servidor local.
 */
@Component
public class WebhookUrlPolicy {

	private final boolean allowInsecure;

	public WebhookUrlPolicy(@Value("${app.monitoring.allow-insecure-webhooks:false}") boolean allowInsecure) {
		this.allowInsecure = allowInsecure;
	}

	/** Valida e devolve a URL limpa; lança IllegalArgumentException (HTTP 400) com a razão em português. */
	public URI validate(String url) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Informe o endereço do webhook");
		}
		URI uri;
		try {
			uri = URI.create(url.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("O endereço do webhook não é válido");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
		if (!scheme.equals("https") && !(allowInsecure && scheme.equals("http"))) {
			throw new IllegalArgumentException("O endereço do webhook precisa começar com https://");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException("O endereço do webhook não é válido");
		}
		if (uri.getUserInfo() != null) {
			throw new IllegalArgumentException("O endereço do webhook não pode conter usuário e senha");
		}
		if (!allowInsecure) {
			requirePublicHost(uri.getHost());
		}
		return uri;
	}

	private static void requirePublicHost(String host) {
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("Não foi possível encontrar o endereço do webhook (" + host + ")");
		}
		for (InetAddress address : addresses) {
			if (isInternal(address)) {
				throw new IllegalArgumentException("O endereço do webhook não pode apontar para a rede interna");
			}
		}
	}

	static boolean isInternal(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			int first = bytes[0] & 0xff;
			int second = bytes[1] & 0xff;
			return (first == 100 && second >= 64 && second <= 127) // CGNAT 100.64.0.0/10
					|| first == 0
					|| first >= 240;
		}
		return (bytes[0] & 0xfe) == 0xfc; // IPv6 ULA fc00::/7
	}

	/** O que é seguro mostrar de volta: só o host e o final do endereço (a URL inteira é um segredo). */
	public static String hint(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(url.trim());
			String tail = url.trim().length() > 4 ? url.trim().substring(url.trim().length() - 4) : "";
			return uri.getHost() + "/…" + tail;
		} catch (IllegalArgumentException e) {
			return "…";
		}
	}

}
