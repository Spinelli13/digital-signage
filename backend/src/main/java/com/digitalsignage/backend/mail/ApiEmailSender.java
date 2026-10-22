package com.digitalsignage.backend.mail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Envio pela API HTTPS do provedor (Resend ou Brevo). Existe porque o plano gratuito do Render bloqueia as portas de SMTP
 * (25, 465 e 587); HTTPS passa em qualquer plano. A chave da API só entra por variável de ambiente e nunca vai para o log.
 */
public final class ApiEmailSender implements EmailSender {

	public enum Provider {
		RESEND("https://api.resend.com/emails"),
		BREVO("https://api.brevo.com/v3/smtp/email");

		private final String endpoint;

		Provider(String endpoint) {
			this.endpoint = endpoint;
		}

		/** O provedor pelo nome da variável {@code MAIL_API}; nulo quando o nome não é conhecido. */
		public static Provider of(String name) {
			if (name == null) {
				return null;
			}
			for (Provider provider : values()) {
				if (provider.name().equals(name.strip().toUpperCase(Locale.ROOT))) {
					return provider;
				}
			}
			return null;
		}
	}

	/** O remetente separado em nome e endereço ("Vyzzu <avisos@empresa.com>" ou só o endereço). */
	record Sender(String name, String address) {

		static Sender parse(String text) {
			String value = text.strip();
			int open = value.lastIndexOf('<');
			int close = value.lastIndexOf('>');
			if (open >= 0 && close > open) {
				String name = value.substring(0, open).strip().replaceAll("^\"|\"$", "");
				return new Sender(name.isEmpty() ? null : name, value.substring(open + 1, close).strip());
			}
			return new Sender(null, value);
		}
	}

	private static final int MAX_ERROR_TEXT = 200;
	private static final String USER_AGENT = "Vyzzu/1.0 (e-mail do sistema)";

	private final Provider provider;
	private final URI endpoint;
	private final String apiKey;
	private final String from;
	private final Sender sender;
	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	/** {@code endpoint} só é informado nos testes (um servidor local); em produção vale o endereço oficial do provedor. */
	public ApiEmailSender(Provider provider, String endpoint, String apiKey, String from) {
		this.provider = provider;
		this.endpoint = URI.create(endpoint == null || endpoint.isBlank() ? provider.endpoint : endpoint.strip());
		this.apiKey = apiKey.strip();
		this.from = from.strip();
		this.sender = Sender.parse(from);
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public void send(Email email) {
		HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT);
		if (provider == Provider.RESEND) {
			request.header("Authorization", "Bearer " + apiKey);
		} else {
			request.header("api-key", apiKey);
		}
		try {
			HttpResponse<String> response = client.send(request.POST(HttpRequest.BodyPublishers.ofString(body(email))).build(),
					HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (status < 200 || status >= 300) {
				throw new IllegalStateException("Falha no envio pela API " + provider.name().toLowerCase(Locale.ROOT) + ": HTTP " + status + " " + shorten(response.body()));
			}
		} catch (IOException e) {
			throw new IllegalStateException("Falha no envio pela API " + provider.name().toLowerCase(Locale.ROOT) + ": " + e.getClass().getSimpleName(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Envio interrompido", e);
		}
	}

	private String body(Email email) throws JsonProcessingException {
		Map<String, Object> payload;
		if (provider == Provider.RESEND) {
			payload = Map.of("from", from, "to", List.of(email.to()), "subject", email.subject(), "text", email.body());
		} else {
			Map<String, Object> origin = sender.name() == null ? Map.of("email", sender.address()) : Map.of("name", sender.name(), "email", sender.address());
			payload = Map.of("sender", origin, "to", List.of(Map.of("email", email.to())), "subject", email.subject(), "textContent", email.body());
		}
		return json.writeValueAsString(payload);
	}

	private static String shorten(String text) {
		if (text == null) {
			return "";
		}
		String flat = text.replaceAll("\\s+", " ").strip();
		return flat.length() <= MAX_ERROR_TEXT ? flat : flat.substring(0, MAX_ERROR_TEXT) + "…";
	}
}
