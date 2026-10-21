package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.fasterxml.jackson.databind.JsonNode;

/** Layouts responsivos: a versão para TV em pé e o ajuste da mídia em cada zona (caber ou preencher). */
class ResponsiveLayoutTest extends AbstractApiTest {

	private static Map<String, Object> zone(String name, int x, int y, int w, int h, boolean main, Long playlistId, String fit) {
		Map<String, Object> zone = new HashMap<>();
		zone.put("name", name);
		zone.put("x", x);
		zone.put("y", y);
		zone.put("width", w);
		zone.put("height", h);
		zone.put("main", main);
		zone.put("playlistId", playlistId);
		if (fit != null) {
			zone.put("fit", fit);
		}
		return zone;
	}

	private static List<Map<String, Object>> landscape(Long side) {
		return List.of(zone("Principal", 0, 0, 75, 100, true, null, null), zone("Lateral", 75, 0, 25, 100, false, side, "COVER"));
	}

	private static List<Map<String, Object>> portrait(Long side) {
		return List.of(zone("Principal", 0, 0, 100, 75, true, null, null), zone("Lateral", 0, 75, 100, 25, false, side, "COVER"));
	}

	private ResultActions submit(Map<String, Object> body) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private Map<String, Object> body(String name, List<Map<String, Object>> zones, List<Map<String, Object>> portraitZones) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("zones", zones);
		if (portraitZones != null) {
			body.put("portraitZones", portraitZones);
		}
		return body;
	}

	private long create(String name, Long side) throws Exception {
		return idOf(submit(body(name, landscape(side), portrait(side))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private String tv(String name, long layoutId, long playlistId) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.12.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		mvc.perform(asAdmin(put("/api/v1/devices/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "layoutId", layoutId, "currentPlaylistId", playlistId))))).andExpect(status().isOk());
		return token;
	}

	private JsonNode config(String token) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private long heartbeatVersion(String token) throws Exception {
		return json.readTree(mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.12.0\",\"configVersion\":0}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
				.get("configVersion").asLong();
	}

	@Test
	void aLayoutKeepsBothVersionsAndTheFitOfEachZone() throws Exception {
		long promo = createPlaylist("Promos", List.of(uploadPng("promo")));
		long id = create("Lateral", promo);

		mvc.perform(asAdmin(get("/api/v1/layouts/" + id))).andExpect(status().isOk())
				.andExpect(jsonPath("$.zones.length()").value(2))
				.andExpect(jsonPath("$.zones[0].fit").value("CONTAIN"))
				.andExpect(jsonPath("$.zones[1].fit").value("COVER"))
				.andExpect(jsonPath("$.portraitZones.length()").value(2))
				.andExpect(jsonPath("$.portraitZones[0].name").value("Principal"))
				.andExpect(jsonPath("$.portraitZones[0].width").value(100))
				.andExpect(jsonPath("$.portraitZones[0].height").value(75))
				.andExpect(jsonPath("$.portraitZones[1].playlistId").value(promo))
				.andExpect(jsonPath("$.portraitZones[1].playlistName").value("Promos"));
	}

	@Test
	void aLayoutWithoutAPortraitVersionHasNone() throws Exception {
		long id = idOf(submit(body("Só horizontal", landscape(null), null)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		mvc.perform(asAdmin(get("/api/v1/layouts/" + id))).andExpect(jsonPath("$.portraitZones.length()").value(0));
	}

	@Test
	void thePortraitVersionFollowsTheSameRulesAsTheHorizontalOne() throws Exception {
		submit(body("Sem principal", landscape(null), List.of(zone("A", 0, 0, 100, 100, false, null, null)))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Escolha exatamente uma zona principal (a que exibe a playlist da TV)"));
		submit(body("Sobrepostas", landscape(null), List.of(zone("A", 0, 0, 100, 60, true, null, null), zone("B", 0, 50, 100, 50, false, null, null))))
				.andExpect(status().isBadRequest());
		submit(body("Fora da tela", landscape(null), List.of(zone("A", 0, 0, 100, 120, true, null, null)))).andExpect(status().isBadRequest());
		submit(body("Playlist fantasma", landscape(null), List.of(zone("A", 0, 0, 100, 60, true, null, null), zone("B", 0, 60, 100, 40, false, 9999L, null))))
				.andExpect(status().isNotFound());
		mvc.perform(asAdmin(get("/api/v1/layouts"))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void anUnknownFitIsRefusedInEitherVersion() throws Exception {
		submit(body("Ajuste ruim", List.of(zone("A", 0, 0, 100, 100, true, null, "ESTICAR")), null)).andExpect(status().isBadRequest());
		submit(body("Ajuste ruim em pé", landscape(null), List.of(zone("A", 0, 0, 100, 100, true, null, "ESTICAR")))).andExpect(status().isBadRequest());
	}

	@Test
	void editingCanRemoveThePortraitVersion() throws Exception {
		long id = create("Lateral", null);

		mvc.perform(asAdmin(put("/api/v1/layouts/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body("Lateral", landscape(null), null))))).andExpect(status().isOk())
				.andExpect(jsonPath("$.zones.length()").value(2))
				.andExpect(jsonPath("$.portraitZones.length()").value(0));
	}

	@Test
	void theTvReceivesBothVersionsWithTheMainZoneFilledByItsPlaylist() throws Exception {
		long promo = createPlaylist("Promos", List.of(uploadPng("promo")));
		long own = createPlaylist("Da TV", List.of(uploadPng("a"), uploadPng("b")));
		long layout = create("Lateral", promo);
		String token = tv("TV 1", layout, own);

		JsonNode config = config(token);

		assertEquals(2, config.get("zones").size());
		assertEquals(2, config.get("portraitZones").size());
		assertEquals(own, config.get("portraitZones").get(0).get("playlistId").asLong());
		assertEquals(2, config.get("portraitZones").get(0).get("items").size());
		assertEquals(1, config.get("portraitZones").get(1).get("items").size());
		assertEquals("COVER", config.get("portraitZones").get(1).get("fit").asText());
		assertEquals("CONTAIN", config.get("zones").get(0).get("fit").asText());
		assertEquals(2, config.get("items").size()); // continua sendo a zona principal da versão horizontal
	}

	@Test
	void aLayoutWithoutPortraitSendsAnEmptyList() throws Exception {
		long own = createPlaylist("Da TV", List.of(uploadPng("a")));
		long layout = idOf(submit(body("Só horizontal", landscape(null), null)).andReturn().getResponse().getContentAsString());
		String token = tv("TV 1", layout, own);

		assertEquals(0, config(token).get("portraitZones").size());
	}

	@Test
	void changingAPlaylistOfThePortraitVersionTellsTheTv() throws Exception {
		long promo = createPlaylist("Promos", List.of(uploadPng("promo")));
		long own = createPlaylist("Da TV", List.of(uploadPng("a")));
		long other = createPlaylist("Outra", List.of(uploadPng("o")));
		// só a versão em pé usa a playlist "Promos"
		long layout = idOf(submit(body("Só em pé usa", landscape(null), portrait(promo))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		String token = tv("TV 1", layout, own);
		long before = heartbeatVersion(token);

		replacePlaylistItems(promo, "Promos", List.of(uploadPng("novo")));

		assertNotEquals(before, heartbeatVersion(token));
		// e trocar a playlist da zona em pé também
		long afterEdit = heartbeatVersion(token);
		mvc.perform(asAdmin(put("/api/v1/layouts/" + layout).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body("Só em pé usa", landscape(null), portrait(other)))))).andExpect(status().isOk());
		assertNotEquals(afterEdit, heartbeatVersion(token));
	}

	@Test
	void aPlaylistUsedOnlyInThePortraitVersionCannotBeDeleted() throws Exception {
		long promo = createPlaylist("Promos", List.of(uploadPng("promo")));
		idOf(submit(body("Só em pé usa", landscape(null), portrait(promo))).andReturn().getResponse().getContentAsString());

		mvc.perform(asAdmin(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/playlists/" + promo)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void templatesBringASuggestionForPortraitScreens() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/layouts/templates"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItems("principal-lateral", "totem-em-pe")))
				.andExpect(jsonPath("$[1].portraitZones.length()").value(2))
				.andExpect(jsonPath("$[1].portraitZones[1].y").value(75));

		long id = idOf(mvc.perform(asAdmin(post("/api/v1/layouts/from-template").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("templateId", "totem-em-pe", "name", "Totem"))))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		JsonNode layout = json.readTree(mvc.perform(asAdmin(get("/api/v1/layouts/" + id))).andReturn().getResponse().getContentAsString());
		assertEquals(3, layout.get("zones").size());
		assertEquals(3, layout.get("portraitZones").size());
		assertTrue(layout.get("portraitZones").get(1).get("main").asBoolean());
	}

}
