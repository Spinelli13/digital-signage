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

class DeviceTest extends AbstractApiTest {

	@Test
	void createListGetUpdateDelete() throws Exception {
		long id = createDevice("TV Recepção");

		mvc.perform(asAdmin(get("/api/v1/devices")))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("TV Recepção"))
				.andExpect(jsonPath("$[0].status").value("PENDING"))
				.andExpect(jsonPath("$[0].online").value(false));

		mvc.perform(asAdmin(put("/api/v1/devices/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV Hall", "location", "Térreo", "status", "active")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("TV Hall"))
				.andExpect(jsonPath("$.location").value("Térreo"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mvc.perform(asAdmin(delete("/api/v1/devices/" + id))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/devices/" + id))).andExpect(status().isNotFound());
	}

	@Test
	void nameIsRequired() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" \"}")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateOmittingStatusAndPlaylistKeepsThem() throws Exception {
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long id = createDevice("TV");
		assignPlaylist(id, "TV", playlist);

		mvc.perform(asAdmin(put("/api/v1/devices/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV renomeada")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.currentPlaylistId").value(playlist));
	}

	@Test
	void invalidStatusIsRejected() throws Exception {
		long id = createDevice("TV");

		mvc.perform(asAdmin(put("/api/v1/devices/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "status", "exploded")))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownDeviceIsNotFound() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/devices/999999"))).andExpect(status().isNotFound());
		mvc.perform(asAdmin(delete("/api/v1/devices/999999"))).andExpect(status().isNotFound());
	}

	@Test
	void assigningUnknownPlaylistIsNotFound() throws Exception {
		long id = createDevice("TV");

		mvc.perform(asAdmin(put("/api/v1/devices/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "currentPlaylistId", 777)))))
				.andExpect(status().isNotFound());
	}

	@Test
	void removingThePlaylistLeavesTheDeviceWithoutOneAndWithAnEmptyConfig() throws Exception {
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long id = createDevice("TV");
		assignPlaylist(id, "TV", playlist);

		mvc.perform(asAdmin(delete("/api/v1/devices/" + id + "/playlist")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPlaylistId").doesNotExist());

		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/config")))
				.andExpect(jsonPath("$.configVersion").value(0))
				.andExpect(jsonPath("$.items", hasSize(0)));
		// a playlist em si não é apagada: só deixa de estar atribuída
		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlist))).andExpect(status().isOk());
	}

	@Test
	void removingThePlaylistIsIdempotent() throws Exception {
		long id = createDevice("TV");

		mvc.perform(asAdmin(delete("/api/v1/devices/" + id + "/playlist"))).andExpect(status().isOk());
		mvc.perform(asAdmin(delete("/api/v1/devices/" + id + "/playlist"))).andExpect(status().isOk());
	}

	@Test
	void removingThePlaylistOfAnUnknownDeviceIsNotFound() throws Exception {
		mvc.perform(asAdmin(delete("/api/v1/devices/999999/playlist"))).andExpect(status().isNotFound());
	}

	@Test
	void removingThePlaylistRequiresAdmin() throws Exception {
		long id = createDevice("TV");

		mvc.perform(delete("/api/v1/devices/" + id + "/playlist")).andExpect(status().isUnauthorized());
	}

	@Test
	void deletingAPlaylistDetachesItFromTheDevice() throws Exception {
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long id = createDevice("TV");
		assignPlaylist(id, "TV", playlist);

		mvc.perform(asAdmin(delete("/api/v1/playlists/" + playlist))).andExpect(status().isNoContent());

		mvc.perform(asAdmin(get("/api/v1/devices/" + id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPlaylistId").doesNotExist());
	}

	// --- GET /devices/{id}/config (visão do admin sobre o que o Player receberá) ---

	@Test
	void configOfDeviceWithoutPlaylistIsEmptyVersionZero() throws Exception {
		long id = createDevice("TV");

		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/config")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configVersion").value(0))
				.andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void configListsMediaWithUrlChecksumAndDuration() throws Exception {
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long id = createDevice("TV");
		assignPlaylist(id, "TV", playlist);

		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/config")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playlistId").value(playlist))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].mediaId").value(media))
				.andExpect(jsonPath("$.items[0].url").value("/api/v1/media/" + media + "/file"))
				.andExpect(jsonPath("$.items[0].checksum").isNotEmpty())
				.andExpect(jsonPath("$.items[0].durationSeconds").value(10));
	}

	/**
	 * Regressão (decisão de design): configVersion vem sempre da playlist, nunca de devices.config_version.
	 * Editar a playlist depois de associada precisa refletir no config do dispositivo sem reassociar.
	 */
	@Test
	void configVersionFollowsPlaylistEditsWithoutReassigning() throws Exception {
		long media = uploadPng("img");
		long playlist = createPlaylist("P", List.of(media));
		long id = createDevice("TV");
		assignPlaylist(id, "TV", playlist);

		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/config"))).andExpect(jsonPath("$.configVersion").value(1));

		replacePlaylistItems(playlist, "P", List.of(media, media, media));

		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/config")))
				.andExpect(jsonPath("$.configVersion").value(2))
				.andExpect(jsonPath("$.items", hasSize(3)));
	}

}
