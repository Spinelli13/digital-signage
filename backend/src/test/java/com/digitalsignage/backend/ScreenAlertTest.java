package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

/** "Alertar agora": o aviso em tela cheia chega à TV certa pelo heartbeat, tem prazo, pode ser encerrado e respeita o cliente. */
class ScreenAlertTest extends AbstractApiTest {

	private record Tv(String token, long id) {
	}

	private Tv activeTv(String name, String panelToken) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.14.70")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(withToken(panelToken, post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		return new Tv(token, id);
	}

	private Tv activeTv(String name) throws Exception {
		return activeTv(name, adminToken());
	}

	private JsonNode heartbeat(Tv tv) throws Exception {
		return json.readTree(mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playerVersion", "0.14.70", "configVersion", 0))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private ResultActions alert(String token, Map<String, Object> body) throws Exception {
		return mvc.perform(withToken(token, post("/api/v1/screen-alerts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private Map<String, Object> everyone(String message) {
		Map<String, Object> body = new HashMap<>();
		body.put("message", message);
		body.put("tone", "URGENT");
		body.put("targetType", "ALL");
		return body;
	}

	private long create(String token, Map<String, Object> body) throws Exception {
		return idOf(alert(token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private void end(String token, long id) throws Exception {
		mvc.perform(withToken(token, post("/api/v1/screen-alerts/" + id + "/end"))).andExpect(status().isOk());
	}

	@Test
	void everyTvGetsTheAlertOnTheNextHeartbeatAndItDisappearsWhenEnded() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		String admin = adminToken();
		Tv a = activeTv("TV A");
		Tv b = activeTv("TV B");
		assertTrue(heartbeat(a).get("alert").isNull());

		Map<String, Object> body = everyone("Evacuação: sigam para a saída.");
		body.put("title", "ATENÇÃO");
		body.put("durationMinutes", 10);
		long id = create(admin, body);

		for (Tv tv : new Tv[] { a, b }) {
			JsonNode alert = heartbeat(tv).get("alert");
			assertEquals(id, alert.get("id").asLong());
			assertEquals("ATENÇÃO", alert.get("title").asText());
			assertEquals("Evacuação: sigam para a saída.", alert.get("message").asText());
			assertEquals("URGENT", alert.get("tone").asText());
			assertEquals(600, alert.get("remainingSeconds").asLong());
		}

		clock.freeze("2026-11-05T15:04:00Z");
		assertEquals(360, heartbeat(a).get("alert").get("remainingSeconds").asLong());

		end(admin, id);
		assertTrue(heartbeat(a).get("alert").isNull());
		assertTrue(heartbeat(b).get("alert").isNull());
	}

	@Test
	void theAlertEndsByItselfWhenTheTimeIsUp() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		Tv tv = activeTv("TV A");
		Map<String, Object> body = everyone("Promoção relâmpago");
		body.put("durationMinutes", 5);
		long id = create(adminToken(), body);

		clock.freeze("2026-11-05T15:04:59Z");
		assertEquals(1, heartbeat(tv).get("alert").get("remainingSeconds").asLong());
		clock.freeze("2026-11-05T15:05:00Z");
		assertTrue(heartbeat(tv).get("alert").isNull());

		mvc.perform(asAdmin(get("/api/v1/screen-alerts"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].state").value("EXPIRED")).andExpect(jsonPath("$[0].devices").doesNotExist());
	}

	@Test
	void withoutADurationTheAlertLastsFifteenMinutes() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		Tv tv = activeTv("TV A");
		create(adminToken(), everyone("Aviso"));

		assertEquals(900, heartbeat(tv).get("alert").get("remainingSeconds").asLong());
	}

	@Test
	void aGroupOrASingleTvOnlyReachesWhoWasChosen() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		String admin = adminToken();
		Tv recepcao = activeTv("Recepção");
		Tv sala = activeTv("Sala de espera");
		Tv loja = activeTv("Loja");
		long group = idOf(mvc.perform(withToken(admin, post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Clínica", "deviceIds", java.util.List.of(recepcao.id(), sala.id()))))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		Map<String, Object> toGroup = everyone("Fechamos mais cedo hoje");
		toGroup.put("targetType", "GROUP");
		toGroup.put("targetId", group);
		long groupAlert = create(admin, toGroup);
		assertEquals("Fechamos mais cedo hoje", heartbeat(recepcao).get("alert").get("message").asText());
		assertEquals("Fechamos mais cedo hoje", heartbeat(sala).get("alert").get("message").asText());
		assertTrue(heartbeat(loja).get("alert").isNull());
		end(admin, groupAlert);

		Map<String, Object> toOne = everyone("Chame o gerente");
		toOne.put("targetType", "DEVICE");
		toOne.put("targetId", loja.id());
		create(admin, toOne);
		assertEquals("Chame o gerente", heartbeat(loja).get("alert").get("message").asText());
		assertTrue(heartbeat(recepcao).get("alert").isNull());
		assertTrue(heartbeat(sala).get("alert").isNull());
	}

	@Test
	void theMostUrgentAlertWinsAndTheOtherReturnsWhenItEnds() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		String admin = adminToken();
		Tv tv = activeTv("TV A");
		Map<String, Object> info = everyone("Hoje tem bolo");
		info.put("tone", "INFO");
		create(admin, info);
		clock.freeze("2026-11-05T15:01:00Z");
		long urgent = create(admin, everyone("Saiam do prédio"));
		clock.freeze("2026-11-05T15:02:00Z");
		Map<String, Object> warning = everyone("Chuva forte");
		warning.put("tone", "WARNING");
		create(admin, warning);

		assertEquals("Saiam do prédio", heartbeat(tv).get("alert").get("message").asText());
		end(admin, urgent);
		assertEquals("Chuva forte", heartbeat(tv).get("alert").get("message").asText());
	}

	@Test
	void theRequestIsValidated() throws Exception {
		String admin = adminToken();
		Map<String, Object> blank = everyone("   ");
		alert(admin, blank).andExpect(status().isBadRequest());

		alert(admin, everyone("x".repeat(301))).andExpect(status().isBadRequest());

		Map<String, Object> longTitle = everyone("Aviso");
		longTitle.put("title", "t".repeat(61));
		alert(admin, longTitle).andExpect(status().isBadRequest());

		for (int minutes : new int[] { 0, -5, 1441 }) {
			Map<String, Object> body = everyone("Aviso");
			body.put("durationMinutes", minutes);
			alert(admin, body).andExpect(status().isBadRequest());
		}

		Map<String, Object> missingGroup = everyone("Aviso");
		missingGroup.put("targetType", "GROUP");
		missingGroup.put("targetId", 9999);
		alert(admin, missingGroup).andExpect(status().isNotFound());

		Map<String, Object> noTarget = everyone("Aviso");
		noTarget.put("targetType", "DEVICE");
		alert(admin, noTarget).andExpect(status().isNotFound());

		Map<String, Object> noTone = everyone("Aviso");
		noTone.remove("tone");
		alert(admin, noTone).andExpect(status().isBadRequest());
	}

	@Test
	void thereIsACeilingOfFiveAlertsOnTheAir() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		String admin = adminToken();
		long first = 0;
		for (int i = 0; i < 5; i++) {
			long id = create(admin, everyone("Aviso " + i));
			first = i == 0 ? id : first;
		}
		alert(admin, everyone("Aviso 6")).andExpect(status().isConflict());

		end(admin, first);
		create(admin, everyone("Aviso 6"));
	}

	@Test
	void endingTwiceIsHarmlessAndAnUnknownAlertIs404() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		String admin = adminToken();
		long id = create(admin, everyone("Aviso"));
		end(admin, id);
		end(admin, id);
		mvc.perform(withToken(admin, post("/api/v1/screen-alerts/99999/end"))).andExpect(status().isNotFound());
	}

	@Test
	void theListShowsStateReachAndWhoDidIt() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		String admin = adminToken();
		activeTv("TV A");
		activeTv("TV B");
		long ended = create(admin, everyone("Primeiro"));
		end(admin, ended);
		clock.freeze("2026-11-05T15:01:00Z");
		create(admin, everyone("Segundo"));

		mvc.perform(withToken(admin, get("/api/v1/screen-alerts"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].message").value("Segundo")).andExpect(jsonPath("$[0].state").value("ACTIVE"))
				.andExpect(jsonPath("$[0].devices").value(2)).andExpect(jsonPath("$[0].targetType").value("ALL"))
				.andExpect(jsonPath("$[0].createdBy").isNotEmpty())
				.andExpect(jsonPath("$[1].message").value("Primeiro")).andExpect(jsonPath("$[1].state").value("ENDED"))
				.andExpect(jsonPath("$[1].endedBy").isNotEmpty()).andExpect(jsonPath("$[1].devices").doesNotExist());
	}

	@Test
	void onlyEditorsAndAboveCanSendOrEndButEveryoneCanSee() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		long tenant = createTenantRow("Cliente Aviso", "interno", "ACTIVE");
		createUserRow(tenant, "viewer-aviso@teste.com", "VIEWER", "senha-forte-1");
		createUserRow(tenant, "editor-aviso@teste.com", "EDITOR", "senha-forte-1");
		String viewer = loginAs("viewer-aviso@teste.com", "senha-forte-1");
		String editor = loginAs("editor-aviso@teste.com", "senha-forte-1");

		alert(viewer, everyone("Aviso")).andExpect(status().isForbidden());
		long id = create(editor, everyone("Aviso do editor"));
		mvc.perform(withToken(viewer, get("/api/v1/screen-alerts"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
		mvc.perform(withToken(viewer, post("/api/v1/screen-alerts/" + id + "/end"))).andExpect(status().isForbidden());
		end(editor, id);
		mvc.perform(post("/api/v1/screen-alerts").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(everyone("Sem login"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anAlertNeverCrossesToAnotherClient() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		long other = createTenantRow("Outro Cliente", "interno", "ACTIVE");
		createUserRow(other, "admin-outro@teste.com", "ADMIN", "senha-forte-1");
		String otherAdmin = loginAs("admin-outro@teste.com", "senha-forte-1");
		Tv mine = activeTv("Minha TV");
		Tv theirs = activeTv("TV do outro", otherAdmin);

		long id = create(adminToken(), everyone("Só dos meus"));

		assertFalse(heartbeat(mine).get("alert").isNull());
		assertTrue(heartbeat(theirs).get("alert").isNull());
		mvc.perform(withToken(otherAdmin, get("/api/v1/screen-alerts"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(withToken(otherAdmin, post("/api/v1/screen-alerts/" + id + "/end"))).andExpect(status().isNotFound());
		// e o outro cliente não consegue mirar numa TV que não é dele
		Map<String, Object> aimed = everyone("Invasão");
		aimed.put("targetType", "DEVICE");
		aimed.put("targetId", mine.id());
		alert(otherAdmin, aimed).andExpect(status().isNotFound());
		assertFalse(heartbeat(mine).get("alert").get("message").asText().equals("Invasão"));
	}
}
