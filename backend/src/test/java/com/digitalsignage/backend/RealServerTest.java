package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.digitalsignage.backend.security.TokenHasher;

/**
 * MockMvc não passa pelo despacho de erro do contêiner (/error), então não enxerga bugs em que o status
 * real de uma resposta de erro é trocado. Estes testes sobem o Tomcat de verdade.
 *
 * Regressão: sem liberar DispatcherType.ERROR, qualquer 400/403/500 chegava ao cliente como 401 —
 * o que, por exemplo, deslogaria o painel ao enviar um JSON inválido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		AbstractApiTest.configure(registry);
	}

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();

	@BeforeEach
	void clean() {
		jdbc.execute("TRUNCATE playlist_items, device_playlists, devices, playlists, media RESTART IDENTITY CASCADE");
	}

	private HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		for (int i = 0; i < headers.length; i += 2) {
			request.header(headers[i], headers[i + 1]);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private String adminJwt() throws Exception {
		String body = send("POST", "/api/v1/auth/login",
				"{\"email\":\"admin@digitalsignage.local\",\"password\":\"changeme\"}").body();
		return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
	}

	private String activeDeviceToken() {
		String token = "a".repeat(64);
		jdbc.update("INSERT INTO devices (name, status, device_token_hash, tenant_id) VALUES ('TV', 'ACTIVE', ?, 2)",
				TokenHasher.sha256Hex(token));
		return token;
	}

	@Test
	void anonymousRequestIs401() throws Exception {
		assertEquals(401, send("GET", "/api/v1/devices", null).statusCode());
	}

	@Test
	void malformedJsonFromAnAdminIs400NotAMaskedUnauthorized() throws Exception {
		String jwt = adminJwt();

		HttpResponse<String> response = send("POST", "/api/v1/devices", "{isso nao e json", "Authorization", "Bearer " + jwt);

		assertEquals(400, response.statusCode());
	}

	@Test
	void forbiddenIsReportedAs403NotMaskedAs401() throws Exception {
		String deviceToken = activeDeviceToken();
		String jwt = adminJwt();

		// aparelho tentando usar a API do painel
		assertEquals(403, send("GET", "/api/v1/devices", null, "X-Device-Token", deviceToken).statusCode());
		// administrador tentando usar a API do Player
		assertEquals(403, send("GET", "/api/v1/player/config", null, "Authorization", "Bearer " + jwt).statusCode());
	}

	@Test
	void pendingDeviceAskingForConfigIs403() throws Exception {
		String token = "b".repeat(64);
		assertEquals(200, send("POST", "/api/v1/player/register", "{\"deviceToken\":\"" + token + "\"}").statusCode());

		assertEquals(403, send("GET", "/api/v1/player/config", null, "X-Device-Token", token).statusCode());
	}

	@Test
	void notFoundKeepsItsJsonMessage() throws Exception {
		HttpResponse<String> response = send("GET", "/api/v1/devices/424242", null, "Authorization", "Bearer " + adminJwt());

		assertEquals(404, response.statusCode());
		assertTrue(response.body().contains("Dispositivo não encontrado"));
	}

	@Test
	void disabledDeviceGets401SoThePlayerKnowsToStop() throws Exception {
		String token = "c".repeat(64);
		jdbc.update("INSERT INTO devices (name, status, device_token_hash, tenant_id) VALUES ('TV', 'DISABLED', ?, 2)",
				TokenHasher.sha256Hex(token));

		assertEquals(401, send("GET", "/api/v1/player/config", null, "X-Device-Token", token).statusCode());
	}

}
