package com.digitalsignage.backend;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.digitalsignage.backend.widget.WidgetHttp;

/** Os testes nunca falam com a internet: os provedores de clima, cotações e notícias respondem o que cada teste combinar. */
@TestConfiguration
public class TestWidgetHttpConfig {

	@Bean
	@Primary
	public StubWidgetHttp stubWidgetHttp() {
		return new StubWidgetHttp();
	}

	public static class StubWidgetHttp implements WidgetHttp {

		/** Trecho do endereço -> resposta. O primeiro trecho contido no endereço vence. */
		private final Map<String, Response> routes = new LinkedHashMap<>();
		private final List<String> calls = new ArrayList<>();
		private final List<String> conditions = new ArrayList<>();
		private boolean failAll;

		public void reset() {
			routes.clear();
			calls.clear();
			conditions.clear();
			failAll = false;
		}

		public void on(String urlPart, String body) {
			on(urlPart, 200, body.getBytes(StandardCharsets.UTF_8), "application/json");
		}

		public void on(String urlPart, int status, byte[] body, String contentType) {
			routes.put(urlPart, new Response(status, body, contentType));
		}

		/** Como {@link #on(String, String)}, com os cabeçalhos {@code Last-Modified} e {@code Expires} do provedor. */
		public void on(String urlPart, String body, String lastModified, String expires) {
			routes.put(urlPart, new Response(200, body.getBytes(StandardCharsets.UTF_8), "application/json", lastModified, expires));
		}

		public void failEverything(boolean value) {
			this.failAll = value;
		}

		public List<String> calls() {
			return calls;
		}

		/** O {@code If-Modified-Since} de cada consulta condicional (nulo na primeira consulta). */
		public List<String> conditions() {
			return conditions;
		}

		public long callsTo(String urlPart) {
			return calls.stream().filter(call -> call.contains(urlPart)).count();
		}

		/** Responde 304 quando a pergunta traz o mesmo {@code Last-Modified} que a rota guarda, como o MET Norway faz. */
		@Override
		public Response get(URI uri, boolean publicOnly, String ifModifiedSince) throws IOException {
			Response response = get(uri, publicOnly);
			conditions.add(ifModifiedSince);
			if (ifModifiedSince != null && ifModifiedSince.equals(response.lastModified())) {
				return new Response(304, new byte[0], null, response.lastModified(), response.expires());
			}
			return response;
		}

		@Override
		public Response get(URI uri, boolean publicOnly) throws IOException {
			calls.add(uri.toString());
			if (failAll) {
				throw new IOException("sem rede");
			}
			for (Map.Entry<String, Response> route : routes.entrySet()) {
				if (uri.toString().contains(route.getKey())) {
					return route.getValue();
				}
			}
			throw new IOException("sem rota combinada para " + uri);
		}
	}

}
