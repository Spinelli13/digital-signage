package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

/** Diagnóstico da TV: o Player manda o relatório, o painel mostra o último e pode pedir um novo. */
class DiagnosticTest extends AbstractApiTest {

	private static final String REPORT = "Vyzzu Player 0.14.70 | Android 11\nInternet: ok\nServidor: alcançável\nPermissão para instalar apps: FALTA";

	private record Tv(String token, long id) {
	}

	private Tv activeTv(String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.14.70")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		return new Tv(token, id);
	}

	private org.springframework.test.web.servlet.ResultActions send(Tv tv, Object body) throws Exception {
		return mvc.perform(post("/api/v1/player/diagnostics").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body)));
	}

	private JsonNode heartbeat(Tv tv) throws Exception {
		return json.readTree(mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playerVersion", "0.14.70", "configVersion", 0))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	@Test
	void thePanelSeesTheLastReportTheTvSentAndNothingBeforeThat() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		Tv tv = activeTv("TV Recepção");

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id() + "/diagnostic"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.report").doesNotExist()).andExpect(jsonPath("$.receivedAt").doesNotExist());

		send(tv, Map.of("report", REPORT, "issues", 1)).andExpect(status().isOk());
		clock.freeze("2026-11-05T15:10:00Z");
		send(tv, Map.of("report", REPORT + "\nmais uma linha", "issues", 2)).andExpect(status().isOk()); // o último substitui o anterior

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id() + "/diagnostic"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.report").value(REPORT + "\nmais uma linha"))
				.andExpect(jsonPath("$.issues").value(2))
				.andExpect(jsonPath("$.receivedAt").value("2026-11-05T15:10:00Z"));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM device_diagnostics", Integer.class));
	}

	@Test
	void thePanelAsksForADiagnosticAndTheTvLearnsAtTheNextHeartbeat() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		Tv tv = activeTv("TV A");
		assertEquals(0, heartbeat(tv).get("diagnosticToken").asLong()); // ninguém pediu

		mvc.perform(asAdmin(post("/api/v1/devices/" + tv.id() + "/diagnostic-request"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.requestedAt").value("2026-11-05T15:00:00Z"));

		long token = heartbeat(tv).get("diagnosticToken").asLong();
		assertEquals(java.time.Instant.parse("2026-11-05T15:00:00Z").toEpochMilli(), token);
		clock.freeze("2026-11-05T16:00:00Z");
		mvc.perform(asAdmin(post("/api/v1/devices/" + tv.id() + "/diagnostic-request"))).andExpect(status().isOk());
		assertTrue(heartbeat(tv).get("diagnosticToken").asLong() > token, "um novo pedido muda o número, e a TV manda outro relatório");
	}

	@Test
	void theReportIsValidatedAndLongOnesAreTrimmed() throws Exception {
		Tv tv = activeTv("TV A");

		send(tv, Map.of("report", "   ")).andExpect(status().isBadRequest());
		send(tv, Map.of("report", REPORT, "issues", 99)).andExpect(status().isBadRequest());
		send(tv, Map.of("report", "x".repeat(8_001))).andExpect(status().isBadRequest());

		send(tv, Map.of("report", "y".repeat(6_000))).andExpect(status().isOk());
		assertEquals(4_000, jdbc.queryForObject("SELECT length(report) FROM device_diagnostics", Integer.class));
	}

	@Test
	void aTvCannotSendWithoutItsToken() throws Exception {
		mvc.perform(post("/api/v1/player/diagnostics").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("report", REPORT)))).andExpect(status().is4xxClientError());
	}

	@Test
	void deletingTheTvDeletesItsReport() throws Exception {
		Tv tv = activeTv("TV A");
		send(tv, Map.of("report", REPORT)).andExpect(status().isOk());

		mvc.perform(asAdmin(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/devices/" + tv.id()))).andExpect(status().isNoContent());

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM device_diagnostics", Integer.class));
	}
}
