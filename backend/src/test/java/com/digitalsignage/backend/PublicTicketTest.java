package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.queue.PublicQueueService;
import com.fasterxml.jackson.databind.JsonNode;

/** Senha pelo celular: o QR Code da fila, a página pública (sem login) e o que a pessoa pode ver e fazer. */
class PublicTicketTest extends AbstractApiTest {

	@Autowired
	private PublicQueueService publicService;

	@BeforeEach
	void forgetTheClients() {
		publicService.forgetClients();
	}

	// ---------- montagem ----------

	private JsonNode createQueue(String name, String prefix, boolean remote) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("prefix", prefix);
		body.put("remoteEnabled", remote);
		return json.readTree(mvc.perform(asAdmin(post("/api/v1/queues").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long counter(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/counters").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", name, "queueIds", List.of())))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Sem login: é como o celular de quem escaneou o QR Code chama. {@code device} é o código que a página guarda no navegador. */
	private ResultActions take(String queueToken, String name, String ip) throws Exception {
		return take(queueToken, name, ip, null);
	}

	private ResultActions take(String queueToken, String name, String ip, String device) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/public/queues/" + queueToken + "/tickets").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(name == null ? Map.of() : Map.of("customerName", name)));
		if (ip != null) {
			request.header("X-Forwarded-For", ip);
		}
		if (device != null) {
			request.header("X-Client-Id", device);
		}
		return mvc.perform(request);
	}

	private JsonNode ticket(String ticketToken) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/public/tickets/" + ticketToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private long callNext(long counterId) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/attendance/counters/" + counterId + "/call-next"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	// ---------- o QR Code da fila ----------

