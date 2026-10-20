package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;

/** Layouts (zonas), modelos, TV com layout e a configuração que o Player recebe. */
class LayoutTest extends AbstractApiTest {

	private static Map<String, Object> zone(String name, int x, int y, int w, int h, boolean main, Long playlistId) {
		Map<String, Object> zone = new HashMap<>();
		zone.put("name", name);
		zone.put("x", x);
		zone.put("y", y);
		zone.put("width", w);
		zone.put("height", h);
		zone.put("main", main);
		zone.put("playlistId", playlistId);
		return zone;
	}

	private ResultActions submit(Map<String, Object> body) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private long createLayout(String name, List<Map<String, Object>> zones) throws Exception {
		return idOf(submit(Map.of("name", name, "zones", zones)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Principal 75% + lateral 25% (a lateral com a playlist fixa informada). */
	private long sideLayout(String name, Long sidePlaylist) throws Exception {
		return createLayout(name, List.of(zone("Principal", 0, 0, 75, 100, true, null), zone("Lateral", 75, 0, 25, 100, false, sidePlaylist)));
	}

	private String activeTv(String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.6.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name))))).andExpect(status().isOk());
		return token;
	}

	private long deviceId(String name) {
		return jdbc.queryForObject("SELECT id FROM devices WHERE name = ?", Long.class, name);
	}

	private MockHttpServletRequestBuilder asDevice(MockHttpServletRequestBuilder request, String token) {
		return request.header("X-Device-Token", token);
	}

	private JsonNode heartbeat(String token) throws Exception {
		return json.readTree(mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.6.0\",\"configVersion\":0}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode config(String token) throws Exception {
		return json.readTree(mvc.perform(asDevice(get("/api/v1/player/config"), token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode device(long id) throws Exception {
		return json.readTree(mvc.perform(asAdmin(get("/api/v1/devices/" + id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private void setLayout(long deviceId, String name, long layoutId) throws Exception {
		mvc.perform(asAdmin(put("/api/v1/devices/" + deviceId).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "layoutId", layoutId))))).andExpect(status().isOk());
	}

	// ---------- cadastro ----------

	@Test
	void shipsReadyMadeTemplates() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/layouts/templates")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value("tela-cheia"))
				.andExpect(jsonPath("$[*].id", hasItems("principal-lateral", "principal-rodape", "duas-metades", "quatro-quadrantes")))
				.andExpect(jsonPath("$[1].zones[0].main").value(true));
	}

	@Test
	void createsAndReadsALayoutWithItsZones() throws Exception {
		long promo = createPlaylist("Promos", List.of(uploadPng("promo")));

		long id = sideLayout("Lateral de promoções", promo);

		mvc.perform(asAdmin(get("/api/v1/layouts/" + id)))
				.andExpect(jsonPath("$.name").value("Lateral de promoções"))
				.andExpect(jsonPath("$.configVersion").value(2)) // 1 + a gravação das zonas
				.andExpect(jsonPath("$.deviceCount").value(0))
				.andExpect(jsonPath("$.zones", hasSize(2)))
				.andExpect(jsonPath("$.zones[0].name").value("Principal"))
				.andExpect(jsonPath("$.zones[0].main").value(true))
				.andExpect(jsonPath("$.zones[1].width").value(25))
				.andExpect(jsonPath("$.zones[1].playlistId").value(promo))
				.andExpect(jsonPath("$.zones[1].playlistName").value("Promos"));
	}

	@Test
	void theListIsSortedByNameAndCountsTheTvsThatUseEachLayout() throws Exception {
		long b = sideLayout("Beta", null);
		sideLayout("alfa", null);
		activeTv("TV 1");
		setLayout(deviceId("TV 1"), "TV 1", b);

		mvc.perform(asAdmin(get("/api/v1/layouts")))
				.andExpect(jsonPath("$[*].name", contains("alfa", "Beta")))
				.andExpect(jsonPath("$[0].deviceCount").value(0))
				.andExpect(jsonPath("$[1].deviceCount").value(1));
	}

	@Test
	void refusesInvalidGeometryWithAnExplanation() throws Exception {
		submit(Map.of("name", "Sem principal", "zones", List.of(zone("A", 0, 0, 100, 100, false, null))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Escolha exatamente uma zona principal (a que exibe a playlist da TV)"));
		submit(Map.of("name", "Sobrepostas", "zones", List.of(zone("A", 0, 0, 60, 100, true, null), zone("B", 50, 0, 50, 100, false, null))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("As zonas \"A\" e \"B\" se sobrepõem"));
		submit(Map.of("name", "Fora", "zones", List.of(zone("A", 50, 0, 60, 100, true, null))))
				.andExpect(status().isBadRequest());
		submit(Map.of("name", "Sem zonas", "zones", List.of())).andExpect(status().isBadRequest());
		submit(Map.of("name", " ", "zones", List.of(zone("A", 0, 0, 100, 100, true, null)))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/layouts"))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void aZonePlaylistThatDoesNotExistIsA404() throws Exception {
		submit(Map.of("name", "X", "zones", List.of(zone("Principal", 0, 0, 75, 100, true, null), zone("Lateral", 75, 0, 25, 100, false, 9999L))))
				.andExpect(status().isNotFound());
	}

	@Test
	void layoutNamesAreUniqueIgnoringCase() throws Exception {
		sideLayout("Meu layout", null);

		submit(Map.of("name", "MEU LAYOUT", "zones", List.of(zone("A", 0, 0, 100, 100, true, null))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Já existe um layout chamado \"MEU LAYOUT\""));
	}

	@Test
	void startsFromATemplateWithItsNameOrYours() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"principal-rodape\"}")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Principal + rodapé"))
				.andExpect(jsonPath("$.zones", hasSize(2)))
				.andExpect(jsonPath("$.zones[1].name").value("Rodapé"));
		mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON)
				.content("{\"templateId\":\"principal-rodape\",\"name\":\"Rodapé da loja\"}")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Rodapé da loja"));
		mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"principal-rodape\"}")))
				.andExpect(status().isConflict());
		mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"nao-existe\"}")))
				.andExpect(status().isNotFound());
	}

	@Test
	void updatingReplacesTheZonesAndBumpsTheVersion() throws Exception {
		long id = sideLayout("Layout", null);
		long before = json.readTree(mvc.perform(asAdmin(get("/api/v1/layouts/" + id))).andReturn().getResponse().getContentAsString()).get("configVersion").asLong();

		mvc.perform(asAdmin(put("/api/v1/layouts/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Layout 2", "description", "Duas metades",
				"zones", List.of(zone("Esquerda", 0, 0, 50, 100, true, null), zone("Direita", 50, 0, 50, 100, false, null)))))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Layout 2"))
				.andExpect(jsonPath("$.description").value("Duas metades"))
				.andExpect(jsonPath("$.zones[0].width").value(50))
				.andExpect(jsonPath("$.configVersion").value(before + 1));
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM layout_zones WHERE layout_id = ?", Integer.class, id));
	}

	@Test
	void renamingToAnExistingNameIsRefusedButKeepingYourOwnNameIsFine() throws Exception {
		long a = sideLayout("A", null);
		sideLayout("B", null);

		mvc.perform(asAdmin(put("/api/v1/layouts/" + a).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "b", "zones", List.of(zone("P", 0, 0, 100, 100, true, null)))))))
				.andExpect(status().isConflict());
		mvc.perform(asAdmin(put("/api/v1/layouts/" + a).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "A", "zones", List.of(zone("P", 0, 0, 100, 100, true, null)))))))
				.andExpect(status().isOk());
	}

	@Test
	void aLayoutInUseCannotBeDeletedUntilTheTvsLeaveIt() throws Exception {
		long id = sideLayout("Em uso", null);
		activeTv("TV 1");
		long tv = deviceId("TV 1");
		setLayout(tv, "TV 1", id);

		mvc.perform(asAdmin(delete("/api/v1/layouts/" + id)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("O layout \"Em uso\" está em uso por 1 TV(s). Escolha outro layout (ou tela cheia) para elas antes de excluir."));

		mvc.perform(asAdmin(delete("/api/v1/devices/" + tv + "/layout"))).andExpect(status().isOk()).andExpect(jsonPath("$.layoutId").doesNotExist());
		mvc.perform(asAdmin(delete("/api/v1/layouts/" + id))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/layouts/" + id))).andExpect(status().isNotFound());
	}

	@Test
	void aPlaylistUsedByAZoneCannotBeDeleted() throws Exception {
		long promo = createPlaylist("Promos", List.of(uploadPng("promo")));
		long layout = sideLayout("Com lateral", promo);

		mvc.perform(asAdmin(delete("/api/v1/playlists/" + promo)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A playlist \"Promos\" está em uso pelo layout \"Com lateral\". Troque a playlist da zona ou exclua o layout antes."));

		mvc.perform(asAdmin(delete("/api/v1/layouts/" + layout))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(delete("/api/v1/playlists/" + promo))).andExpect(status().isNoContent());
	}

	@Test
	void layoutsNeedAnAdmin() throws Exception {
		mvc.perform(get("/api/v1/layouts")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/layouts/templates")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
	}

	// ---------- TV com layout ----------

	@Test
	void aTvTakesALayoutAndGoesBackToFullScreen() throws Exception {
		long id = sideLayout("Lateral", null);
		activeTv("TV 1");
		long tv = deviceId("TV 1");

		mvc.perform(asAdmin(put("/api/v1/devices/" + tv).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "TV 1", "layoutId", id)))))
				.andExpect(jsonPath("$.layoutId").value(id))
				.andExpect(jsonPath("$.layoutName").value("Lateral"));
		mvc.perform(asAdmin(put("/api/v1/devices/" + tv).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"TV 1\"}")))
				.andExpect(jsonPath("$.layoutId").value(id)); // ausente = não muda
		mvc.perform(asAdmin(put("/api/v1/devices/" + tv).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"TV 1\",\"layoutId\":99999}")))
				.andExpect(status().isNotFound());

		mvc.perform(asAdmin(delete("/api/v1/devices/" + tv + "/layout"))).andExpect(jsonPath("$.layoutId").doesNotExist());
		mvc.perform(asAdmin(delete("/api/v1/devices/" + tv + "/layout"))).andExpect(status().isOk()); // idempotente
	}

	@Test
	void bulkActionsAssignAndRemoveTheLayout() throws Exception {
		long id = sideLayout("Lateral", null);
		activeTv("TV 1");
		activeTv("TV 2");
		List<Long> ids = List.of(deviceId("TV 1"), deviceId("TV 2"));

		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "ASSIGN_LAYOUT", "deviceIds", ids, "layoutId", id)))))
				.andExpect(jsonPath("$.affected").value(2));
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM devices WHERE layout_id = ?", Integer.class, id));

		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "ASSIGN_LAYOUT", "deviceIds", ids))))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "REMOVE_LAYOUT", "deviceIds", ids)))))
				.andExpect(jsonPath("$.affected").value(2));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM devices WHERE layout_id IS NOT NULL", Integer.class));
	}

	@Test
	void layoutChangesAreOnTheTvHistory() throws Exception {
		long id = sideLayout("Lateral", null);
		activeTv("TV 1");
		long tv = deviceId("TV 1");

		setLayout(tv, "TV 1", id);
		setLayout(tv, "TV 1", id); // sem mudança: não repete
		mvc.perform(asAdmin(delete("/api/v1/devices/" + tv + "/layout")));

		List<String> messages = jdbc.queryForList("SELECT message FROM device_events WHERE device_id = ? AND type = 'LAYOUT_CHANGED' ORDER BY id", String.class, tv);
		assertEquals(List.of("Layout da tela: \"Lateral\"", "Layout da tela removido: a TV voltou para a tela cheia"), messages);
	}

	// ---------- configuração do Player ----------

	@Test
	void withoutALayoutTheConfigIsExactlyWhatItAlwaysWas() throws Exception {
		long playlist = createPlaylist("Base", List.of(uploadPng("a")));
		String token = activeTv("TV 1");
		long tv = deviceId("TV 1");
		assignPlaylist(tv, "TV 1", playlist);

		JsonNode config = config(token);

		assertEquals(1, config.get("items").size());
		assertEquals(0, config.get("zones").size());
		assertEquals(playlist, config.get("playlistId").asLong());
		long version = jdbc.queryForObject("SELECT config_version FROM playlists WHERE id = ?", Long.class, playlist);
		assertEquals(version, config.get("configVersion").asLong());
		assertEquals(version, heartbeat(token).get("configVersion").asLong());
		assertEquals(version, device(tv).get("desiredConfigVersion").asLong());
	}

	@Test
	void withALayoutTheConfigCarriesEveryZoneAndTheTopLevelItemsAreTheMainZone() throws Exception {
		long main = createPlaylist("Principal", List.of(uploadPng("m1"), uploadPng("m2")));
		long side = createPlaylist("Lateral", List.of(uploadPng("s1")));
		long layout = sideLayout("Lateral", side);
		String token = activeTv("TV 1");
		long tv = deviceId("TV 1");
		assignPlaylist(tv, "TV 1", main);
		setLayout(tv, "TV 1", layout);

		JsonNode config = config(token);

		assertEquals(2, config.get("items").size()); // quem não conhece layouts exibe a principal em tela cheia
		assertEquals(main, config.get("playlistId").asLong());
		JsonNode zones = config.get("zones");
		assertEquals(2, zones.size());
		assertEquals("Principal", zones.get(0).get("name").asText());
		assertTrue(zones.get(0).get("main").asBoolean());
		assertEquals(75, zones.get(0).get("width").asInt());
		assertEquals(2, zones.get(0).get("items").size());
		assertEquals(main, zones.get(0).get("playlistId").asLong());
		assertEquals("Lateral", zones.get(1).get("name").asText());
		assertEquals(25, zones.get(1).get("width").asInt());
		assertEquals(1, zones.get(1).get("items").size());
		assertEquals(side, zones.get(1).get("playlistId").asLong());
	}

	@Test
	void theThreeViewsOfTheVersionAgree() throws Exception {
		long main = createPlaylist("Principal", List.of(uploadPng("m1")));
		long side = createPlaylist("Lateral", List.of(uploadPng("s1")));
		long layout = sideLayout("Lateral", side);
		String token = activeTv("TV 1");
		long tv = deviceId("TV 1");
		assignPlaylist(tv, "TV 1", main);
		setLayout(tv, "TV 1", layout);

		long fromConfig = config(token).get("configVersion").asLong();

		assertEquals(fromConfig, heartbeat(token).get("configVersion").asLong());
		assertEquals(fromConfig, device(tv).get("desiredConfigVersion").asLong());
		// e a lista de TVs (que calcula em lote) concorda com a consulta de uma TV
		mvc.perform(asAdmin(get("/api/v1/devices"))).andExpect(jsonPath("$[0].desiredConfigVersion").value(fromConfig));
	}

	@Test
	void changingAnyPartOfTheContentChangesTheVersion() throws Exception {
		long main = createPlaylist("Principal", List.of(uploadPng("m1")));
		long side = createPlaylist("Lateral", List.of(uploadPng("s1")));
		long layout = sideLayout("Lateral", side);
		String token = activeTv("TV 1");
		long tv = deviceId("TV 1");
		assignPlaylist(tv, "TV 1", main);
		setLayout(tv, "TV 1", layout);
		long v0 = config(token).get("configVersion").asLong();

		replacePlaylistItems(side, "Lateral", List.of(uploadPng("s2"))); // a playlist da zona lateral mudou
		long v1 = config(token).get("configVersion").asLong();
		replacePlaylistItems(main, "Principal", List.of(uploadPng("m2"))); // a principal mudou
		long v2 = config(token).get("configVersion").asLong();
		mvc.perform(asAdmin(put("/api/v1/layouts/" + layout).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Lateral", "zones", List.of(zone("Principal", 0, 0, 60, 100, true, null), zone("Lateral", 60, 0, 40, 100, false, side)))))))
				.andExpect(status().isOk()); // as zonas mudaram
		long v3 = config(token).get("configVersion").asLong();

		assertEquals(4, List.of(v0, v1, v2, v3).stream().distinct().count());
	}

	@Test
	void aCampaignReplacesOnlyTheMainZone() throws Exception {
		long base = createPlaylist("Base", List.of(uploadPng("b1")));
		long promo = createPlaylist("Promo", List.of(uploadPng("p1"), uploadPng("p2")));
		long side = createPlaylist("Lateral", List.of(uploadPng("s1")));
		long layout = sideLayout("Lateral", side);
		String token = activeTv("TV 1");
		long tv = deviceId("TV 1");
		assignPlaylist(tv, "TV 1", base);
		setLayout(tv, "TV 1", layout);
		long before = config(token).get("configVersion").asLong();

		mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Promoção", "playlistId", promo, "targets", List.of(Map.of("type", "ALL")))))))
				.andExpect(status().isCreated());
		JsonNode config = config(token);

		assertEquals(promo, config.get("playlistId").asLong());
		assertEquals(2, config.get("zones").get(0).get("items").size()); // a principal mostra a campanha
		assertEquals(side, config.get("zones").get(1).get("playlistId").asLong()); // a lateral continua a mesma
		assertEquals(1, config.get("zones").get(1).get("items").size());
		assertNotEquals(before, config.get("configVersion").asLong());
	}

	@Test
	void aLayoutWithoutAMainPlaylistStillShowsTheOtherZones() throws Exception {
		long side = createPlaylist("Lateral", List.of(uploadPng("s1")));
		long layout = sideLayout("Lateral", side);
		String token = activeTv("TV 1");
		setLayout(deviceId("TV 1"), "TV 1", layout);

		JsonNode config = config(token);

		assertTrue(config.get("playlistId").isNull());
		assertEquals(0, config.get("items").size());
		assertEquals(0, config.get("zones").get(0).get("items").size());
		assertEquals(1, config.get("zones").get(1).get("items").size());
		assertNotEquals(0, config.get("configVersion").asLong());
	}

	@Test
	void aTvWithoutAPlaylistAndWithoutALayoutHasVersionZero() throws Exception {
		String token = activeTv("TV 1");

		assertEquals(0, config(token).get("configVersion").asLong());
		assertEquals(0, device(deviceId("TV 1")).get("desiredConfigVersion").asLong());
	}

}
