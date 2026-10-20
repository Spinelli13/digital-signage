package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

/** Conteúdo web: uma página (https) na biblioteca, na playlist e na configuração do Player. */
class WebContentTest extends AbstractApiTest {

	private ResultActions createWeb(Map<String, Object> body) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/media/web").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private long web(String name, String url, int seconds) throws Exception {
		return idOf(createWeb(Map.of("name", name, "url", url, "durationSeconds", seconds)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	@Test
	void savesAPageInTheLibraryWithoutAnyFile() throws Exception {
		createWeb(Map.of("name", "Painel de vendas", "url", "https://painel.empresa.com/tv?token=abc", "durationSeconds", 45))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("WEB"))
				.andExpect(jsonPath("$.name").value("Painel de vendas"))
				.andExpect(jsonPath("$.url").value("https://painel.empresa.com/tv?token=abc"))
				.andExpect(jsonPath("$.durationSeconds").value(45))
				.andExpect(jsonPath("$.fileSize").value(0));

		mvc.perform(asAdmin(get("/api/v1/media"))).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].type").value("WEB"));
	}

	@Test
	void theNameDefaultsToTheSiteAndTheAddressIsCleaned() throws Exception {
		createWeb(Map.of("url", "  https://noticias.exemplo.com/  ", "durationSeconds", 20))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("noticias.exemplo.com"))
				.andExpect(jsonPath("$.url").value("https://noticias.exemplo.com/"));
	}

	@Test
	void refusesUnsafeOrInvalidAddressesAndDurations() throws Exception {
		createWeb(Map.of("url", "http://inseguro.com", "durationSeconds", 20))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("O endereço da página precisa começar com https://"));
		createWeb(Map.of("url", "javascript:alert(1)", "durationSeconds", 20)).andExpect(status().isBadRequest());
		createWeb(Map.of("url", "https://u:p@site.com", "durationSeconds", 20)).andExpect(status().isBadRequest());
		createWeb(Map.of("url", " ", "durationSeconds", 20)).andExpect(status().isBadRequest());
		createWeb(Map.of("url", "https://site.com", "durationSeconds", 1)).andExpect(status().isBadRequest());
		createWeb(Map.of("url", "https://site.com", "durationSeconds", 99999)).andExpect(status().isBadRequest());
		createWeb(Map.of("url", "https://site.com")).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/media"))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void thereIsNoFileToDownloadButThePreviewAddressIsThePageItself() throws Exception {
		long id = web("Página", "https://site.com/p", 30);

		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/file")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Esta mídia é uma página da web: não há arquivo para baixar"));
		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/url")))
				.andExpect(jsonPath("$.url").value("https://site.com/p"))
				.andExpect(jsonPath("$.requiresAuth").value(false));
	}

	@Test
	void aPageGoesIntoAPlaylistAndTheConfigGivesThePlayerTheAddress() throws Exception {
		long page = web("Painel", "https://painel.empresa.com/tv", 40);
		long image = uploadPng("logo");
		long playlist = createPlaylist("Mista", List.of(image, page));
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.6.0")))).andReturn().getResponse().getContentAsString();
		mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", json.readTree(registered).get("activationCode").asText(), "name", "TV")))));
		assignPlaylist(jdbc.queryForObject("SELECT id FROM devices WHERE name = 'TV'", Long.class), "TV", playlist);

		JsonNode items = json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", token)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString()).get("items");

		assertEquals(2, items.size());
		assertEquals("IMAGE", items.get(0).get("type").asText());
		assertEquals("/api/v1/media/" + image + "/file", items.get(0).get("url").asText());
		assertEquals("WEB", items.get(1).get("type").asText());
		assertEquals("https://painel.empresa.com/tv", items.get(1).get("url").asText()); // o Player abre direto, sem baixar
		assertEquals(40, items.get(1).get("durationSeconds").asInt()); // a duração padrão vem da mídia
		assertEquals(0, items.get(1).get("fileSize").asLong());
		assertEquals(true, items.get(1).get("checksum").isNull());
	}

	@Test
	void deletingAPageNeedsNoStorageAndIsRefusedWhileInAPlaylist() throws Exception {
		long page = web("Página", "https://site.com", 30);
		long playlist = createPlaylist("P", List.of(page));

		mvc.perform(asAdmin(delete("/api/v1/media/" + page))).andExpect(status().isConflict());
		mvc.perform(asAdmin(delete("/api/v1/playlists/" + playlist))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(delete("/api/v1/media/" + page))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/media"))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void onlyAdminsCanAddPages() throws Exception {
		mvc.perform(post("/api/v1/media/web").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://site.com\",\"durationSeconds\":30}"))
				.andExpect(status().isUnauthorized());
	}

}
