package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.monitoring.MonitoringService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * O teste mais importante da fase SaaS: dois clientes na mesma instalação não enxergam nem tocam nos dados um do
 * outro, por nenhum caminho (listas, leitura por id, edição, exclusão, referência cruzada, TV, relatórios).
 */
class TenantIsolationTest extends AbstractApiTest {

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private MonitoringService monitoring;

	/** Um segundo cliente com um administrador. */
	private record Other(long tenantId, String token) {
	}

	private long newTenant(String name, String planCode) {
		return jdbc.queryForObject(
				"INSERT INTO tenants (name, slug, plan_id, status) VALUES (?, ?, (SELECT id FROM plans WHERE code = ?), 'ACTIVE') RETURNING id",
				Long.class, name, name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID().toString().substring(0, 6), planCode);
	}

	private void newUser(long tenantId, String email, String role, String password) {
		jdbc.update("INSERT INTO users (name, email, password_hash, tenant_id, role, active) VALUES (?, ?, ?, ?, ?, true)",
				email, email, encoder.encode(password), tenantId, role);
	}

	private String login(String email, String password) throws Exception {
		String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("token").asText();
	}

	private Other otherTenant(String name) throws Exception {
		long tenant = newTenant(name, "interno");
		String email = "admin-" + tenant + "@teste.com";
		newUser(tenant, email, "ADMIN", "senha-forte-1");
		return new Other(tenant, login(email, "senha-forte-1"));
	}

