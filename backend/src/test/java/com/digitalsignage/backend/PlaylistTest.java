package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class PlaylistTest extends AbstractApiTest {

	/**
	 * Regressão: GET /playlists e GET /playlists/{id} quebravam com LazyInitializationException
	 * (open-in-view desligado + coleção de itens LAZY fora de transação). POST/PUT escondiam o bug.
	 */
	@Test
	void listAndGetReadLazyItemsWithoutLazyInitializationException() throws Exception {
		long media1 = uploadPng("a");
		long media2 = uploadPng("b");
		long playlistId = createPlaylist("Loja", List.of(media1, media2));

		mvc.perform(asAdmin(get("/api/v1/playlists")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].items", hasSize(2)));

		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlistId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[0].mediaName").value("a"));
	}

	@Test
	void itemsKeepTheOrderTheyWereSentIn() throws Exception {
		long first = uploadPng("primeira");
		long second = uploadPng("segunda");
		long third = uploadPng("terceira");

		long playlistId = createPlaylist("Ordem", List.of(third, first, second));

		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlistId)))
				.andExpect(jsonPath("$.items[0].mediaName").value("terceira"))
				.andExpect(jsonPath("$.items[0].position").value(0))
				.andExpect(jsonPath("$.items[1].mediaName").value("primeira"))
				.andExpect(jsonPath("$.items[2].mediaName").value("segunda"));
	}

	@Test
	void imageWithoutDurationDefaultsToTenSeconds() throws Exception {
		long media = uploadPng("img");
		long playlistId = createPlaylist("Default", List.of(media));

		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlistId)))
				.andExpect(jsonPath("$.items[0].durationSeconds").value(10));
	}

	@Test
	void videoWithoutExplicitDurationUsesTheMediaDuration() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "v.mp4", "video/mp4", new byte[] { 9, 9 });
		String body = mvc.perform(asAdmin(MockMvcRequestBuilders.multipart("/api/v1/media").file(file)
				.param("durationSeconds", "33")))
				.andReturn().getResponse().getContentAsString();
		long videoId = idOf(body);

		long playlistId = createPlaylist("Video", List.of(videoId));

		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlistId)))
				.andExpect(jsonPath("$.items[0].durationSeconds").value(33));
	}

	@Test
	void explicitDurationWinsOverMediaDuration() throws Exception {
		long media = uploadPng("img");

		String body = mvc.perform(asAdmin(post("/api/v1/playlists")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of(
						"name", "Explícita",
						"items", List.of(Map.of("mediaId", media, "durationSeconds", 25)))))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		mvc.perform(asAdmin(get("/api/v1/playlists/" + idOf(body))))
				.andExpect(jsonPath("$.items[0].durationSeconds").value(25));
	}

	@Test
	void configVersionIncrementsEveryTimeItemsAreReplaced() throws Exception {
		long media = uploadPng("img");
		long playlistId = createPlaylist("Versão", List.of(media));

		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlistId))).andExpect(jsonPath("$.configVersion").value(1));

		replacePlaylistItems(playlistId, "Versão", List.of(media, media));
		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlistId)))
				.andExpect(jsonPath("$.configVersion").value(2))
				.andExpect(jsonPath("$.items", hasSize(2)));
	}

	@Test
	void updateWithoutItemsKeepsExistingItemsAndVersion() throws Exception {
		long media = uploadPng("img");
		long playlistId = createPlaylist("Mantém", List.of(media));

		mvc.perform(asAdmin(put("/api/v1/playlists/" + playlistId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Renomeada")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renomeada"))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.configVersion").value(1));
	}

	@Test
	void playlistReferencingUnknownMediaIsNotFound() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/playlists")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of(
						"name", "Quebrada",
						"items", List.of(Map.of("mediaId", 424242)))))))
				.andExpect(status().isNotFound());
	}

	@Test
	void playlistNameIsRequired() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/playlists")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}")))
				.andExpect(status().isBadRequest());
	}

	/** Regressão de integridade: FK ON DELETE RESTRICT em playlist_items.media_id vira 409, não 500. */
	@Test
	void mediaUsedByAPlaylistCannotBeDeletedUntilThePlaylistIsGone() throws Exception {
		long media = uploadPng("em-uso");
		long playlistId = createPlaylist("Usa mídia", List.of(media));

		mvc.perform(asAdmin(delete("/api/v1/media/" + media))).andExpect(status().isConflict());
		// o arquivo não pode ter sido apagado pela tentativa que falhou
		mvc.perform(asAdmin(get("/api/v1/media/" + media + "/file"))).andExpect(status().isOk());

		mvc.perform(asAdmin(delete("/api/v1/playlists/" + playlistId))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(delete("/api/v1/media/" + media))).andExpect(status().isNoContent());
	}

	@Test
	void deletingPlaylistRemovesItsItems() throws Exception {
		long media = uploadPng("img");
		long playlistId = createPlaylist("Descartável", List.of(media));

		mvc.perform(asAdmin(delete("/api/v1/playlists/" + playlistId))).andExpect(status().isNoContent());

		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlistId))).andExpect(status().isNotFound());
		org.junit.jupiter.api.Assertions.assertEquals(0,
				jdbc.queryForObject("SELECT count(*) FROM playlist_items", Integer.class));
	}

}
