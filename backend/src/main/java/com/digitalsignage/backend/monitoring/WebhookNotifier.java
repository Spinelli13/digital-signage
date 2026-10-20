package com.digitalsignage.backend.monitoring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Envia o aviso para o webhook configurado (Slack, Discord e compatíveis). Nunca lança: devolve o resultado. */
@Component
public class WebhookNotifier {

	private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);

	public record Result(boolean ok, int status, String error) {

		static Result success(int status) {
			return new Result(true, status, null);
		}

		static Result failure(int status, String error) {
			return new Result(false, status, error);
		}
	}

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER) // um redirecionamento poderia levar à rede interna
			.build();
	private final ObjectMapper json;
	private final WebhookUrlPolicy policy;

	public WebhookNotifier(ObjectMapper json, WebhookUrlPolicy policy) {
		this.json = json;
		this.policy = policy;
	}

	/**
	 * O texto vai em "text" (Slack e outros) e em "content" (Discord); o resto são dados estruturados para quem
	 * queira tratar o evento por conta própria.
	 */
	public Result send(String url, String text, Map<String, Object> data) {
		URI uri;
		try {
			uri = policy.validate(url);
		} catch (IllegalArgumentException e) {
			return Result.failure(0, e.getMessage());
		}
		try {
			Map<String, Object> payload = new java.util.LinkedHashMap<>(data);
			payload.put("text", text);
			payload.put("content", text);
			HttpRequest request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(8))
					.header("Content-Type", "application/json; charset=utf-8")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 == 2) {
				return Result.success(response.statusCode());
			}
			return Result.failure(response.statusCode(), "O webhook respondeu HTTP " + response.statusCode());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Result.failure(0, "Envio interrompido");
		} catch (Exception e) {
			log.warn("Falha ao enviar o webhook de alertas: {}", e.toString());
			return Result.failure(0, "Não foi possível enviar ao webhook: " + e.getClass().getSimpleName());
		}
	}

}
