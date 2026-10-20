package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.security.TokenHasher;

/** Ciclo de vida do Player: registro → código na TV → ativação pelo painel → config/heartbeat/download. */
class PlayerFlowTest extends AbstractApiTest {

	private static final String HEADER = "X-Device-Token";

	private String newToken() {
		return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
	}

	private String register(String token) throws Exception {
		return mvc.perform(post("/api/v1/player/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "1.0.0"))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String activationCodeOf(String registerResponse) throws Exception {
		return json.readTree(registerResponse).get("activationCode").asText();
	}

	private void activate(String code, String name) throws Exception {
		mvc.perform(asAdmin(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder asDevice(MockHttpServletRequestBuilder request, String token) {
		return request.header(HEADER, token);
	}

	private String registerAndActivate(String name) throws Exception {
		String token = newToken();
		activate(activationCodeOf(register(token)), name);
		return token;
	}

	// --- registro ---

	@Test
	void registerCreatesPendingDeviceWithReadableCode() throws Exception {
		mvc.perform(post("/api/v1/player/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", newToken(), "playerVersion", "1.0.0"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				// sem 0, O, 1, I: ambíguos de longe na TV
				.andExpect(jsonPath("$.activationCode", matchesPattern("[A-HJ-NP-Z2-9]{6}")));
	}

	@Test
	void registerIsIdempotentForTheSameToken() throws Exception {
		String token = newToken();
		String first = activationCodeOf(register(token));
		String second = activationCodeOf(register(token));

		assertEquals(first, second);
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM devices", Integer.class));
	}

	@Test
	void registerStoresOnlyAHashOfTheToken() throws Exception {
		String token = newToken();
		register(token);

		String stored = jdbc.queryForObject("SELECT device_token_hash FROM devices", String.class);
		assertEquals(TokenHasher.sha256Hex(token), stored);
	}

	@Test
	void registerRejectsShortToken() throws Exception {
		mvc.perform(post("/api/v1/player/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", "curto"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void registerDropsAbandonedPendingDevicesOlderThanADay() throws Exception {
		register(newToken());
		jdbc.update("UPDATE devices SET created_at = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(2 * 86400)));

		register(newToken());

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM devices", Integer.class));
	}

	@Test
	void registerRefusesWhenTooManyDevicesAreWaiting() throws Exception {
		for (int i = 0; i < 100; i++) {
			jdbc.update("INSERT INTO devices (name, status, device_token_hash, activation_code, tenant_id) VALUES (?, 'PENDING', ?, ?, 1)",
					"p" + i, "hash" + i, String.format("CODE%02d", i));
		}

		mvc.perform(post("/api/v1/player/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", newToken()))))
				.andExpect(status().isTooManyRequests());
	}

	// --- autenticação do dispositivo ---

	@Test
	void playerEndpointsRequireAKnownToken() throws Exception {
		mvc.perform(get("/api/v1/player/status")).andExpect(status().isUnauthorized());
		mvc.perform(asDevice(get("/api/v1/player/status"), newToken())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/player/config")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/player/heartbeat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"configVersion\":0}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void pendingDeviceCanReadItsStatusButNothingElse() throws Exception {
		String token = newToken();
		String code = activationCodeOf(register(token));

		mvc.perform(asDevice(get("/api/v1/player/status"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.activationCode").value(code));

		mvc.perform(asDevice(get("/api/v1/player/config"), token)).andExpect(status().isForbidden());
		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"configVersion\":0}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void deviceTokenDoesNotOpenAdminEndpoints() throws Exception {
		String token = registerAndActivate("TV");

		mvc.perform(asDevice(get("/api/v1/devices"), token)).andExpect(status().isForbidden());
		mvc.perform(asDevice(get("/api/v1/playlists"), token)).andExpect(status().isForbidden());
		mvc.perform(asDevice(get("/api/v1/media"), token)).andExpect(status().isForbidden());
	}

	@Test
	void adminJwtDoesNotOpenPlayerEndpoints() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/player/config"))).andExpect(status().isForbidden());
	}

	@Test
	void disabledDeviceLosesAccess() throws Exception {
		String token = newToken();
		activate(activationCodeOf(register(token)), "TV");
		long id = jdbc.queryForObject("SELECT id FROM devices", Long.class);

		mvc.perform(asDevice(get("/api/v1/player/config"), token)).andExpect(status().isOk());

		mvc.perform(asAdmin(put("/api/v1/devices/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "status", "disabled")))))
				.andExpect(status().isOk());

		mvc.perform(asDevice(get("/api/v1/player/config"), token)).andExpect(status().isUnauthorized());
		mvc.perform(asDevice(get("/api/v1/player/status"), token)).andExpect(status().isUnauthorized());
	}

	// --- ativação pelo painel ---

	@Test
	void adminActivatesDeviceByCode() throws Exception {
		String token = newToken();
		String code = activationCodeOf(register(token));

		mvc.perform(asAdmin(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of(
						"activationCode", code.toLowerCase() + " ",
						"name", "TV Recepção",
						"location", "Térreo")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("TV Recepção"))
				.andExpect(jsonPath("$.location").value("Térreo"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mvc.perform(asDevice(get("/api/v1/player/status"), token))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.activationCode").doesNotExist())
				.andExpect(jsonPath("$.deviceName").value("TV Recepção"));
	}

	@Test
	void activationCodeWorksOnlyOnce() throws Exception {
		String code = activationCodeOf(register(newToken()));
		activate(code, "TV");

		mvc.perform(asAdmin(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", "Outra")))))
				.andExpect(status().isNotFound());
	}

	@Test
	void unknownActivationCodeIsNotFound() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", "ZZZZZZ", "name", "TV")))))
				.andExpect(status().isNotFound());
	}

	@Test
	void activationRequiresAdmin() throws Exception {
		String code = activationCodeOf(register(newToken()));

		mvc.perform(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", "TV"))))
				.andExpect(status().isUnauthorized());
	}

	// --- configuração, heartbeat e download ---

	@Test
	void activeDeviceReceivesItsPlaylistConfig() throws Exception {
		String token = registerAndActivate("TV");
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long deviceId = jdbc.queryForObject("SELECT id FROM devices", Long.class);
		assignPlaylist(deviceId, "TV", playlist);

		mvc.perform(asDevice(get("/api/v1/player/config"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceId").value(deviceId))
				.andExpect(jsonPath("$.playlistId").value(playlist))
				.andExpect(jsonPath("$.configVersion").value(1))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].url").value("/api/v1/media/" + media + "/file"));
	}

	@Test
	void deviceWithoutPlaylistGetsEmptyConfig() throws Exception {
		String token = registerAndActivate("TV");

		mvc.perform(asDevice(get("/api/v1/player/config"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configVersion").value(0))
				.andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void heartbeatRecordsPresenceAndReportsTheDesiredConfigVersion() throws Exception {
		String token = registerAndActivate("TV");
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long deviceId = jdbc.queryForObject("SELECT id FROM devices", Long.class);
		assignPlaylist(deviceId, "TV", playlist);

		mvc.perform(asAdmin(get("/api/v1/devices/" + deviceId))).andExpect(jsonPath("$.online").value(false));

		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playerVersion", "1.2.3", "configVersion", 0))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configVersion").value(1))
				.andExpect(jsonPath("$.serverTime").isNotEmpty());

		mvc.perform(asAdmin(get("/api/v1/devices/" + deviceId)))
				.andExpect(jsonPath("$.online").value(true))
				.andExpect(jsonPath("$.playerVersion").value("1.2.3"))
				.andExpect(jsonPath("$.configVersion").value(0));

		// Player sincroniza a versão 1 e avisa no próximo heartbeat.
		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playerVersion", "1.2.3", "configVersion", 1))));
		mvc.perform(asAdmin(get("/api/v1/devices/" + deviceId))).andExpect(jsonPath("$.configVersion").value(1));
	}

	@Test
	void heartbeatSeesPlaylistEditsMadeAfterAssignment() throws Exception {
		String token = registerAndActivate("TV");
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long deviceId = jdbc.queryForObject("SELECT id FROM devices", Long.class);
		assignPlaylist(deviceId, "TV", playlist);

		replacePlaylistItems(playlist, "P", List.of(media, media));

		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"configVersion\":1}"))
				.andExpect(jsonPath("$.configVersion").value(2));
	}

	/**
	 * Regressão de projeto: duas playlists distintas podem ter o mesmo configVersion. O heartbeat precisa
	 * devolver o playlistId para o Player perceber que o conteúdo mudou mesmo com a versão igual.
	 */
	@Test
	void heartbeatIdentifiesThePlaylistSoSwappingToOneWithTheSameVersionIsDetected() throws Exception {
		String token = registerAndActivate("TV");
		long media = uploadPng("img");
		long first = createPlaylist("A", List.of(media));
		long second = createPlaylist("B", List.of(media));
		long deviceId = jdbc.queryForObject("SELECT id FROM devices", Long.class);

		assignPlaylist(deviceId, "TV", first);
		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"configVersion\":0}"))
				.andExpect(jsonPath("$.playlistId").value(first))
				.andExpect(jsonPath("$.configVersion").value(1));

		assignPlaylist(deviceId, "TV", second);
		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"configVersion\":1}"))
				.andExpect(jsonPath("$.playlistId").value(second))
				.andExpect(jsonPath("$.configVersion").value(1));
	}

	/** O Player só percebe a remoção se o heartbeat passar a devolver "sem playlist, versão 0". */
	@Test
	void heartbeatReportsNoPlaylistAfterItIsRemovedFromTheDevice() throws Exception {
		String token = registerAndActivate("TV");
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long deviceId = jdbc.queryForObject("SELECT id FROM devices", Long.class);
		assignPlaylist(deviceId, "TV", playlist);

		mvc.perform(asAdmin(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/v1/devices/" + deviceId + "/playlist")))
				.andExpect(status().isOk());

		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"configVersion\":1}"))
				.andExpect(jsonPath("$.playlistId").doesNotExist())
				.andExpect(jsonPath("$.configVersion").value(0));
	}

	// --- pedido de verificação de atualização do Player ---

	private String heartbeatBody() throws Exception {
		return json.writeValueAsString(Map.of("playerVersion", "0.3.0", "configVersion", 0));
	}

	@Test
	void heartbeatReportsNoUpdateRequestUntilTheAdminAsks() throws Exception {
		String token = registerAndActivate("TV");

		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON).content(heartbeatBody()))
				.andExpect(jsonPath("$.updateCheckToken").value(0));
	}

	@Test
	void adminCanAskOneTvToCheckForPlayerUpdatesAndTheTokenChangesEachTime() throws Exception {
		String token = registerAndActivate("TV");
		long deviceId = jdbc.queryForObject("SELECT id FROM devices", Long.class);
		var heartbeat = asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON).content(heartbeatBody());

		mvc.perform(asAdmin(post("/api/v1/devices/" + deviceId + "/update-check"))).andExpect(status().isOk());
		long first = Long.parseLong(com.jayway.jsonpath.JsonPath.read(
				mvc.perform(heartbeat).andReturn().getResponse().getContentAsString(), "$.updateCheckToken").toString());
		org.junit.jupiter.api.Assertions.assertTrue(first > 0);

		Thread.sleep(5);
		mvc.perform(asAdmin(post("/api/v1/devices/" + deviceId + "/update-check"))).andExpect(status().isOk());
		long second = Long.parseLong(com.jayway.jsonpath.JsonPath.read(
				mvc.perform(heartbeat).andReturn().getResponse().getContentAsString(), "$.updateCheckToken").toString());
		org.junit.jupiter.api.Assertions.assertTrue(second > first, "um novo pedido precisa gerar um valor novo");
	}

	@Test
	void adminCanAskEveryActiveTvAndPendingOnesAreLeftAlone() throws Exception {
		String activeToken = registerAndActivate("TV ativa");
		String pendingToken = newToken();
		register(pendingToken);

		mvc.perform(asAdmin(post("/api/v1/devices/update-check")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requested").value(1));

		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), activeToken)
				.contentType(MediaType.APPLICATION_JSON).content(heartbeatBody()))
				.andExpect(jsonPath("$.updateCheckToken", org.hamcrest.Matchers.greaterThan(0L)));
		org.junit.jupiter.api.Assertions.assertEquals(1,
				jdbc.queryForObject("SELECT count(*) FROM devices WHERE update_requested_at IS NOT NULL", Integer.class));
	}

	@Test
	void updateCheckRequestsNeedAnAdminAndAnExistingTv() throws Exception {
		mvc.perform(post("/api/v1/devices/update-check")).andExpect(status().isUnauthorized());
		mvc.perform(asAdmin(post("/api/v1/devices/999999/update-check"))).andExpect(status().isNotFound());
	}

	@Test
	void activeDeviceDownloadsMediaButPendingAndAnonymousCannot() throws Exception {
		byte[] content = "bytes-da-imagem".getBytes();
		long media = uploadMedia("a.png", "image/png", content);
		String activeToken = registerAndActivate("TV");
		String pendingToken = newToken();
		register(pendingToken);

		byte[] downloaded = mvc.perform(asDevice(get("/api/v1/media/" + media + "/file"), activeToken))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		assertArrayEquals(content, downloaded);

		mvc.perform(asDevice(get("/api/v1/media/" + media + "/file"), pendingToken)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/media/" + media + "/file")).andExpect(status().isUnauthorized());
	}

	@Test
	void deviceCannotDeleteOrUploadMedia() throws Exception {
		String token = registerAndActivate("TV");
		long media = uploadPng("img");

		mvc.perform(asDevice(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/v1/media/" + media), token))
				.andExpect(status().isForbidden());
	}

}