	@Test
	void theAdministratorGetsTheSecretOfTheQrCodeAndOthersDoNot() throws Exception {
		JsonNode queue = createQueue("Geral", "A", true);

		assertTrue(queue.get("remoteEnabled").asBoolean());
		assertTrue(queue.get("publicToken").asText().matches("[0-9a-f]{32}"));
		mvc.perform(asAdmin(get("/api/v1/queues"))).andExpect(jsonPath("$[0].publicToken").value(queue.get("publicToken").asText()));
		mvc.perform(asAdmin(get("/api/v1/attendance/overview"))).andExpect(jsonPath("$.queues[0].publicToken").value(queue.get("publicToken").asText()));

		String email = "editor-qr-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
		createUserRow(2L, email, "EDITOR", "senha-forte-1");
		String editor = loginAs(email, "senha-forte-1");
		// o Editor vê a fila, mas não o segredo do QR Code
		JsonNode queues = json.readTree(mvc.perform(withToken(editor, get("/api/v1/queues"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertEquals("Geral", queues.get(0).get("name").asText());
		assertTrue(queues.get(0).get("publicToken").isNull());
		JsonNode overview = json.readTree(mvc.perform(withToken(editor, get("/api/v1/attendance/overview"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertTrue(overview.get("queues").get(0).get("publicToken").isNull());
	}

	@Test
	void theTokenOfTheQueueIsStableAcrossEditsAndTurningItOffClosesThePage() throws Exception {
		JsonNode queue = createQueue("Geral", "A", true);
		String token = queue.get("publicToken").asText();
		long id = queue.get("id").asLong();

		Map<String, Object> off = new HashMap<>();
		off.put("name", "Geral");
		off.put("prefix", "A");
		off.put("remoteEnabled", false);
		mvc.perform(asAdmin(put("/api/v1/queues/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(off))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.publicToken").value(token)).andExpect(jsonPath("$.remoteEnabled").value(false));

		mvc.perform(get("/api/v1/public/queues/" + token)).andExpect(status().isOk()).andExpect(jsonPath("$.open").value(false));
		take(token, null, "1.1.1.1").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Este QR Code não está mais ativo. Procure o atendimento."));
	}

	// ---------- pegar a senha ----------

	@Test
	void anyoneWithTheLinkSeesTheQueueAndTakesATicketWithoutLoggingIn() throws Exception {
		String token = createQueue("Atendimento geral", "A", true).get("publicToken").asText();

		mvc.perform(get("/api/v1/public/queues/" + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Atendimento geral")).andExpect(jsonPath("$.open").value(true)).andExpect(jsonPath("$.waiting").value(0));

		JsonNode first = json.readTree(take(token, "  Maria ", "10.0.0.1").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		JsonNode second = json.readTree(take(token, null, "10.0.0.2").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		assertEquals("A001", first.get("label").asText());
		assertEquals("A002", second.get("label").asText());
		assertEquals(0, first.get("ahead").asInt());
		assertEquals(1, second.get("ahead").asInt());
		assertEquals("WAITING", first.get("status").asText());
		assertTrue(first.get("ticketToken").asText().matches("[0-9a-f]{32}"));
		assertNotEquals(first.get("ticketToken").asText(), second.get("ticketToken").asText());
		// o nome nunca volta pela área pública
		assertFalse(first.has("customerName"));
		mvc.perform(get("/api/v1/public/queues/" + token)).andExpect(jsonPath("$.waiting").value(2));

		// o atendente vê a senha como pegada pelo celular, com o nome
		JsonNode waiting = json.readTree(mvc.perform(asAdmin(get("/api/v1/attendance/overview"))).andReturn().getResponse().getContentAsString()).get("waiting");
		assertEquals("REMOTE", waiting.get(0).get("source").asText());
		assertEquals("Maria", waiting.get(0).get("customerName").asText());
	}

	@Test
	void theTicketPageFollowsThePositionTheCallAndTheEnd() throws Exception {
		String token = createQueue("Geral", "A", true).get("publicToken").asText();
		long window = counter("Guichê 3");
		String mine = take(token, null, "10.0.0.1").andReturn().getResponse().getContentAsString();
		String ticketToken = json.readTree(mine).get("ticketToken").asText();
		String other = json.readTree(take(token, null, "10.0.0.2").andReturn().getResponse().getContentAsString()).get("ticketToken").asText();

		assertEquals(0, ticket(ticketToken).get("ahead").asInt());
		assertEquals(1, ticket(other).get("ahead").asInt());

		long called = callNext(window);
		JsonNode calling = ticket(ticketToken);
		assertEquals("CALLING", calling.get("status").asText());
		assertEquals("Guichê 3", calling.get("counter").asText());
		assertEquals(1, calling.get("callCount").asInt());
		assertTrue(calling.get("calledAtMs").asLong() > 0);
		assertEquals(0, ticket(other).get("ahead").asInt()); // a outra pessoa avançou

		mvc.perform(asAdmin(post("/api/v1/attendance/tickets/" + called + "/recall"))).andExpect(status().isOk());
		assertEquals(2, ticket(ticketToken).get("callCount").asInt());

		mvc.perform(asAdmin(post("/api/v1/attendance/tickets/" + called + "/start"))).andExpect(status().isOk());
		assertEquals("SERVING", ticket(ticketToken).get("status").asText());
		mvc.perform(asAdmin(post("/api/v1/attendance/tickets/" + called + "/finish"))).andExpect(status().isOk());
		assertEquals("DONE", ticket(ticketToken).get("status").asText());
	}

	@Test
	void aPersonCanLeaveTheQueueWhileWaitingButNotAfterBeingCalled() throws Exception {
		String token = createQueue("Geral", "A", true).get("publicToken").asText();
		long window = counter("Guichê 1");
		String first = json.readTree(take(token, null, "10.0.0.1").andReturn().getResponse().getContentAsString()).get("ticketToken").asText();
		String second = json.readTree(take(token, null, "10.0.0.2").andReturn().getResponse().getContentAsString()).get("ticketToken").asText();

		JsonNode left = json.readTree(mvc.perform(post("/api/v1/public/tickets/" + second + "/cancel")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertEquals("CANCELED", left.get("status").asText());
		mvc.perform(get("/api/v1/public/queues/" + token)).andExpect(jsonPath("$.waiting").value(1));

		callNext(window);
		mvc.perform(post("/api/v1/public/tickets/" + first + "/cancel")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A senha A001 está sendo chamada: esta ação não se aplica"));
	}

	// ---------- abuso ----------

	@Test
	void theSameDeviceCannotFloodTheQueueWithTickets() throws Exception {
		String token = createQueue("Geral", "A", true).get("publicToken").asText();
		String other = createQueue("Outra", "B", true).get("publicToken").asText();

		for (int i = 0; i < 4; i++) {
			take(token, null, "200.1.1.1", "aparelho-0001").andExpect(status().isCreated());
		}
		take(token, null, "200.1.1.1", "aparelho-0001").andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error").value("Você já pegou senhas demais por hora. Procure o atendimento."));
		// outro aparelho (mesmo Wi-Fi) e outra fila não são afetados
		take(token, null, "200.1.1.1", "aparelho-0002").andExpect(status().isCreated());
		take(other, null, "200.1.1.1", "aparelho-0001").andExpect(status().isCreated());
	}

	@Test
	void aSharedWifiGetsRoomForManyCustomersButNotForAFlood() throws Exception {
		String token = createQueue("Geral", "A", true).get("publicToken").asText();

		for (int i = 0; i < 60; i++) {
			take(token, null, "200.2.2.2", "cliente-" + String.format("%04d", i)).andExpect(status().isCreated());
		}
		take(token, null, "200.2.2.2", "cliente-9999").andExpect(status().isTooManyRequests());
		// e uma rede diferente continua livre
		take(token, null, "200.2.2.3", "cliente-9998").andExpect(status().isCreated());
	}

	@Test
	void invalidOrUnknownSecretsAreNotFoundAndNeverTouchTheDatabaseWithJunk() throws Exception {
		mvc.perform(get("/api/v1/public/queues/abc")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/public/queues/" + "0".repeat(32))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Este QR Code não é válido. Procure o atendimento."));
		mvc.perform(get("/api/v1/public/queues/%27%20OR%201=1--")).andExpect(status().is4xxClientError());
		mvc.perform(get("/api/v1/public/tickets/" + "f".repeat(32))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Esta senha não foi encontrada. Ela pode ter expirado."));
		mvc.perform(post("/api/v1/public/tickets/" + "f".repeat(32) + "/cancel")).andExpect(status().isNotFound());
	}

	@Test
	void aLongNameIsRefusedAndAnInactiveQueueGivesNoTicket() throws Exception {
		JsonNode queue = createQueue("Geral", "A", true);
		String token = queue.get("publicToken").asText();

		take(token, "x".repeat(61), "9.9.9.9").andExpect(status().isBadRequest());

		Map<String, Object> inactive = new HashMap<>();
		inactive.put("name", "Geral");
		inactive.put("prefix", "A");
		inactive.put("active", false);
		inactive.put("remoteEnabled", true);
		mvc.perform(asAdmin(put("/api/v1/queues/" + queue.get("id").asLong()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(inactive)))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/public/queues/" + token)).andExpect(jsonPath("$.open").value(false));
		take(token, null, "9.9.9.10").andExpect(status().isNotFound());
	}

	@Test
	void eachClientOnlyReachesItsOwnQueueThroughTheSecret() throws Exception {
		String mine = createQueue("Geral", "A", true).get("publicToken").asText();
		long other = createTenantRow("Outro cliente QR", "interno", "ACTIVE");
		createUserRow(other, "admin-qr2-" + other + "@teste.com", "ADMIN", "senha-forte-1");
		String otherAdmin = loginAs("admin-qr2-" + other + "@teste.com", "senha-forte-1");
		JsonNode theirs = json.readTree(mvc.perform(withToken(otherAdmin, post("/api/v1/queues").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Geral", "prefix", "A", "remoteEnabled", true))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		take(mine, null, "1.2.3.4").andExpect(status().isCreated());
		take(theirs.get("publicToken").asText(), null, "1.2.3.4").andExpect(status().isCreated());

		// cada cliente só vê a própria senha, mesmo as duas sendo "A001"
		assertEquals(1, json.readTree(mvc.perform(asAdmin(get("/api/v1/attendance/overview"))).andReturn().getResponse().getContentAsString()).get("waiting").size());
		assertEquals(1, json.readTree(mvc.perform(withToken(otherAdmin, get("/api/v1/attendance/overview"))).andReturn().getResponse().getContentAsString()).get("waiting").size());
	}

}
