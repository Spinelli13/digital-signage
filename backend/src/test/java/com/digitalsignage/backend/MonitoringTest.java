package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.monitoring.AlertNotifier;
import com.digitalsignage.backend.monitoring.MonitoringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;

/**
 * Histórico (linha do tempo), alertas e webhook. O relógio do servidor é congelado pelo teste; a avaliação
 * periódica é chamada na hora que o teste quer.
 */
class MonitoringTest extends AbstractApiTest {

	private static final String T0 = "2026-11-05T12:00:00Z";

	@Autowired
	private MonitoringService monitoring;

	@Autowired
	private AlertNotifier notifier;

	private HttpServer webhookServer;

	@AfterEach
	void stopWebhookServer() {
		if (webhookServer != null) {
			webhookServer.stop(0);
		}
	}

	// ---------- montagem ----------

	private long playlist(String name) throws Exception {
		return createPlaylist(name, List.of(uploadPng("img-" + name)));
	}

	/** Uma TV de verdade: registra pelo Player, ativa pelo painel e devolve o token do aparelho. */
	private String activeTv(String name, Long basePlaylist) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.4.0"))))
				.andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		if (basePlaylist != null) {
			assignPlaylist(id, name, basePlaylist);
		}
		return token;
	}

	private MockHttpServletRequestBuilder asDevice(MockHttpServletRequestBuilder request, String token) {
		return request.header("X-Device-Token", token);
	}

	private ResultActions heartbeat(String token, Map<String, Object> extra) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("playerVersion", "0.4.0", "configVersion", 0));
		body.putAll(extra);
		return mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body)));
	}

	private ResultActions heartbeat(String token) throws Exception {
		return heartbeat(token, Map.of());
	}

	private long deviceId(String name) {
		return jdbc.queryForObject("SELECT id FROM devices WHERE name = ?", Long.class, name);
	}

	private List<String> types(long deviceId) {
		return jdbc.queryForList("SELECT type FROM device_events WHERE device_id = ? ORDER BY id", String.class, deviceId);
	}

	private List<Map<String, Object>> events(long deviceId, String type) {
		return jdbc.queryForList("SELECT * FROM device_events WHERE device_id = ? AND type = ? ORDER BY id", deviceId, type);
	}

	private Instant at(String iso) {
		return Instant.parse(iso);
	}

	private void evaluateAt(String iso) {
		clock.freeze(iso);
		inTenant(DEFAULT_TENANT, () -> monitoring.evaluate(clock.instant()));
	}

	private int openAlerts(long deviceId, String type) {
		return jdbc.queryForObject("SELECT count(*) FROM alerts WHERE device_id = ? AND type = ? AND resolved_at IS NULL", Integer.class, deviceId, type);
	}

	private int allAlerts(long deviceId, String type) {
		return jdbc.queryForObject("SELECT count(*) FROM alerts WHERE device_id = ? AND type = ?", Integer.class, deviceId, type);
	}

	// ---------- linha do tempo ----------

	@Test
	void activationAndTheFirstContactAreOnTheTimeline() throws Exception {
		long base = playlist("Base");
		clock.freeze(T0);
		String token = activeTv("TV Recepção", base);
		long id = deviceId("TV Recepção");

		heartbeat(token).andExpect(status().isOk());

		assertEquals(List.of("ACTIVATED", "ONLINE", "PLAYLIST_CHANGED"), types(id));
		assertEquals("Primeiro contato da TV com o servidor", events(id, "ONLINE").get(0).get("message"));
		assertEquals("Exibindo a playlist \"Base\"", events(id, "PLAYLIST_CHANGED").get(0).get("message"));
	}

	@Test
	void whileTheTvStaysOnlineNothingIsRepeated() throws Exception {
		long base = playlist("Base");
		clock.freeze(T0);
		String token = activeTv("TV", base);
		long id = deviceId("TV");
		heartbeat(token);

		clock.freeze("2026-11-05T12:00:30Z");
		heartbeat(token);
		clock.freeze("2026-11-05T12:01:00Z");
		heartbeat(token);

		assertEquals(1, events(id, "ONLINE").size());
		assertEquals(1, events(id, "PLAYLIST_CHANGED").size());
	}

	@Test
	void theServerNoticesASilentTvAndRecordsWhenItWentOffline() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);

		evaluateAt("2026-11-05T12:01:00Z"); // ainda dentro dos 2 minutos
		assertEquals(0, events(id, "OFFLINE").size());

		evaluateAt("2026-11-05T12:03:00Z");
		List<Map<String, Object>> offline = events(id, "OFFLINE");
		assertEquals(1, offline.size());
		assertEquals("WARN", offline.get(0).get("level"));
		// a queda vale a partir de 2 minutos depois do último contato, não do momento em que o servidor percebeu
		assertEquals(at("2026-11-05T12:02:00Z"), ((java.sql.Timestamp) offline.get(0).get("occurred_at")).toInstant());

		evaluateAt("2026-11-05T12:30:00Z"); // seguir offline não repete o evento
		assertEquals(1, events(id, "OFFLINE").size());
	}

	@Test
	void whenTheTvReturnsTheOnlineEventSaysForHowLong() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);
		evaluateAt("2026-11-05T12:10:00Z");

		clock.freeze("2026-11-05T14:30:00Z");
		heartbeat(token);

		assertEquals(List.of("ACTIVATED", "ONLINE", "OFFLINE", "ONLINE"), types(id));
		assertEquals("TV voltou a se conectar, depois de 2 h 30 min sem contato", events(id, "ONLINE").get(1).get("message"));
	}

	/** O ciclo pode não ter rodado: a história da TV precisa ficar completa mesmo assim. */
	@Test
	void aSilenceThatTheCycleNeverSawIsStillRecordedWhenTheTvReturns() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);

		clock.freeze("2026-11-05T13:00:00Z");
		heartbeat(token);

		List<Map<String, Object>> offline = events(id, "OFFLINE");
		assertEquals(1, offline.size());
		assertEquals(at("2026-11-05T12:02:00Z"), ((java.sql.Timestamp) offline.get(0).get("occurred_at")).toInstant());
		assertEquals(2, events(id, "ONLINE").size());
	}

	@Test
	void recordsPlayerVersionChanges() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);

		clock.freeze("2026-11-05T12:00:30Z");
		heartbeat(token, Map.of("playerVersion", "0.5.0"));

		List<Map<String, Object>> updates = events(id, "PLAYER_UPDATED");
		assertEquals(1, updates.size());
		assertEquals("Player atualizado de 0.4.0 para 0.5.0", updates.get(0).get("message"));
	}

	@Test
	void recordsWhatTheTvShowsOnlyWhenItChanges() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze(T0);
		String token = activeTv("TV", base);
		long id = deviceId("TV");
		heartbeat(token);

		// uma campanha passa a valer: o conteúdo muda e o histórico diz qual
		mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Happy hour", "playlistId", promo, "targets", List.of(Map.of("type", "ALL")))))))
				.andExpect(status().isCreated());
		clock.freeze("2026-11-05T12:00:30Z");
		heartbeat(token);
		clock.freeze("2026-11-05T12:01:00Z");
		heartbeat(token); // sem mudança: nada novo

		List<Map<String, Object>> changes = events(id, "PLAYLIST_CHANGED");
		assertEquals(2, changes.size());
		assertEquals("Exibindo a playlist \"Promo\" da campanha \"Happy hour\"", changes.get(1).get("message"));
	}

	@Test
	void theTimelineIsServedNewestFirstWithFiltersAndPaging() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token, Map.of("events", List.of(
				Map.of("level", "ERROR", "message", "erro 1", "at", "2026-11-05T11:59:00Z"),
				Map.of("level", "WARN", "message", "aviso 1", "at", "2026-11-05T11:59:10Z"),
				Map.of("level", "ERROR", "message", "erro 2", "at", "2026-11-05T11:59:20Z"))));

		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/events")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].type").value("PLAYER_LOG"))
				.andExpect(jsonPath("$[*].message", org.hamcrest.Matchers.hasItems("erro 1", "aviso 1", "erro 2")));
		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/events?level=error")))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].message", contains("erro 2", "erro 1")));
		mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/events?type=ACTIVATED")))
				.andExpect(jsonPath("$", hasSize(1)));

		String firstPage = mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/events?limit=2"))).andReturn().getResponse().getContentAsString();
		JsonNode page = json.readTree(firstPage);
		assertEquals(2, page.size());
		long lastId = page.get(1).get("id").asLong();
		String secondPage = mvc.perform(asAdmin(get("/api/v1/devices/" + id + "/events?limit=2&beforeId=" + lastId))).andReturn().getResponse().getContentAsString();
		assertTrue(json.readTree(secondPage).get(0).get("id").asLong() < lastId);

		mvc.perform(asAdmin(get("/api/v1/devices/999999/events"))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/devices/" + id + "/events")).andExpect(status().isUnauthorized());
	}

	// ---------- ações do painel ----------

	@Test
	void panelActionsLeaveTheirMarkOnTheTimeline() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);

		mvc.perform(asAdmin(post("/api/v1/devices/" + id + "/update-check"))).andExpect(status().isOk());
		mvc.perform(asAdmin(put("/api/v1/devices/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "status", "disabled"))))).andExpect(status().isOk());
		mvc.perform(asAdmin(put("/api/v1/devices/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "status", "active"))))).andExpect(status().isOk());

		List<String> types = types(id);
		assertTrue(types.containsAll(List.of("UPDATE_REQUESTED", "DISABLED", "ENABLED")), types.toString());
	}

	@Test
	void bulkActionsAreRecordedToo() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);

		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "DISABLE", "deviceIds", List.of(id)))))).andExpect(status().isOk());
		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "ENABLE", "deviceIds", List.of(id)))))).andExpect(status().isOk());
		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "CHECK_UPDATE", "deviceIds", List.of(id)))))).andExpect(status().isOk());

		assertTrue(types(id).containsAll(List.of("DISABLED", "ENABLED", "UPDATE_REQUESTED")));
	}

	@Test
	void deletingATvDeletesItsHistory() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);

		mvc.perform(asAdmin(delete("/api/v1/devices/" + id))).andExpect(status().isNoContent());

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM device_events", Integer.class));
	}

	// ---------- o que o Player relata ----------

	@Test
	void storesTheMessagesThePlayerReports() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");

		heartbeat(token, Map.of("events", List.of(
				Map.of("level", "ERROR", "message", "  Falha ao baixar a mídia  ", "at", "2026-11-05T11:58:00Z"),
				Map.of("level", "weird", "message", "nível desconhecido vira INFO"),
				Map.of("level", "WARN", "message", "   "),
				Map.of("level", "WARN", "message", "tempo no futuro", "at", "2030-01-01T00:00:00Z")))).andExpect(status().isOk());

		List<Map<String, Object>> logs = events(id, "PLAYER_LOG");
		assertEquals(3, logs.size()); // a vazia foi descartada
		assertEquals("Falha ao baixar a mídia", logs.get(0).get("message"));
		assertEquals("ERROR", logs.get(0).get("level"));
		assertEquals(at("2026-11-05T11:58:00Z"), ((java.sql.Timestamp) logs.get(0).get("occurred_at")).toInstant());
		assertEquals("INFO", logs.get(1).get("level"));
		// um horário absurdo (relógio da TV errado) vira o horário do servidor
		assertEquals(at(T0), ((java.sql.Timestamp) logs.get(2).get("occurred_at")).toInstant());
	}

	@Test
	void aLongMessageIsTruncated() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");

		heartbeat(token, Map.of("events", List.of(Map.of("level", "ERROR", "message", "x".repeat(500))))).andExpect(status().isOk());
		heartbeat(token, Map.of("events", List.of(Map.of("level", "ERROR", "message", "y".repeat(501))))).andExpect(status().isBadRequest());

		assertEquals(500, ((String) events(id, "PLAYER_LOG").get(0).get("message")).length());
	}

	/** O Player reenvia o que não teve confirmação: o mesmo evento não pode aparecer duas vezes. */
	@Test
	void aResentHeartbeatDoesNotDuplicateEvents() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		Map<String, Object> extra = Map.of("events", List.of(Map.of("level", "ERROR", "message", "mesmo erro", "at", "2026-11-05T11:59:00Z")));

		heartbeat(token, extra);
		heartbeat(token, extra);

		assertEquals(1, events(id, "PLAYER_LOG").size());
	}

	@Test
	void tooManyEventsInOneHeartbeatAreRefused() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		List<Map<String, Object>> many = new ArrayList<>();
		for (int i = 0; i < 31; i++) {
			many.add(Map.of("level", "INFO", "message", "m" + i));
		}

		heartbeat(token, Map.of("events", many)).andExpect(status().isBadRequest());
	}

	@Test
	void anOldPlayerThatSendsNothingExtraStillWorks() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);

		mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"playerVersion\":\"0.3.0\",\"configVersion\":0}"))
				.andExpect(status().isOk());
	}

	@Test
	void theDeviceListShowsWhatThePlayerSaysIsOnScreen() throws Exception {
		long media = uploadPng("Promoção");
		long base = createPlaylist("Base", List.of(media));
		clock.freeze(T0);
		String token = activeTv("TV", base);

		heartbeat(token, Map.of("currentMediaId", media));

		mvc.perform(asAdmin(get("/api/v1/devices")))
				.andExpect(jsonPath("$[0].currentMediaId").value(media))
				.andExpect(jsonPath("$[0].currentMediaName").value("Promoção"));
	}

	// ---------- exibições ----------

	@Test
	void countsPlaysPerMediaAndDayAndAccumulatesAcrossHeartbeats() throws Exception {
		long a = uploadPng("A");
		long b = uploadPng("B");
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");

		heartbeat(token, Map.of("plays", List.of(Map.of("mediaId", a, "count", 3, "seconds", 30), Map.of("mediaId", b, "count", 1, "seconds", 10))));
		clock.freeze("2026-11-05T12:00:30Z");
		heartbeat(token, Map.of("plays", List.of(Map.of("mediaId", a, "count", 2, "seconds", 20))));

		assertEquals(5, jdbc.queryForObject("SELECT plays FROM media_plays WHERE device_id = ? AND media_id = ?", Integer.class, id, a));
		assertEquals(50, jdbc.queryForObject("SELECT seconds FROM media_plays WHERE device_id = ? AND media_id = ?", Long.class, id, a));
		assertEquals(1, jdbc.queryForObject("SELECT plays FROM media_plays WHERE device_id = ? AND media_id = ?", Integer.class, id, b));
	}

	@Test
	void playsOfDeletedOrInvalidMediaDoNotBreakTheHeartbeat() throws Exception {
		long a = uploadPng("A");
		clock.freeze(T0);
		String token = activeTv("TV", null);

		heartbeat(token, Map.of("plays", List.of(
				Map.of("mediaId", 999999, "count", 5, "seconds", 50),
				Map.of("mediaId", a, "count", 0, "seconds", 10),
				Map.of("mediaId", a, "count", -3, "seconds", 10)))).andExpect(status().isOk());

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM media_plays", Integer.class));
	}

	@Test
	void theDayBelongsToTheReportTimezone() throws Exception {
		long a = uploadPng("A");
		String token;
		clock.freeze("2026-11-05T02:00:00Z"); // 23:00 de 04/11 em São Paulo
		token = activeTv("TV", null);

		heartbeat(token, Map.of("plays", List.of(Map.of("mediaId", a, "count", 1, "seconds", 10))));

		assertEquals("2026-11-04", jdbc.queryForObject("SELECT to_char(day, 'YYYY-MM-DD') FROM media_plays", String.class));
	}

	// ---------- alertas ----------

	@Test
	void anOfflineAlertOpensOnlyAfterTheThresholdAndOnlyOnce() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV Vitrine", null);
		long id = deviceId("TV Vitrine");
		heartbeat(token);

		evaluateAt("2026-11-05T12:09:00Z");
		assertEquals(0, openAlerts(id, "OFFLINE")); // 9 min < 10 min

		evaluateAt("2026-11-05T12:10:00Z");
		assertEquals(1, openAlerts(id, "OFFLINE"));
		evaluateAt("2026-11-05T13:00:00Z");
		assertEquals(1, allAlerts(id, "OFFLINE")); // continua o mesmo alerta
		assertTrue(jdbc.queryForObject("SELECT message FROM alerts WHERE device_id = ?", String.class, id).contains("TV Vitrine"));
	}

	@Test
	void theOfflineAlertResolvesWhenTheTvReturns() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");
		assertEquals(1, openAlerts(id, "OFFLINE"));

		clock.freeze("2026-11-05T12:31:00Z");
		heartbeat(token);
		evaluateAt("2026-11-05T12:31:30Z");

		assertEquals(0, openAlerts(id, "OFFLINE"));
		assertEquals(1, allAlerts(id, "OFFLINE"));
		assertNotNull(jdbc.queryForObject("SELECT resolved_at FROM alerts WHERE device_id = ?", java.sql.Timestamp.class, id));
	}

	@Test
	void aNewOutageOpensANewAlert() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");
		clock.freeze("2026-11-05T12:31:00Z");
		heartbeat(token);
		evaluateAt("2026-11-05T12:31:30Z");

		evaluateAt("2026-11-05T14:00:00Z");

		assertEquals(2, allAlerts(id, "OFFLINE"));
		assertEquals(1, openAlerts(id, "OFFLINE"));
	}

	@Test
	void theOfflineThresholdIsConfigurable() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"offlineAfterMinutes\": 3}"))).andExpect(status().isOk());

		evaluateAt("2026-11-05T12:03:00Z");

		assertEquals(1, openAlerts(id, "OFFLINE"));
	}

	@Test
	void aTvThatNeverConnectedGetsAnAlertAfterTheDeadline() throws Exception {
		clock.freeze(T0);
		activeTv("TV nova", null);
		long id = deviceId("TV nova");
		jdbc.update("UPDATE devices SET activated_at = ? WHERE id = ?", java.sql.Timestamp.from(at("2026-11-04T10:00:00Z")), id);

		evaluateAt("2026-11-05T09:00:00Z"); // 23 h depois
		assertEquals(0, openAlerts(id, "NEVER_CONNECTED"));

		evaluateAt("2026-11-05T10:00:00Z"); // 24 h depois
		assertEquals(1, openAlerts(id, "NEVER_CONNECTED"));
	}

	@Test
	void theNeverConnectedAlertResolvesOnTheFirstContact() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV nova", null);
		long id = deviceId("TV nova");
		jdbc.update("UPDATE devices SET activated_at = ? WHERE id = ?", java.sql.Timestamp.from(at("2026-11-03T10:00:00Z")), id);
		evaluateAt(T0);
		assertEquals(1, openAlerts(id, "NEVER_CONNECTED"));

		heartbeat(token);
		evaluateAt("2026-11-05T12:00:30Z");

		assertEquals(0, openAlerts(id, "NEVER_CONNECTED"));
	}

	@Test
	void repeatedPlayerErrorsOpenAnAlertAndItClosesWhenTheyStop() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		List<Map<String, Object>> errors = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			errors.add(Map.of("level", "ERROR", "message", "erro " + i, "at", "2026-11-05T11:58:0" + i + "Z"));
		}
		heartbeat(token, Map.of("events", errors));
		evaluateAt("2026-11-05T12:00:10Z");
		assertEquals(0, openAlerts(id, "PLAYER_ERRORS")); // 4 < 5

		clock.freeze("2026-11-05T12:00:30Z");
		heartbeat(token, Map.of("events", List.of(Map.of("level", "ERROR", "message", "erro 4", "at", "2026-11-05T11:59:00Z"))));
		evaluateAt("2026-11-05T12:00:40Z");
		assertEquals(1, openAlerts(id, "PLAYER_ERRORS"));

		// 15 minutos depois: os erros saíram da janela de 10 minutos; a TV segue online
		clock.freeze("2026-11-05T12:15:00Z");
		heartbeat(token);
		evaluateAt("2026-11-05T12:15:10Z");
		assertEquals(0, openAlerts(id, "PLAYER_ERRORS"));
	}

	@Test
	void warningsDoNotCountAsErrorsForTheAlert() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		List<Map<String, Object>> warnings = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			warnings.add(Map.of("level", "WARN", "message", "aviso " + i, "at", "2026-11-05T11:59:00Z"));
		}
		heartbeat(token, Map.of("events", warnings));

		evaluateAt("2026-11-05T12:00:10Z");

		assertEquals(0, openAlerts(id, "PLAYER_ERRORS"));
	}

	@Test
	void disablingATvClosesItsAlerts() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");
		assertEquals(1, openAlerts(id, "OFFLINE"));

		mvc.perform(asAdmin(put("/api/v1/devices/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "status", "disabled"))))).andExpect(status().isOk());
		evaluateAt("2026-11-05T12:31:00Z");

		assertEquals(0, openAlerts(id, "OFFLINE"));
	}

	@Test
	void deletingATvDeletesItsAlerts() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");

		mvc.perform(asAdmin(delete("/api/v1/devices/" + id))).andExpect(status().isNoContent());

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM alerts", Integer.class));
	}

	@Test
	void theAlertListShowsOpenOnesFirstAndCanBeFiltered() throws Exception {
		clock.freeze(T0);
		String first = activeTv("TV A", null);
		activeTv("TV B", null);
		heartbeat(first);
		evaluateAt("2026-11-05T12:30:00Z"); // TV A offline (alerta) e TV B nunca conectou (ainda sem prazo)
		clock.freeze("2026-11-05T12:31:00Z");
		heartbeat(first); // TV A volta: alerta resolvido
		evaluateAt("2026-11-05T12:31:30Z");
		jdbc.update("UPDATE devices SET activated_at = ? WHERE name = 'TV B'", java.sql.Timestamp.from(at("2026-11-01T00:00:00Z")));
		evaluateAt("2026-11-05T12:32:00Z");

		mvc.perform(asAdmin(get("/api/v1/alerts")))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].open").value(true))
				.andExpect(jsonPath("$[0].type").value("NEVER_CONNECTED"))
				.andExpect(jsonPath("$[0].deviceName").value("TV B"))
				.andExpect(jsonPath("$[1].open").value(false))
				.andExpect(jsonPath("$[1].type").value("OFFLINE"));
		mvc.perform(asAdmin(get("/api/v1/alerts?status=open"))).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
	}

	@Test
	void theSummaryCountsOpenAlerts() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");

		mvc.perform(asAdmin(get("/api/v1/summary"))).andExpect(jsonPath("$.openAlerts").value(1));
	}

	// ---------- configurações ----------

	@Test
	void settingsStartWithSensibleDefaults() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/alerts/settings")))
				.andExpect(jsonPath("$.offlineAfterMinutes").value(10))
				.andExpect(jsonPath("$.neverConnectedAfterHours").value(24))
				.andExpect(jsonPath("$.errorBurstCount").value(5))
				.andExpect(jsonPath("$.eventsRetentionDays").value(90))
				.andExpect(jsonPath("$.notifyOnResolve").value(true))
				.andExpect(jsonPath("$.webhookConfigured").value(false))
				.andExpect(jsonPath("$.webhookHint").doesNotExist());
	}

	@Test
	void settingsAreValidated() throws Exception {
		for (String body : new String[] {
				"{\"offlineAfterMinutes\": 0}", "{\"offlineAfterMinutes\": 1}", "{\"offlineAfterMinutes\": 5000}", "{\"neverConnectedAfterHours\": 0}",
				"{\"errorBurstCount\": 0}", "{\"errorBurstCount\": 101}", "{\"eventsRetentionDays\": 3}", "{\"webhookUrl\": \"ftp://x/y\"}" }) {
			mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content(body)))
					.andExpect(status().isBadRequest());
		}
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"offlineAfterMinutes\": 5000}")))
				.andExpect(jsonPath("$.error").value("O tempo sem contato para alertar precisa estar entre 2 e 1440"));
	}

	@Test
	void theWebhookAddressIsASecretAndNeverComesBack() throws Exception {
		String secret = "http://127.0.0.1:9/services/T000/B000/SECRETSECRET1234";

		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("webhookUrl", secret)))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.webhookConfigured").value(true))
				.andExpect(jsonPath("$.webhookHint").value("127.0.0.1/…1234"));
		String body = mvc.perform(asAdmin(get("/api/v1/alerts/settings"))).andReturn().getResponse().getContentAsString();
		assertFalse(body.contains("SECRET"));

		// campo ausente não mexe; vazio remove
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"offlineAfterMinutes\": 20}")))
				.andExpect(jsonPath("$.webhookConfigured").value(true));
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"webhookUrl\": \"\"}")))
				.andExpect(jsonPath("$.webhookConfigured").value(false));
	}

	// ---------- webhook ----------

	private String startWebhook(List<String> received, AtomicInteger status) throws Exception {
		webhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		webhookServer.createContext("/hook", exchange -> {
			received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(status.get(), -1);
			exchange.close();
		});
		webhookServer.start();
		return "http://127.0.0.1:" + webhookServer.getAddress().getPort() + "/hook";
	}

	private void setWebhook(String url) throws Exception {
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("webhookUrl", url))))).andExpect(status().isOk());
	}

	@Test
	void anOpenedAlertIsSentToTheWebhookWithReadableTextAndData() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		setWebhook(startWebhook(received, new AtomicInteger(204)));
		clock.freeze(T0);
		String token = activeTv("TV Vitrine", null);
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");

		int sent = inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));

		assertEquals(1, sent);
		JsonNode payload = json.readTree(received.get(0));
		assertEquals("alert.opened", payload.get("event").asText());
		assertTrue(payload.get("text").asText().startsWith("⚠️"));
		assertEquals(payload.get("text").asText(), payload.get("content").asText()); // Slack lê "text"; Discord lê "content"
		assertEquals("OFFLINE", payload.get("alert").get("type").asText());
		assertEquals("TV Vitrine", payload.get("alert").get("deviceName").asText());
	}

	@Test
	void theSameAlertIsNeverSentTwice() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		setWebhook(startWebhook(received, new AtomicInteger(200)));
		clock.freeze(T0);
		String token = activeTv("TV", null);
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");

		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));
		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));

		assertEquals(1, received.size());
	}

	@Test
	void resolutionIsAnnouncedWhenEnabledAndSilentWhenNot() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		setWebhook(startWebhook(received, new AtomicInteger(200)));
		clock.freeze(T0);
		String token = activeTv("TV", null);
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");
		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));

		clock.freeze("2026-11-05T12:31:00Z");
		heartbeat(token);
		evaluateAt("2026-11-05T12:31:30Z");
		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));

		assertEquals(2, received.size());
		assertEquals("alert.resolved", json.readTree(received.get(1)).get("event").asText());
		assertTrue(json.readTree(received.get(1)).get("text").asText().startsWith("✅ Resolvido"));

		// com o aviso de resolução desligado, só o de abertura sai
		received.clear();
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"notifyOnResolve\": false}")));
		evaluateAt("2026-11-05T14:00:00Z"); // nova queda -> novo alerta
		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));
		clock.freeze("2026-11-05T14:05:00Z");
		heartbeat(token);
		evaluateAt("2026-11-05T14:05:30Z");
		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));
		assertEquals(1, received.size());
		assertEquals("alert.opened", json.readTree(received.get(0)).get("event").asText());
	}

	@Test
	void withoutAWebhookNothingIsSentAndNothingIsMarked() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");

		assertEquals(0, inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant())));

		assertNull(jdbc.queryForObject("SELECT notified_at FROM alerts", java.sql.Timestamp.class));
	}

	@Test
	void aWebhookThatFailsIsRetriedAndEventuallyGivenUp() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		AtomicInteger status = new AtomicInteger(500);
		setWebhook(startWebhook(received, status));
		clock.freeze(T0);
		String token = activeTv("TV", null);
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z");

		assertEquals(0, inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(at("2026-11-05T12:31:00Z"))));
		assertNull(jdbc.queryForObject("SELECT notified_at FROM alerts", java.sql.Timestamp.class)); // tentará de novo

		status.set(200);
		assertEquals(1, inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(at("2026-11-05T12:32:00Z")))); // o webhook voltou
		assertNotNull(jdbc.queryForObject("SELECT notified_at FROM alerts", java.sql.Timestamp.class));
	}

	@Test
	void afterAnHourOfFailuresTheNotificationIsGivenUp() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		setWebhook(startWebhook(received, new AtomicInteger(500)));
		clock.freeze(T0);
		String token = activeTv("TV", null);
		heartbeat(token);
		evaluateAt("2026-11-05T12:30:00Z"); // alerta aberto às 12:30

		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(at("2026-11-05T13:31:00Z"))); // mais de 1 h depois

		assertNotNull(jdbc.queryForObject("SELECT notified_at FROM alerts", java.sql.Timestamp.class));
	}

	@Test
	void theTestButtonSendsAMessageAndReportsTheOutcome() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		AtomicInteger status = new AtomicInteger(200);
		setWebhook(startWebhook(received, status));

		mvc.perform(asAdmin(post("/api/v1/alerts/settings/test"))).andExpect(jsonPath("$.ok").value(true));
		assertEquals("test", json.readTree(received.get(0)).get("event").asText());

		status.set(404);
		mvc.perform(asAdmin(post("/api/v1/alerts/settings/test")))
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("O webhook respondeu HTTP 404"));
	}

	@Test
	void testingWithoutAWebhookIsA400() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/alerts/settings/test"))).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/alerts/settings/test")).andExpect(status().isUnauthorized());
	}

	// ---------- retenção ----------

	@Test
	void oldHistoryIsPurgedButRecentHistoryStays() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV", null);
		long id = deviceId("TV");
		heartbeat(token);
		inTenant(DEFAULT_TENANT, () -> monitoring.record(id, com.digitalsignage.backend.monitoring.EventType.PLAYER_LOG, com.digitalsignage.backend.monitoring.EventLevel.INFO, "antigo", at("2026-06-01T00:00:00Z")));

		int removed = inTenant(DEFAULT_TENANT, () -> monitoring.purge(at(T0)));

		assertEquals(1, removed);
		assertEquals(0, events(id, "PLAYER_LOG").size());
		assertEquals(1, events(id, "ONLINE").size());
	}

}
