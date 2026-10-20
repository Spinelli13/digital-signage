package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SummaryTest extends AbstractApiTest {

	private long device(String name, String location) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(location == null
						? Map.of("name", name)
						: Map.of("name", name, "location", location)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private void state(long id, String status, String lastSeenSql) {
		jdbc.update("UPDATE devices SET status = ?, last_seen_at = " + lastSeenSql + " WHERE id = ?", status, id);
	}

	@Test
	void anEmptySystemHasZeros() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/summary")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.devices.total").value(0))
				.andExpect(jsonPath("$.devices.online").value(0))
				.andExpect(jsonPath("$.byLocation", hasSize(0)))
				.andExpect(jsonPath("$.attention", hasSize(0)));
	}

	@Test
	void countsEachSituationLocationsGroupsMediaAndPlaylists() throws Exception {
		long online = device("A online", "Centro");
		long online2 = device("B online", "Centro");
		long offline = device("C offline", "Shopping");
		long never = device("D nunca", null);
		long pending = device("E pendente", null);
		long disabled = device("F desativada", "Shopping");
		state(online, "ACTIVE", "now()");
		state(online2, "ACTIVE", "now() - interval '30 seconds'");
		state(offline, "ACTIVE", "now() - interval '1 hour'");
		state(never, "ACTIVE", "NULL");
		state(pending, "PENDING", "NULL");
		state(disabled, "DISABLED", "now()");
		mvc.perform(asAdmin(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Todos\"}"))).andExpect(status().isCreated());
		createPlaylist("P", List.of(uploadPng("img")));

		mvc.perform(asAdmin(get("/api/v1/summary")))
				.andExpect(jsonPath("$.devices.total").value(6))
				.andExpect(jsonPath("$.devices.online").value(2))
				.andExpect(jsonPath("$.devices.offline").value(1))
				.andExpect(jsonPath("$.devices.never").value(1))
				.andExpect(jsonPath("$.devices.pending").value(1))
				.andExpect(jsonPath("$.devices.disabled").value(1))
				.andExpect(jsonPath("$.locations").value(2))
				.andExpect(jsonPath("$.groups").value(1))
				.andExpect(jsonPath("$.media").value(1))
				.andExpect(jsonPath("$.playlists").value(1));
	}

	@Test
	void summarizesEachLocationAndGroupsTvsWithoutLocation() throws Exception {
		long a = device("A", "Centro");
		long b = device("B", "Centro");
		device("C", null);
		state(a, "ACTIVE", "now()");
		state(b, "ACTIVE", "now() - interval '1 hour'");

		mvc.perform(asAdmin(get("/api/v1/summary")))
				.andExpect(jsonPath("$.byLocation", hasSize(2)))
				.andExpect(jsonPath("$.byLocation[0].name").value("Centro"))
				.andExpect(jsonPath("$.byLocation[0].total").value(2))
				.andExpect(jsonPath("$.byLocation[0].online").value(1))
				.andExpect(jsonPath("$.byLocation[1].name").value("Sem local"))
				.andExpect(jsonPath("$.byLocation[1].id").doesNotExist())
				.andExpect(jsonPath("$.byLocation[1].total").value(1));
	}

	/** Ordem: offline (era para estar no ar) → nunca conectou → aguardando ativação. Desativadas e online não alertam. */
	@Test
	void attentionListsProblemsMostUrgentFirstAndSkipsHealthyAndDisabledTvs() throws Exception {
		long online = device("online", "Centro");
		long pending = device("pendente", null);
		long never = device("nunca", null);
		long offline = device("offline", "Centro");
		long disabled = device("desativada", null);
		state(online, "ACTIVE", "now()");
		state(pending, "PENDING", "NULL");
		state(never, "ACTIVE", "NULL");
		state(offline, "ACTIVE", "now() - interval '1 hour'");
		state(disabled, "DISABLED", "NULL");

		mvc.perform(asAdmin(get("/api/v1/summary")))
				.andExpect(jsonPath("$.attention[*].name", contains("offline", "nunca", "pendente")))
				.andExpect(jsonPath("$.attention[0].connection").value("OFFLINE"))
				.andExpect(jsonPath("$.attention[0].location").value("Centro"));
	}

	@Test
	void theSummaryNeedsAnAdmin() throws Exception {
		mvc.perform(get("/api/v1/summary")).andExpect(status().isUnauthorized());
	}

}
