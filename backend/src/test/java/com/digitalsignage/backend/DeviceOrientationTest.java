package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

import com.fasterxml.jackson.databind.JsonNode;

/** Orientação da tela de cada TV: como ela foi instalada (horizontal, vertical, de cabeça para baixo). */
class DeviceOrientationTest extends AbstractApiTest {

	private record Tv(long id, String token) {
	}

	private Tv tv(String name, Long playlistId) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.13.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		if (playlistId != null) {
			update(id, name, Map.of("currentPlaylistId", playlistId)).andExpect(status().isOk());
		}
		return new Tv(id, token);
	}

	private org.springframework.test.web.servlet.ResultActions update(long id, String name, Map<String, Object> extra) throws Exception {
		Map<String, Object> body = new HashMap<>(extra);
		body.put("name", name);
		return mvc.perform(asAdmin(put("/api/v1/devices/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private JsonNode config(Tv tv) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tv.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private long heartbeatVersion(Tv tv) throws Exception {
		return json.readTree(mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.13.0\",\"configVersion\":0}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
				.get("configVersion").asLong();
	}

	private List<String> events(long deviceId) {
		return jdbc.queryForList("SELECT message FROM device_events WHERE device_id = ? AND type = 'ORIENTATION_CHANGED' ORDER BY id", String.class, deviceId);
	}

	@Test
	void aTvIsAutomaticByDefaultAndTheConfigSaysSo() throws Exception {
		long playlist = createPlaylist("Base", List.of(uploadPng("a")));
		Tv tv = tv("TV 1", playlist);

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(status().isOk()).andExpect(jsonPath("$.orientation").value("AUTO"));
		assertEquals("AUTO", config(tv).get("orientation").asText());
	}

	@Test
	void theAdministratorSetsHowTheTvWasInstalledAndTheTvLearnsIt() throws Exception {
		long playlist = createPlaylist("Base", List.of(uploadPng("a")));
		Tv tv = tv("TV 1", playlist);
		long before = heartbeatVersion(tv);

		update(tv.id(), "TV 1", Map.of("orientation", "portrait_top_right")).andExpect(status().isOk())
				.andExpect(jsonPath("$.orientation").value("PORTRAIT_TOP_RIGHT"));

		JsonNode config = config(tv);
		assertEquals("PORTRAIT_TOP_RIGHT", config.get("orientation").asText());
		assertNotEquals(before, heartbeatVersion(tv), "a mudança de orientação tem de fazer a TV baixar a configuração");
		assertEquals(config.get("configVersion").asLong(), heartbeatVersion(tv), "a versão da configuração e a do heartbeat precisam bater, senão a TV baixaria sem parar");
		assertEquals(List.of("Orientação da tela: vertical, com o topo da TV virado para a direita"), events(tv.id()));
	}

	@Test
	void changingBetweenOrientationsAlwaysChangesTheVersionAndGoingBackRestoresIt() throws Exception {
		long playlist = createPlaylist("Base", List.of(uploadPng("a")));
		Tv tv = tv("TV 1", playlist);
		long auto = heartbeatVersion(tv);

		java.util.Set<Long> versions = new java.util.HashSet<>();
		for (String orientation : List.of("LANDSCAPE", "LANDSCAPE_FLIPPED", "PORTRAIT_TOP_RIGHT", "PORTRAIT_TOP_LEFT")) {
			update(tv.id(), "TV 1", Map.of("orientation", orientation)).andExpect(status().isOk());
			versions.add(heartbeatVersion(tv));
		}
		assertEquals(4, versions.size());
		assertEquals(false, versions.contains(auto));

		update(tv.id(), "TV 1", Map.of("orientation", "AUTO")).andExpect(status().isOk());
		assertEquals(auto, heartbeatVersion(tv), "no automático a versão é a de sempre: nenhuma TV existente é reconfigurada");
	}

	@Test
	void anUnknownOrientationIsRefusedAndKeepsTheCurrentOne() throws Exception {
		Tv tv = tv("TV 1", createPlaylist("Base", List.of(uploadPng("a"))));
		update(tv.id(), "TV 1", Map.of("orientation", "PORTRAIT_TOP_RIGHT")).andExpect(status().isOk());

		update(tv.id(), "TV 1", Map.of("orientation", "DE LADO")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Orientação inválida: use AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT ou PORTRAIT_TOP_LEFT"));

		assertEquals("PORTRAIT_TOP_RIGHT", config(tv).get("orientation").asText());
	}

	@Test
	void savingTheTvWithoutTheFieldKeepsTheOrientationAndRepeatingItDoesNotAddHistory() throws Exception {
		Tv tv = tv("TV 1", createPlaylist("Base", List.of(uploadPng("a"))));
		update(tv.id(), "TV 1", Map.of("orientation", "LANDSCAPE_FLIPPED")).andExpect(status().isOk());

		update(tv.id(), "TV 1 renomeada", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.orientation").value("LANDSCAPE_FLIPPED"));
		update(tv.id(), "TV 1 renomeada", Map.of("orientation", "LANDSCAPE_FLIPPED")).andExpect(status().isOk());

		assertEquals(List.of("Orientação da tela: horizontal de cabeça para baixo"), events(tv.id()));
	}

	@Test
	void aTvWithoutContentKeepsVersionZeroButStillReceivesTheOrientation() throws Exception {
		Tv tv = tv("TV vazia", null);

		update(tv.id(), "TV vazia", Map.of("orientation", "PORTRAIT_TOP_LEFT")).andExpect(status().isOk());

		assertEquals(0, heartbeatVersion(tv));
		assertEquals("PORTRAIT_TOP_LEFT", config(tv).get("orientation").asText());
	}

	@Test
	void theBulkActionSetsTheOrientationOfManyTvsAndTellsOnlyTheOnesThatChanged() throws Exception {
		long playlist = createPlaylist("Base", List.of(uploadPng("a")));
		Tv one = tv("TV 1", playlist);
		Tv two = tv("TV 2", playlist);
		update(two.id(), "TV 2", Map.of("orientation", "PORTRAIT_TOP_RIGHT")).andExpect(status().isOk());

		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "SET_ORIENTATION", "deviceIds", List.of(one.id(), two.id()), "orientation", "PORTRAIT_TOP_RIGHT")))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.affected").value(2));

		assertEquals("PORTRAIT_TOP_RIGHT", config(one).get("orientation").asText());
		assertEquals(1, events(one.id()).size());
		assertEquals(1, events(two.id()).size(), "a TV que já estava assim não ganha outro registro");

		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "SET_ORIENTATION", "deviceIds", List.of(one.id()))))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Informe a orientação (orientation)"));
	}

	@Test
	void theOrientationTravelsWithTheOfflineAgendaSoTheScreenDoesNotRestartWhenItSwitches() throws Exception {
		long base = createPlaylist("Base", List.of(uploadPng("a")));
		long promo = createPlaylist("Promo", List.of(uploadPng("p")));
		Tv tv = tv("TV 1", base);
		mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Sempre", "playlistId", promo, "targets", List.of(Map.of("type", "ALL")))))))
				.andExpect(status().isCreated());
		update(tv.id(), "TV 1", Map.of("orientation", "PORTRAIT_TOP_LEFT")).andExpect(status().isOk());

		JsonNode config = config(tv);

		assertEquals("PORTRAIT_TOP_LEFT", config.get("orientation").asText());
		// a configuração de cada trecho da agenda tem a mesma versão que o servidor daria naquele momento
		JsonNode segment = config.get("offlinePlan").get("segments").get(0);
		assertEquals(config.get("configVersion").asLong(), segment.get("configVersion").asLong());
	}

}