	private MockHttpServletRequestBuilder as(Other other, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + other.token());
	}

	private long uploadAs(Other other, String name) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", name + ".png", "image/png", ("png-" + name).getBytes());
		return idOf(mvc.perform(as(other, multipart("/api/v1/media").file(file))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long createAs(Other other, String path, Map<String, Object> body) throws Exception {
		return idOf(mvc.perform(as(other, post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Uma TV de verdade: registra pelo Player e ativa no painel do cliente indicado (null = o cliente original). */
	private String activeTv(String name, Other owner) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.6.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		MockHttpServletRequestBuilder activate = post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)));
		mvc.perform(owner == null ? asAdmin(activate) : as(owner, activate)).andExpect(status().isOk());
		return token;
	}

	private long deviceId(String name) {
		return jdbc.queryForObject("SELECT id FROM devices WHERE name = ?", Long.class, name);
	}

	private int rows(String table, long tenantId) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenantId);
	}

	// ---------- listas ----------

	@Test
	void anotherClientSeesNoneOfMyDataInAnyList() throws Exception {
		Other b = otherTenant("Cliente B");
		long media = uploadPng("a-media");
		long playlist = createPlaylist("A playlist", List.of(media));
		createAs(b, "/api/v1/locations", Map.of("name", "do B")); // dado do B, para provar que ele vê o que é dele
		mvc.perform(asAdmin(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Loja do A\"}")));
		mvc.perform(asAdmin(post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Grupo do A\"}")));
		mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Campanha do A", "playlistId", playlist, "targets", List.of(Map.of("type", "ALL")))))));
		mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"principal-lateral\"}")));
		activeTv("TV do A", null);

		for (String path : List.of("/media", "/playlists", "/groups", "/campaigns", "/layouts", "/devices", "/alerts")) {
			mvc.perform(as(b, get("/api/v1" + path))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		}
		mvc.perform(as(b, get("/api/v1/locations"))).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("do B"));
		mvc.perform(as(b, get("/api/v1/summary")))
				.andExpect(jsonPath("$.devices.total").value(0))
				.andExpect(jsonPath("$.media").value(0))
				.andExpect(jsonPath("$.playlists").value(0))
				.andExpect(jsonPath("$.groups").value(0))
				.andExpect(jsonPath("$.campaigns.total").value(0));
		for (String path : List.of("/reports/inventory", "/reports/events", "/reports/uptime", "/reports/plays")) {
			String body = mvc.perform(as(b, get("/api/v1" + path))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			JsonNode node = json.readTree(body);
			assertTrue(node.isArray() ? node.isEmpty() : (node.has("devices") ? node.get("devices").isEmpty() : node.get("rows").isEmpty()), path + " -> " + body);
		}
		// e o A continua vendo tudo o que é dele
		mvc.perform(asAdmin(get("/api/v1/devices"))).andExpect(jsonPath("$", hasSize(1)));
	}

	// ---------- leitura, edição e exclusão por id ----------

	@Test
	void anotherClientCannotReadChangeOrDeleteMyRecordsByGuessingTheirIds() throws Exception {
		Other b = otherTenant("Cliente B");
		long media = uploadPng("a-media");
		long playlist = createPlaylist("A playlist", List.of(media));
		long location = idOf(mvc.perform(asAdmin(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Loja do A\"}"))).andReturn().getResponse().getContentAsString());
		long group = idOf(mvc.perform(asAdmin(post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Grupo do A\"}"))).andReturn().getResponse().getContentAsString());
		long campaign = idOf(mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Campanha do A", "playlistId", playlist, "targets", List.of(Map.of("type", "ALL"))))))).andReturn().getResponse().getContentAsString());
		long layout = idOf(mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"principal-lateral\"}"))).andReturn().getResponse().getContentAsString());
		activeTv("TV do A", null);
		long tv = deviceId("TV do A");

		String[] gets = { "/media/" + media, "/media/" + media + "/url", "/playlists/" + playlist, "/locations/" + location, "/groups/" + group,
				"/layouts/" + layout, "/devices/" + tv, "/devices/" + tv + "/config", "/devices/" + tv + "/events" };
		for (String path : gets) {
			mvc.perform(as(b, get("/api/v1" + path))).andExpect(status().isNotFound());
		}
		mvc.perform(as(b, get("/api/v1/media/" + media + "/file"))).andExpect(status().isNotFound());
		mvc.perform(as(b, get("/api/v1/campaigns/preview?deviceId=" + tv))).andExpect(status().isNotFound());

		String body = json.writeValueAsString(Map.of("name", "invadido"));
		mvc.perform(as(b, put("/api/v1/locations/" + location).contentType(MediaType.APPLICATION_JSON).content(body))).andExpect(status().isNotFound());
		mvc.perform(as(b, put("/api/v1/groups/" + group).contentType(MediaType.APPLICATION_JSON).content(body))).andExpect(status().isNotFound());
		mvc.perform(as(b, put("/api/v1/playlists/" + playlist).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "invadido", "items", List.of()))))).andExpect(status().isNotFound());
		mvc.perform(as(b, put("/api/v1/devices/" + tv).contentType(MediaType.APPLICATION_JSON).content(body))).andExpect(status().isNotFound());
		mvc.perform(as(b, put("/api/v1/layouts/" + layout).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "invadido", "zones", List.of(Map.of("name", "P", "x", 0, "y", 0, "width", 100, "height", 100, "main", true)))))))
				.andExpect(status().isNotFound());

		mvc.perform(as(b, delete("/api/v1/media/" + media))).andExpect(status().isNotFound());
		mvc.perform(as(b, delete("/api/v1/playlists/" + playlist))).andExpect(status().isNotFound());
		mvc.perform(as(b, delete("/api/v1/locations/" + location))).andExpect(status().isNotFound());
		mvc.perform(as(b, delete("/api/v1/groups/" + group))).andExpect(status().isNotFound());
		mvc.perform(as(b, delete("/api/v1/campaigns/" + campaign))).andExpect(status().isNotFound());
		mvc.perform(as(b, delete("/api/v1/layouts/" + layout))).andExpect(status().isNotFound());
		mvc.perform(as(b, delete("/api/v1/devices/" + tv))).andExpect(status().isNotFound());
		mvc.perform(as(b, delete("/api/v1/devices/" + tv + "/playlist"))).andExpect(status().isNotFound());
		mvc.perform(as(b, post("/api/v1/devices/" + tv + "/update-check"))).andExpect(status().isNotFound());

		// nada do A foi alterado
		assertEquals(1, rows("devices", DEFAULT_TENANT));
		assertEquals(1, rows("media", DEFAULT_TENANT));
		assertEquals(1, rows("playlists", DEFAULT_TENANT));
		assertEquals("Loja do A", jdbc.queryForObject("SELECT name FROM locations WHERE id = ?", String.class, location));
		assertEquals("A playlist", jdbc.queryForObject("SELECT name FROM playlists WHERE id = ?", String.class, playlist));
	}

	// ---------- referências cruzadas ----------

	@Test
	void anotherClientCannotBuildOnMyRecords() throws Exception {
		Other b = otherTenant("Cliente B");
		long aMedia = uploadPng("a-media");
		long aPlaylist = createPlaylist("A playlist", List.of(aMedia));
		long aLocation = idOf(mvc.perform(asAdmin(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Loja do A\"}"))).andReturn().getResponse().getContentAsString());
		activeTv("TV do A", null);
		long aTv = deviceId("TV do A");
		activeTv("TV do B", b);
		long bTv = deviceId("TV do B");

		// playlist do B com mídia do A
		mvc.perform(as(b, post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "x", "items", List.of(Map.of("mediaId", aMedia)))))))
				.andExpect(status().isNotFound());
		// TV do B com playlist, local e layout do A
		mvc.perform(as(b, put("/api/v1/devices/" + bTv).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "TV do B", "currentPlaylistId", aPlaylist)))))
				.andExpect(status().isNotFound());
		mvc.perform(as(b, put("/api/v1/devices/" + bTv).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "TV do B", "locationId", aLocation)))))
				.andExpect(status().isNotFound());
		// campanha do B apontando para a playlist do A, ou para a TV do A
		mvc.perform(as(b, post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "c", "playlistId", aPlaylist, "targets", List.of(Map.of("type", "ALL")))))))
				.andExpect(status().isNotFound());
		long bMedia = uploadAs(b, "b-media");
		long bPlaylist = idOf(mvc.perform(as(b, post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "B playlist", "items", List.of(Map.of("mediaId", bMedia)))))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mvc.perform(as(b, post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "c", "playlistId", bPlaylist, "targets", List.of(Map.of("type", "DEVICE", "id", aTv)))))))
				.andExpect(status().isNotFound());
		// layout do B com playlist do A numa zona
		mvc.perform(as(b, post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "l", "zones", List.of(
						Map.of("name", "P", "x", 0, "y", 0, "width", 70, "height", 100, "main", true),
						Map.of("name", "L", "x", 70, "y", 0, "width", 30, "height", 100, "main", false, "playlistId", aPlaylist)))))))
				.andExpect(status().isNotFound());
		// ação em massa sobre a TV do A
		mvc.perform(as(b, post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("action", "DISABLE", "deviceIds", List.of(aTv))))))
				.andExpect(status().isNotFound());
		assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM devices WHERE id = ?", String.class, aTv));
	}

	// ---------- nomes ----------

	@Test
	void twoClientsMayUseTheSameNames() throws Exception {
		Other b = otherTenant("Cliente B");
		for (String path : List.of("/locations", "/groups")) {
			mvc.perform(asAdmin(post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Centro\"}"))).andExpect(status().isCreated());
			mvc.perform(as(b, post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Centro\"}"))).andExpect(status().isCreated());
			mvc.perform(as(b, post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"centro\"}"))).andExpect(status().isConflict());
		}
		mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"duas-metades\"}"))).andExpect(status().isCreated());
		mvc.perform(as(b, post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"duas-metades\"}"))).andExpect(status().isCreated());
	}

	// ---------- TVs ----------

	@Test
	void aTvBelongsToTheClientThatTypedItsCode() throws Exception {
		Other b = otherTenant("Cliente B");
		String token = activeTv("TV do B", b);

		assertEquals(1, rows("devices", b.tenantId()));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM devices WHERE name = 'TV do B' AND tenant_id <> ?", Integer.class, b.tenantId()));
		mvc.perform(asAdmin(get("/api/v1/devices"))).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(as(b, get("/api/v1/devices"))).andExpect(jsonPath("$", hasSize(1)));
		// e o código só vale uma vez: o cliente original não consegue "roubar" nem reativar a TV
		mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.6.0"))))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void aPendingTvIsInNobodysListUntilSomeoneActivatesIt() throws Exception {
		Other b = otherTenant("Cliente B");
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String code = json.readTree(mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.6.0")))).andReturn().getResponse().getContentAsString()).get("activationCode").asText();

		mvc.perform(asAdmin(get("/api/v1/devices"))).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(as(b, get("/api/v1/devices"))).andExpect(jsonPath("$", hasSize(0)));
		assertEquals(1, rows("devices", 1)); // na reserva

		mvc.perform(as(b, post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", "TV nova")))))
				.andExpect(status().isOk());
		mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", "outra vez")))))
				.andExpect(status().isNotFound());
		assertEquals(0, rows("devices", 1));
		assertEquals(1, rows("devices", b.tenantId()));
	}

	@Test
	void aTvOnlyGetsItsOwnClientsContent() throws Exception {
		Other b = otherTenant("Cliente B");
		long aMedia = uploadPng("a-media");
		long aPlaylist = createPlaylist("A playlist", List.of(aMedia));
		String aToken = activeTv("TV do A", null);
		assignPlaylist(deviceId("TV do A"), "TV do A", aPlaylist);
		String bToken = activeTv("TV do B", b);
		long bMedia = uploadAs(b, "b-media");
		long bPlaylist = idOf(mvc.perform(as(b, post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "B playlist", "items", List.of(Map.of("mediaId", bMedia)))))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mvc.perform(as(b, put("/api/v1/devices/" + deviceId("TV do B")).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV do B", "currentPlaylistId", bPlaylist))))).andExpect(status().isOk());

		JsonNode aConfig = json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", aToken)).andReturn().getResponse().getContentAsString());
		JsonNode bConfig = json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", bToken)).andReturn().getResponse().getContentAsString());
		assertEquals(aPlaylist, aConfig.get("playlistId").asLong());
		assertEquals(bPlaylist, bConfig.get("playlistId").asLong());

		// cada TV baixa só as mídias do próprio cliente
		mvc.perform(get("/api/v1/media/" + aMedia + "/file").header("X-Device-Token", aToken)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/media/" + aMedia + "/file").header("X-Device-Token", bToken)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/media/" + bMedia + "/file").header("X-Device-Token", aToken)).andExpect(status().isNotFound());
	}

	@Test
	void aTvCannotClaimPlaysOfAnotherClientsMedia() throws Exception {
		Other b = otherTenant("Cliente B");
		long aMedia = uploadPng("a-media");
		String bToken = activeTv("TV do B", b);
		long bMedia = uploadAs(b, "b-media");

		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", bToken).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playerVersion", "0.6.0", "configVersion", 0, "plays", List.of(
						Map.of("mediaId", aMedia, "count", 5, "seconds", 50), Map.of("mediaId", bMedia, "count", 2, "seconds", 20))))))
				.andExpect(status().isOk());

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM media_plays WHERE media_id = ?", Integer.class, aMedia));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM media_plays WHERE media_id = ?", Integer.class, bMedia));
		mvc.perform(as(b, get("/api/v1/reports/plays"))).andExpect(jsonPath("$.totalPlays").value(2));
		mvc.perform(asAdmin(get("/api/v1/reports/plays"))).andExpect(jsonPath("$.totalPlays").value(0));
	}

	// ---------- monitoramento ----------

	@Test
	void monitoringKeepsEachClientsAlertsSettingsAndWebhookApart() throws Exception {
		Other b = otherTenant("Cliente B");
		clock.freeze("2026-11-05T12:00:00Z");
		String aToken = activeTv("TV do A", null);
		String bToken = activeTv("TV do B", b);
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", bToken).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.6.0\",\"configVersion\":0}")).andExpect(status().isOk());
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", aToken).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.6.0\",\"configVersion\":0}")).andExpect(status().isOk());
		mvc.perform(as(b, put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"offlineAfterMinutes\": 3}"))).andExpect(status().isOk());
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"webhookUrl\": \"http://127.0.0.1:9/segredo-do-a\"}"))).andExpect(status().isOk());

		clock.freeze("2026-11-05T12:30:00Z");
		inTenant(DEFAULT_TENANT, () -> monitoring.evaluate(clock.instant()));
		inTenant(b.tenantId(), () -> monitoring.evaluate(clock.instant()));

		// as duas TVs ficaram 30 min caladas: cada alerta aparece só para o dono, e cada cliente tem o seu limite
		mvc.perform(asAdmin(get("/api/v1/alerts"))).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].deviceName").value("TV do A"));
		mvc.perform(as(b, get("/api/v1/alerts"))).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].deviceName").value("TV do B"));
		mvc.perform(asAdmin(get("/api/v1/alerts/settings"))).andExpect(jsonPath("$.offlineAfterMinutes").value(10)).andExpect(jsonPath("$.webhookConfigured").value(true));
		mvc.perform(as(b, get("/api/v1/alerts/settings"))).andExpect(jsonPath("$.offlineAfterMinutes").value(3)).andExpect(jsonPath("$.webhookConfigured").value(false));
		mvc.perform(as(b, get("/api/v1/summary"))).andExpect(jsonPath("$.openAlerts").value(1));
		mvc.perform(asAdmin(get("/api/v1/summary"))).andExpect(jsonPath("$.openAlerts").value(1));
	}

	// ---------- quem escolhe o cliente ----------

	@Test
	void theTenantHeaderIsOnlyHonouredForThePlatformOperator() throws Exception {
		Other b = otherTenant("Cliente B");
		activeTv("TV do B", b);

		// o operador da plataforma (o administrador dos testes) pode entrar no cliente B
		mvc.perform(asAdmin(get("/api/v1/devices")).header("X-Tenant-Id", String.valueOf(b.tenantId()))).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(asAdmin(get("/api/v1/devices"))).andExpect(jsonPath("$", hasSize(0)));
		// um id que não existe, a reserva ou lixo: ignora e fica no cliente do próprio usuário
		mvc.perform(asAdmin(get("/api/v1/devices")).header("X-Tenant-Id", "999999")).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(asAdmin(get("/api/v1/devices")).header("X-Tenant-Id", "1")).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(asAdmin(get("/api/v1/devices")).header("X-Tenant-Id", "abc")).andExpect(jsonPath("$", hasSize(0)));
		// um administrador comum não escolhe cliente: o header é ignorado
		activeTv("TV do A", null);
		mvc.perform(as(b, get("/api/v1/devices")).header("X-Tenant-Id", String.valueOf(DEFAULT_TENANT))).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("TV do B"));
	}

	@Test
	void anInactiveUserIsLockedOutAtOnceEvenWithAValidToken() throws Exception {
		Other b = otherTenant("Cliente B");
		mvc.perform(as(b, get("/api/v1/devices"))).andExpect(status().isOk());

		jdbc.update("UPDATE users SET active = false WHERE tenant_id = ?", b.tenantId());

		mvc.perform(as(b, get("/api/v1/devices"))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", "admin-" + b.tenantId() + "@teste.com", "password", "senha-forte-1"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void theDataStaysSeparatedInTheDatabaseToo() throws Exception {
		Other b = otherTenant("Cliente B");
		uploadPng("a-media");
		uploadAs(b, "b-media");
		activeTv("TV do A", null);
		activeTv("TV do B", b);

		for (String table : List.of("media", "devices", "device_events")) {
			assertEquals(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class),
					rows(table, DEFAULT_TENANT) + rows(table, b.tenantId()) + rows(table, 1), table + ": toda linha tem dono");
		}
		assertEquals(1, rows("media", DEFAULT_TENANT));
		assertEquals(1, rows("media", b.tenantId()));
		// os eventos de ativação de cada TV ficaram cada um no seu cliente
		assertEquals(1, rows("device_events", DEFAULT_TENANT));
		assertEquals(1, rows("device_events", b.tenantId()));
	}

}
