package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Várias TVs: filtros da lista, ações em massa, local por nome e situação (online/offline/...) de cada uma. */
class MultiDeviceTest extends AbstractApiTest {

	// ---------- montagem ----------

	private long location(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/locations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private long group(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private long device(String name, Long locationId, List<Long> groupIds) throws Exception {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("name", name);
		if (locationId != null) {
			body.put("locationId", locationId);
		}
		if (groupIds != null) {
			body.put("groupIds", groupIds);
		}
		return idOf(mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private void state(long id, String status, String lastSeenSql) {
		jdbc.update("UPDATE devices SET status = ?, last_seen_at = " + lastSeenSql + " WHERE id = ?", status, id);
	}

	private ResultActions list(String query) throws Exception {
		return mvc.perform(asAdmin(get("/api/v1/devices" + query)));
	}

	private ResultActions bulk(Map<String, Object> body) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/devices/bulk")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body))));
	}

	/** Cinco TVs, uma de cada situação, e dois locais. */
	private long[] fleet() throws Exception {
		long centro = location("Loja Centro");
		long shopping = location("Loja Shopping");
		long online = device("TV Recepção", centro, null);
		long offline = device("TV Vitrine", shopping, null);
		long never = device("TV Estoque", centro, null);
		long pending = device("TV Nova", null, null);
		long disabled = device("TV Antiga", shopping, null);
		state(online, "ACTIVE", "now()");
		state(offline, "ACTIVE", "now() - interval '10 minutes'");
		state(never, "ACTIVE", "NULL");
		state(pending, "PENDING", "NULL");
		state(disabled, "DISABLED", "now()");
		return new long[] { online, offline, never, pending, disabled };
	}

	// ---------- situação ----------

	@Test
	void everyDeviceReportsItsConnectionState() throws Exception {
		long[] f = fleet();

		list("").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[0]))).andExpect(jsonPath("$.connection").value("ONLINE"));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[1]))).andExpect(jsonPath("$.connection").value("OFFLINE"));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[2]))).andExpect(jsonPath("$.connection").value("NEVER"));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[3]))).andExpect(jsonPath("$.connection").value("PENDING"));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[4]))).andExpect(jsonPath("$.connection").value("DISABLED"));
	}

	// ---------- filtros ----------

	@Test
	void filtersByConnection() throws Exception {
		long[] f = fleet();

		list("?connection=ONLINE").andExpect(jsonPath("$[*].id", contains((int) f[0])));
		list("?connection=offline").andExpect(jsonPath("$[*].id", contains((int) f[1])));
		list("?connection=NEVER").andExpect(jsonPath("$[*].id", contains((int) f[2])));
		list("?connection=PENDING").andExpect(jsonPath("$[*].id", contains((int) f[3])));
		list("?connection=DISABLED").andExpect(jsonPath("$[*].id", contains((int) f[4])));
	}

	@Test
	void anUnknownConnectionIsA400WithAHelpfulMessage() throws Exception {
		list("?connection=quebrada")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Situação inválida: quebrada (use ONLINE, OFFLINE, NEVER, PENDING ou DISABLED)"));
	}

	@Test
	void filtersByLocationAndByNoLocation() throws Exception {
		long[] f = fleet();
		long centro = jdbc.queryForObject("SELECT id FROM locations WHERE name = 'Loja Centro'", Long.class);

		list("?locationId=" + centro).andExpect(jsonPath("$[*].id", containsInAnyOrder((int) f[0], (int) f[2])));
		list("?noLocation=true").andExpect(jsonPath("$[*].id", contains((int) f[3])));
	}

	@Test
	void filtersByGroup() throws Exception {
		long salas = group("Salas");
		long a = device("TV A", null, List.of(salas));
		device("TV B", null, null);

		list("?groupId=" + salas).andExpect(jsonPath("$[*].id", contains((int) a)));
	}

	@Test
	void searchMatchesTheDeviceNameAndTheLocationNameIgnoringCase() throws Exception {
		long[] f = fleet();

		list("?q=recep").andExpect(jsonPath("$[*].id", contains((int) f[0])));
		list("?q=SHOPPING").andExpect(jsonPath("$[*].id", containsInAnyOrder((int) f[1], (int) f[4])));
		list("?q=nada-assim").andExpect(jsonPath("$", hasSize(0)));
	}

	/** % e _ digitados na busca são letras comuns, não curingas do SQL. */
	@Test
	void searchTreatsPercentAndUnderscoreAsPlainCharacters() throws Exception {
		device("TV 100% nova", null, null);
		device("TV 100 nova", null, null);
		device("TV_a", null, null);
		device("TVxa", null, null);

		list("?q=100%").andExpect(jsonPath("$", hasSize(1)));
		list("?q=tv_a").andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void filtersCombineWithAnd() throws Exception {
		long[] f = fleet();
		long shopping = jdbc.queryForObject("SELECT id FROM locations WHERE name = 'Loja Shopping'", Long.class);

		list("?locationId=" + shopping + "&connection=OFFLINE").andExpect(jsonPath("$[*].id", contains((int) f[1])));
		list("?locationId=" + shopping + "&connection=ONLINE").andExpect(jsonPath("$", hasSize(0)));
	}

	// ---------- local por nome (compatibilidade com o fluxo de ativação) ----------

	@Test
	void aLocationNameThatDoesNotExistYetCreatesTheLocation() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "location", "Loja Nova")))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.location").value("Loja Nova"))
				.andExpect(jsonPath("$.locationId").isNumber());

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM locations", Integer.class));
	}

	@Test
	void aLocationNameThatExistsIsReusedIgnoringCaseAndSpaces() throws Exception {
		long centro = location("Loja Centro");

		mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "location", "  loja centro ")))))
				.andExpect(jsonPath("$.locationId").value(centro));

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM locations", Integer.class));
	}

	@Test
	void updatingWithoutALocationKeepsItAndAnEmptyNameClearsIt() throws Exception {
		long centro = location("Loja Centro");
		long tv = device("TV", centro, null);

		mvc.perform(asAdmin(put("/api/v1/devices/" + tv)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV nova")))))
				.andExpect(jsonPath("$.locationId").value(centro));

		mvc.perform(asAdmin(put("/api/v1/devices/" + tv)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV nova", "location", "")))))
				.andExpect(jsonPath("$.locationId").doesNotExist());
	}

	@Test
	void anUnknownLocationIdIs404() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "locationId", 999999)))))
				.andExpect(status().isNotFound());
	}

	// ---------- ações em massa ----------

	@Test
	void assignsOnePlaylistToEveryTvOfAGroup() throws Exception {
		long salas = group("Salas");
		long a = device("TV A", null, List.of(salas));
		long b = device("TV B", null, List.of(salas));
		long fora = device("TV fora", null, null);
		long playlist = createPlaylist("Promo", List.of(uploadPng("img")));

		bulk(Map.of("action", "ASSIGN_PLAYLIST", "targetGroupId", salas, "playlistId", playlist))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.affected").value(2));

		mvc.perform(asAdmin(get("/api/v1/devices/" + a))).andExpect(jsonPath("$.currentPlaylistId").value(playlist));
		mvc.perform(asAdmin(get("/api/v1/devices/" + b))).andExpect(jsonPath("$.currentPlaylistId").value(playlist));
		mvc.perform(asAdmin(get("/api/v1/devices/" + fora))).andExpect(jsonPath("$.currentPlaylistId").doesNotExist());
	}

	/** O que a TV recebe tem de refletir a ação em massa (é por aqui que o Player descobre a troca). */
	@Test
	void theBulkAssignmentReachesWhatTheTvDownloads() throws Exception {
		long a = device("TV A", null, null);
		long playlist = createPlaylist("Promo", List.of(uploadPng("img")));

		bulk(Map.of("action", "ASSIGN_PLAYLIST", "deviceIds", List.of(a), "playlistId", playlist)).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/devices/" + a + "/config")))
				.andExpect(jsonPath("$.playlistId").value(playlist))
				.andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void assignsToEveryTvOfALocationAndRemovesAgain() throws Exception {
		long[] f = fleet();
		long centro = jdbc.queryForObject("SELECT id FROM locations WHERE name = 'Loja Centro'", Long.class);
		long playlist = createPlaylist("Promo", List.of(uploadPng("img")));

		bulk(Map.of("action", "ASSIGN_PLAYLIST", "targetLocationId", centro, "playlistId", playlist))
				.andExpect(jsonPath("$.affected").value(2));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[0]))).andExpect(jsonPath("$.currentPlaylistId").value(playlist));

		bulk(Map.of("action", "REMOVE_PLAYLIST", "targetLocationId", centro)).andExpect(jsonPath("$.affected").value(2));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[0]))).andExpect(jsonPath("$.currentPlaylistId").doesNotExist());
	}

	@Test
	void disableAndEnableOnlyTouchTheTvsInTheRightState() throws Exception {
		long[] f = fleet();
		List<Long> all = List.of(f[0], f[1], f[2], f[3], f[4]);

		// ativas: online, offline, nunca conectou (3); a pendente e a desativada ficam como estão
		bulk(Map.of("action", "DISABLE", "deviceIds", all)).andExpect(jsonPath("$.affected").value(3));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[3]))).andExpect(jsonPath("$.status").value("PENDING"));

		// desativadas agora: as 3 de antes + a que já estava (4); a pendente nunca é "reativada"
		bulk(Map.of("action", "ENABLE", "deviceIds", all)).andExpect(jsonPath("$.affected").value(4));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[3]))).andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void checkUpdateOnlyReachesActiveTvs() throws Exception {
		long[] f = fleet();

		bulk(Map.of("action", "CHECK_UPDATE", "deviceIds", List.of(f[0], f[3], f[4])))
				.andExpect(jsonPath("$.affected").value(1));

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM devices WHERE update_requested_at IS NOT NULL", Integer.class));
	}

	@Test
	void movesTvsToAnotherLocationAndClearsIt() throws Exception {
		long[] f = fleet();
		long novo = location("Depósito");

		bulk(Map.of("action", "SET_LOCATION", "deviceIds", List.of(f[0], f[1]), "locationId", novo))
				.andExpect(jsonPath("$.affected").value(2));
		list("?locationId=" + novo).andExpect(jsonPath("$[*].id", containsInAnyOrder((int) f[0], (int) f[1])));

		bulk(Map.of("action", "CLEAR_LOCATION", "deviceIds", List.of(f[0]))).andExpect(jsonPath("$.affected").value(1));
		mvc.perform(asAdmin(get("/api/v1/devices/" + f[0]))).andExpect(jsonPath("$.locationId").doesNotExist());
	}

	@Test
	void addsAndRemovesTvsFromAGroup() throws Exception {
		long a = device("TV A", null, null);
		long b = device("TV B", null, null);
		long salas = group("Salas");

		bulk(Map.of("action", "ADD_TO_GROUP", "deviceIds", List.of(a, b), "groupId", salas))
				.andExpect(jsonPath("$.affected").value(2));
		list("?groupId=" + salas).andExpect(jsonPath("$", hasSize(2)));

		bulk(Map.of("action", "REMOVE_FROM_GROUP", "deviceIds", List.of(a), "groupId", salas))
				.andExpect(jsonPath("$.affected").value(1));
		list("?groupId=" + salas).andExpect(jsonPath("$[*].id", contains((int) b)));
	}

	@Test
	void bulkNeedsExactlyOneTarget() throws Exception {
		long a = device("TV A", null, null);
		long salas = group("Salas");

		bulk(Map.of("action", "DISABLE")).andExpect(status().isBadRequest());
		bulk(Map.of("action", "DISABLE", "deviceIds", List.of())).andExpect(status().isBadRequest());
		bulk(Map.of("action", "DISABLE", "deviceIds", List.of(a), "targetGroupId", salas))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Escolha exatamente um alvo: deviceIds, targetGroupId ou targetLocationId"));
	}

	@Test
	void bulkValidatesTheActionParametersAndTheTargets() throws Exception {
		long a = device("TV A", null, null);

		bulk(Map.of("action", "ASSIGN_PLAYLIST", "deviceIds", List.of(a))).andExpect(status().isBadRequest());
		bulk(Map.of("action", "ASSIGN_PLAYLIST", "deviceIds", List.of(a), "playlistId", 999999)).andExpect(status().isNotFound());
		bulk(Map.of("action", "SET_LOCATION", "deviceIds", List.of(a))).andExpect(status().isBadRequest());
		bulk(Map.of("action", "ADD_TO_GROUP", "deviceIds", List.of(a))).andExpect(status().isBadRequest());
		bulk(Map.of("action", "ADD_TO_GROUP", "deviceIds", List.of(a), "groupId", 999999)).andExpect(status().isNotFound());
		bulk(Map.of("action", "DISABLE", "deviceIds", List.of(a, 999999))).andExpect(status().isNotFound());
		bulk(Map.of("action", "DISABLE", "targetGroupId", 999999)).andExpect(status().isNotFound());
		bulk(Map.of("action", "DISABLE", "targetLocationId", 999999)).andExpect(status().isNotFound());
		bulk(Map.of("action", "FAZER_CAFE", "deviceIds", List.of(a))).andExpect(status().isBadRequest());
	}

	/** Uma ação que falha no meio não pode deixar metade das TVs alteradas. */
	@Test
	void aFailedBulkChangesNothing() throws Exception {
		long a = device("TV A", null, null);
		long b = device("TV B", null, null);
		state(a, "ACTIVE", "now()");

		bulk(Map.of("action", "DISABLE", "deviceIds", List.of(a, b, 999999))).andExpect(status().isNotFound());

		mvc.perform(asAdmin(get("/api/v1/devices/" + a))).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void bulkNeedsAnAdmin() throws Exception {
		mvc.perform(post("/api/v1/devices/bulk")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"DISABLE\",\"deviceIds\":[1]}"))
				.andExpect(status().isUnauthorized());
	}

}
