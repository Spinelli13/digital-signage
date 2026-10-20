package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.monitoring.MonitoringService;

/** Relatórios: disponibilidade, inventário, eventos e exibições (JSON e CSV). */
class ReportsTest extends AbstractApiTest {

	private static final String T0 = "2026-11-05T12:00:00Z"; // 09:00 em São Paulo

	@Autowired
	private MonitoringService monitoring;

	private long location(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/locations")
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", name)))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long group(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", name)))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Uma TV de verdade: registra pelo Player e ativa pelo painel. Devolve o token do aparelho. */
	private String activeTv(String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.4.0"))))
				.andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		mvc.perform(asAdmin(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk());
		return token;
	}

	private long deviceId(String name) {
		return jdbc.queryForObject("SELECT id FROM devices WHERE name = ?", Long.class, name);
	}

	private void place(String name, Long locationId, Long groupId, Long playlistId) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		if (locationId != null) {
			body.put("locationId", locationId);
		}
		if (groupId != null) {
			body.put("groupIds", List.of(groupId));
		}
		if (playlistId != null) {
			body.put("currentPlaylistId", playlistId);
		}
		mvc.perform(asAdmin(put("/api/v1/devices/" + deviceId(name))
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))).andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder asDevice(MockHttpServletRequestBuilder request, String token) {
		return request.header("X-Device-Token", token);
	}

	private ResultActions heartbeat(String token, Map<String, Object> extra) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("playerVersion", "0.4.0", "configVersion", 0));
		body.putAll(extra);
		return mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private void evaluateAt(String iso) {
		clock.freeze(iso);
		monitoring.evaluate(clock.instant());
	}

	private String csv(ResultActions result) throws Exception {
		return new String(result.andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	/** TV online às 12:00, caiu às 12:02, voltou às 13:02; agora são 15:02 (3h02 observadas, 1h offline). */
	private String tvWithAnOutage(String name) throws Exception {
		clock.freeze(T0);
		String token = activeTv(name);
		heartbeat(token, Map.of());
		evaluateAt("2026-11-05T12:10:00Z");
		clock.freeze("2026-11-05T13:02:00Z");
		heartbeat(token, Map.of());
		clock.freeze("2026-11-05T15:02:00Z");
		return token;
	}

	// ---------- disponibilidade ----------

	@Test
	void uptimeCountsOnlineAndOfflineTimeInsideTheDay() throws Exception {
		tvWithAnOutage("TV Vitrine");

		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-05&to=2026-11-05")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.devices", hasSize(1)))
				.andExpect(jsonPath("$.devices[0].name").value("TV Vitrine"))
				.andExpect(jsonPath("$.devices[0].onlineSeconds").value(7320))
				.andExpect(jsonPath("$.devices[0].offlineSeconds").value(3600))
				.andExpect(jsonPath("$.devices[0].observedSeconds").value(10920))
				.andExpect(jsonPath("$.devices[0].uptimePercent").value(67.0))
				.andExpect(jsonPath("$.devices[0].incidents").value(1))
				.andExpect(jsonPath("$.fleetUptimePercent").value(67.0));
	}

	@Test
	void theDayIsTheReportTimezoneDayNotUtc() throws Exception {
		tvWithAnOutage("TV");

		// 05/11 em São Paulo começa às 03:00Z: os eventos de 12:00Z-13:02Z não pertencem ao dia 04/11 (já terminado às 03:00Z)
		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-04&to=2026-11-04")))
				.andExpect(jsonPath("$.devices[0].observedSeconds").value(0))
				.andExpect(jsonPath("$.devices[0].uptimePercent").doesNotExist())
				.andExpect(jsonPath("$.devices[0].incidents").value(0))
				.andExpect(jsonPath("$.fleetUptimePercent").doesNotExist());
	}

	@Test
	void uptimeDefaultsToTheLastSevenDays() throws Exception {
		tvWithAnOutage("TV");

		mvc.perform(asAdmin(get("/api/v1/reports/uptime")))
				.andExpect(jsonPath("$.to").value("2026-11-05"))
				.andExpect(jsonPath("$.from").value("2026-10-30"));
	}

	@Test
	void devicesThatWereNeverActivatedAreNotInTheUptimeReport() throws Exception {
		tvWithAnOutage("TV ativa");
		mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", UUID.randomUUID().toString().replace("-", "") + "00", "playerVersion", "0.4.0"))));

		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.devices", hasSize(1)));
	}

	@Test
	void uptimeCanBeFilteredByLocationAndGroup() throws Exception {
		tvWithAnOutage("TV Centro");
		tvWithAnOutage("TV Norte");
		long centro = location("Centro");
		long vitrines = group("Vitrines");
		place("TV Centro", centro, vitrines, null);

		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-05&to=2026-11-05&locationId=" + centro)))
				.andExpect(jsonPath("$.devices", hasSize(1)))
				.andExpect(jsonPath("$.devices[0].name").value("TV Centro"))
				.andExpect(jsonPath("$.devices[0].location").value("Centro"));
		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-05&to=2026-11-05&groupId=" + vitrines)))
				.andExpect(jsonPath("$.devices[*].name", contains("TV Centro")));
		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.devices", hasSize(2)));
	}

	@Test
	void aDisabledTvDoesNotCountAgainstAvailability() throws Exception {
		clock.freeze(T0);
		String token = activeTv("TV");
		heartbeat(token, Map.of());
		clock.freeze("2026-11-05T12:01:00Z");
		mvc.perform(asAdmin(put("/api/v1/devices/" + deviceId("TV")).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "status", "disabled"))))).andExpect(status().isOk());
		evaluateAt("2026-11-05T15:00:00Z"); // desativada o tempo todo: nada de queda

		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.devices[0].offlineSeconds").value(0))
				.andExpect(jsonPath("$.devices[0].onlineSeconds").value(60))
				.andExpect(jsonPath("$.devices[0].uptimePercent").value(100.0))
				.andExpect(jsonPath("$.devices[0].incidents").value(0));
	}

	@Test
	void uptimeAsCsvOpensInSpreadsheetsInPortuguese() throws Exception {
		tvWithAnOutage("TV Vitrine");

		ResultActions result = mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-05&to=2026-11-05&format=csv")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/csv"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("disponibilidade-2026-11-05-a-2026-11-05.csv")));
		String text = csv(result);

		assertTrue(text.startsWith("﻿TV;Local;Disponibilidade (%);Tempo online;Tempo offline;Tempo observado;Quedas\r\n"), text);
		assertTrue(text.contains("TV Vitrine;;67,0;2 h 2 min;1 h;3 h 2 min;1"), text);
	}

	@Test
	void rangesAreValidated() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2026-11-06&to=2026-11-05")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("A data inicial não pode ser depois da data final"));
		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=2025-01-01&to=2026-11-05")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("O período pode ter no máximo 366 dias"));
		mvc.perform(asAdmin(get("/api/v1/reports/events?from=2026-11-06&to=2026-11-05"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2025-01-01&to=2026-11-05"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/reports/uptime?from=ontem"))).andExpect(status().isBadRequest());
	}

	// ---------- inventário ----------

	@Test
	void inventoryDescribesEachTvAtAGlance() throws Exception {
		long media = uploadPng("Promoção");
		long playlist = createPlaylist("Base", List.of(media));
		long promo = createPlaylist("Promo", List.of(media));
		long centro = location("Centro");
		long vitrines = group("Vitrines");
		String token = tvWithAnOutage("TV Vitrine");
		place("TV Vitrine", centro, vitrines, playlist);
		mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Happy hour", "playlistId", promo, "targets", List.of(Map.of("type", "ALL")))))))
				.andExpect(status().isCreated());
		heartbeat(token, Map.of("currentMediaId", media));

		mvc.perform(asAdmin(get("/api/v1/reports/inventory")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("TV Vitrine"))
				.andExpect(jsonPath("$[0].location").value("Centro"))
				.andExpect(jsonPath("$[0].groups[0]").value("Vitrines"))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].connection").value("ONLINE"))
				.andExpect(jsonPath("$[0].playerVersion").value("0.4.0"))
				.andExpect(jsonPath("$[0].playlist").value("Promo"))
				.andExpect(jsonPath("$[0].campaign").value("Happy hour"))
				.andExpect(jsonPath("$[0].currentMedia").value("Promoção"))
				.andExpect(jsonPath("$[0].activatedAt").exists());
	}

	@Test
	void inventoryAsCsv() throws Exception {
		tvWithAnOutage("TV Vitrine");

		ResultActions result = mvc.perform(asAdmin(get("/api/v1/reports/inventory?format=csv")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inventario-2026-11-05.csv")));
		String text = csv(result);

		assertTrue(text.startsWith("﻿ID;TV;Local;Grupos;Status;Conexão;Último contato;Versão do Player;Playlist exibida;Campanha;Mídia na tela;Ativada em\r\n"), text);
		assertTrue(text.contains(";TV Vitrine;"), text);
		assertTrue(text.contains(";ACTIVE;OFFLINE;"), text); // 15:02, último contato às 13:02
	}

	// ---------- eventos ----------

	@Test
	void theEventReportFiltersByTvTypeLevelAndPeriod() throws Exception {
		String token = tvWithAnOutage("TV A");
		activeTv("TV B");
		heartbeat(token, Map.of("events", List.of(Map.of("level", "ERROR", "message", "falha de rede", "at", "2026-11-05T14:00:00Z"))));
		long a = deviceId("TV A");

		mvc.perform(asAdmin(get("/api/v1/reports/events?from=2026-11-05&to=2026-11-05&deviceId=" + a + "&level=error")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].deviceName").value("TV A"))
				.andExpect(jsonPath("$[0].message").value("falha de rede"));
		mvc.perform(asAdmin(get("/api/v1/reports/events?from=2026-11-05&to=2026-11-05&type=offline")))
				// a queda das 12:02 e a das 13:04, que o servidor só percebeu quando a TV falou de novo às 15:02
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].type", contains("OFFLINE", "OFFLINE")));
		mvc.perform(asAdmin(get("/api/v1/reports/events?from=2026-11-04&to=2026-11-04"))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void theEventReportAsCsvEscapesMessages() throws Exception {
		String token = tvWithAnOutage("TV A");
		heartbeat(token, Map.of("events", List.of(Map.of("level", "ERROR", "message", "=HYPERLINK(\"http://x\")", "at", "2026-11-05T14:00:00Z"))));

		String text = csv(mvc.perform(asAdmin(get("/api/v1/reports/events?from=2026-11-05&to=2026-11-05&format=csv"))).andExpect(status().isOk()));

		assertTrue(text.startsWith("﻿Quando;TV;Tipo;Nível;Mensagem\r\n"), text);
		assertTrue(text.contains("'=HYPERLINK"), text); // a fórmula foi neutralizada
	}

	// ---------- exibições ----------

	private void seedPlays() throws Exception {
		long a = uploadPng("Logo");
		long b = uploadPng("Oferta");
		long centro = location("Centro");
		clock.freeze(T0);
		String tokenA = activeTv("TV A");
		String tokenB = activeTv("TV B");
		place("TV A", centro, null, null);
		heartbeat(tokenA, Map.of("plays", List.of(Map.of("mediaId", a, "count", 10, "seconds", 100), Map.of("mediaId", b, "count", 4, "seconds", 80))));
		heartbeat(tokenB, Map.of("plays", List.of(Map.of("mediaId", a, "count", 6, "seconds", 60))));
		clock.freeze("2026-11-06T12:00:00Z");
		heartbeat(tokenA, Map.of("plays", List.of(Map.of("mediaId", a, "count", 2, "seconds", 20))));
	}

	@Test
	void playsAreGroupedByMediaMostShownFirst() throws Exception {
		seedPlays();

		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-11-05&to=2026-11-06")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.groupBy").value("media"))
				.andExpect(jsonPath("$.totalPlays").value(22))
				.andExpect(jsonPath("$.totalSeconds").value(260))
				.andExpect(jsonPath("$.rows[0].label").value("Logo"))
				.andExpect(jsonPath("$.rows[0].plays").value(18))
				.andExpect(jsonPath("$.rows[0].devices").value(2))
				.andExpect(jsonPath("$.rows[1].label").value("Oferta"))
				.andExpect(jsonPath("$.rows[1].plays").value(4));
	}

	@Test
	void playsCanBeGroupedByTvOrByDay() throws Exception {
		seedPlays();

		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-11-05&to=2026-11-06&groupBy=device")))
				.andExpect(jsonPath("$.rows[0].label").value("TV A"))
				.andExpect(jsonPath("$.rows[0].plays").value(16))
				.andExpect(jsonPath("$.rows[1].label").value("TV B"));
		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-11-05&to=2026-11-06&groupBy=day")))
				.andExpect(jsonPath("$.rows[0].day").value("2026-11-05"))
				.andExpect(jsonPath("$.rows[0].plays").value(20))
				.andExpect(jsonPath("$.rows[1].day").value("2026-11-06"))
				.andExpect(jsonPath("$.rows[1].plays").value(2));
		mvc.perform(asAdmin(get("/api/v1/reports/plays?groupBy=hora"))).andExpect(status().isBadRequest());
	}

	@Test
	void playsCanBeFilteredByPeriodTvAndLocation() throws Exception {
		seedPlays();
		long a = deviceId("TV A");
		long centro = jdbc.queryForObject("SELECT id FROM locations WHERE name = 'Centro'", Long.class);

		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-11-06&to=2026-11-06")))
				.andExpect(jsonPath("$.totalPlays").value(2));
		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-11-05&to=2026-11-06&deviceId=" + a)))
				.andExpect(jsonPath("$.totalPlays").value(16));
		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-11-05&to=2026-11-06&locationId=" + centro)))
				.andExpect(jsonPath("$.totalPlays").value(16));
		mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-10-01&to=2026-10-02")))
				.andExpect(jsonPath("$.totalPlays").value(0))
				.andExpect(jsonPath("$.rows", hasSize(0)));
	}

	@Test
	void playsAsCsv() throws Exception {
		seedPlays();

		String text = csv(mvc.perform(asAdmin(get("/api/v1/reports/plays?from=2026-11-05&to=2026-11-06&format=csv"))).andExpect(status().isOk()));

		assertEquals("﻿Mídia;Exibições;Tempo total;TVs\r\nLogo;18;3 min;2\r\nOferta;4;1 min;1\r\n", text);
	}

	// ---------- acesso ----------

	@Test
	void reportsNeedAnAdmin() throws Exception {
		for (String path : new String[] { "uptime", "inventory", "events", "plays" }) {
			mvc.perform(get("/api/v1/reports/" + path)).andExpect(status().isUnauthorized());
		}
	}

}
