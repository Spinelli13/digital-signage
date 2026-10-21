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
		private boolean failAll;

		public void reset() {
			routes.clear();
			calls.clear();
			failAll = false;
		}

		public void on(String urlPart, String body) {
			on(urlPart, 200, body.getBytes(StandardCharsets.UTF_8), "application/json");
		}

		public void on(String urlPart, int status, byte[] body, String contentType) {
			routes.put(urlPart, new Response(status, body, contentType));
		}

		public void failEverything(boolean value) {
			this.failAll = value;
		}

		public List<String> calls() {
			return calls;
		}

		public long callsTo(String urlPart) {
			return calls.stream().filter(call -> call.contains(urlPart)).count();
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
