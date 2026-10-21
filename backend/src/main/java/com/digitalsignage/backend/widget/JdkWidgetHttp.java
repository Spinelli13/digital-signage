package com.digitalsignage.backend.widget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Component;

import com.digitalsignage.backend.monitoring.WebhookUrlPolicy;

/** A implementação de verdade: HttpClient do Java, com tempo curto, tamanho máximo, gzip e redirecionamentos conferidos um a um. */
@Component
public class JdkWidgetHttp implements WidgetHttp {

	private static final int MAX_BYTES = 3 * 1024 * 1024;
	private static final int MAX_REDIRECTS = 3;
	private static final String USER_AGENT = "Vyzzu/1.0 (digital signage; +https://signage-painel.pages.dev)";

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER) // cada destino é conferido antes de seguir
			.build();
	private final WebhookUrlPolicy urls;

	public JdkWidgetHttp(WebhookUrlPolicy urls) {
		this.urls = urls;
	}

	@Override
	public Response get(URI uri, boolean publicOnly) throws IOException {
		URI current = uri;
		for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			if (publicOnly || hop > 0) { // um redirecionamento nunca pode levar à rede interna, nem num feed conhecido
				urls.validate(current.toString(), "feed de notícias");
			}
			HttpRequest request = HttpRequest.newBuilder(current)
					.timeout(Duration.ofSeconds(8))
					.header("User-Agent", USER_AGENT)
					.header("Accept", "application/json, application/rss+xml, application/atom+xml, application/xml, text/xml, */*;q=0.5")
					.header("Accept-Encoding", "gzip")
					.GET()
					.build();
			HttpResponse<InputStream> response;
			try {
				response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Consulta interrompida");
			}
			int status = response.statusCode();
			if (status >= 300 && status < 400) {
				String location = response.headers().firstValue("Location").orElse(null);
				response.body().close();
				if (location == null) {
					throw new IOException("Redirecionamento sem destino");
				}
				current = current.resolve(location);
				continue;
			}
			try (InputStream raw = response.body()) {
				boolean gzip = response.headers().firstValue("Content-Encoding").map(value -> value.toLowerCase().contains("gzip")).orElse(false);
				InputStream in = gzip ? new GZIPInputStream(raw) : raw;
				return new Response(status, readLimited(in), response.headers().firstValue("Content-Type").orElse(null));
			}
		}
		throw new IOException("Redirecionamentos demais");
	}

	private static byte[] readLimited(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[16 * 1024];
		int read;
		while ((read = in.read(buffer)) >= 0) {
			out.write(buffer, 0, read);
			if (out.size() > MAX_BYTES) {
				throw new IOException("A resposta é grande demais");
			}
		}
		return out.toByteArray();
	}

}
