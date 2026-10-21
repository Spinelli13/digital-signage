package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.digitalsignage.backend.queue.AttendanceService;
import com.digitalsignage.backend.queue.Ticket;
import com.fasterxml.jackson.databind.JsonNode;

/** Fila de atendimento: filas e guichês, senhas, chamada com prioridade e o painel de chamadas da TV. */
class AttendanceTest extends AbstractApiTest {

	@Autowired
	private AttendanceService service;

	// ---------- montagem ----------

	private ResultActions send(String path, Object body) throws Exception {
		return mvc.perform(asAdmin(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private long queue(String name, String prefix, boolean priority) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("prefix", prefix);
		body.put("priority", priority);
		return idOf(send("/api/v1/queues", body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long counter(String name, List<Long> queueIds) throws Exception {
		return idOf(send("/api/v1/counters", Map.of("name", name, "queueIds", queueIds)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode issue(long queueId, String customer) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("queueId", queueId);
		body.put("customerName", customer);
		return json.readTree(send("/api/v1/attendance/tickets", body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode callNext(long counterId) throws Exception {
		return json.readTree(send("/api/v1/attendance/counters/" + counterId + "/call-next", Map.of()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private ResultActions act(long ticketId, String action) throws Exception {
		return send("/api/v1/attendance/tickets/" + ticketId + "/" + action, Map.of());
	}

	private JsonNode overview() throws Exception {
		return json.readTree(mvc.perform(asAdmin(get("/api/v1/attendance/overview"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	/** Chama e conclui até acabar: a ordem em que as senhas foram atendidas. */
	private List<String> serveAll(long counterId, int count) throws Exception {
		List<String> order = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			JsonNode ticket = callNext(counterId);
			order.add(ticket.get("label").asText());
			act(ticket.get("id").asLong(), "finish").andExpect(status().isOk());
		}
		return order;
	}

	// ---------- cadastro ----------

	@Test
	void anAdministratorSetsUpQueuesAndCounters() throws Exception {
		long general = queue("Atendimento geral", "a", false);
		long counterId = counter("Guichê 1", List.of(general));

		mvc.perform(asAdmin(get("/api/v1/queues"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Atendimento geral"))
				.andExpect(jsonPath("$[0].prefix").value("A"))
				.andExpect(jsonPath("$[0].priority").value(false))
				.andExpect(jsonPath("$[0].waiting").value(0));
		mvc.perform(asAdmin(get("/api/v1/counters"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(counterId))
				.andExpect(jsonPath("$[0].queueIds[0]").value(general))
				.andExpect(jsonPath("$[0].current").doesNotExist());
	}

	@Test
	void theQueueAndCounterRulesAreEnforced() throws Exception {
		long general = queue("Geral", "A", false);

		send("/api/v1/queues", Map.of("name", "geral", "prefix", "B")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Já existe uma fila chamada \"geral\""));
		send("/api/v1/queues", Map.of("name", "Outra", "prefix", "a")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A letra \"A\" já é de outra fila: cada fila precisa de uma letra diferente"));
		send("/api/v1/queues", Map.of("name", "Letras demais", "prefix", "ABCD")).andExpect(status().isBadRequest());
		send("/api/v1/queues", Map.of("name", "Símbolo", "prefix", "#")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("A letra da senha tem de 1 a 3 letras ou números (ex.: A, P, VIP)"));
		send("/api/v1/queues", Map.of("name", "Fuso", "prefix", "F", "timezone", "Marte/Olimpo")).andExpect(status().isBadRequest());

		counter("Guichê 1", List.of());
		send("/api/v1/counters", Map.of("name", "guichê 1")).andExpect(status().isConflict());
		send("/api/v1/counters", Map.of("name", "Guichê 2", "queueIds", List.of(9999))).andExpect(status().isNotFound());
		assertTrue(general > 0);
	}

	@Test
	void aQueueOrCounterInUseCannotBeDeletedAndAFreeOneCan() throws Exception {
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());
		long ticket = issue(general, null).get("id").asLong();

		mvc.perform(asAdmin(delete("/api/v1/queues/" + general))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A fila \"Geral\" ainda tem senhas esperando ou em atendimento: conclua ou cancele antes de excluir"));
		callNext(window);
		mvc.perform(asAdmin(delete("/api/v1/counters/" + window))).andExpect(status().isConflict());
		act(ticket, "finish").andExpect(status().isOk());

		mvc.perform(asAdmin(delete("/api/v1/counters/" + window))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(delete("/api/v1/queues/" + general))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/queues"))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void theLetterOfAQueueWithOpenTicketsCannotChange() throws Exception {
		long general = queue("Geral", "A", false);
		issue(general, null);

		mvc.perform(asAdmin(put("/api/v1/queues/" + general).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Geral", "prefix", "G"))))).andExpect(status().isConflict());
		// o nome pode mudar à vontade
		mvc.perform(asAdmin(put("/api/v1/queues/" + general).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Atendimento", "prefix", "A"))))).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Atendimento"));
	}

	// ---------- emitir ----------

	@Test
	void ticketsAreNumberedPerQueueAndTheNameIsOptional() throws Exception {
		long general = queue("Geral", "A", false);
		long vip = queue("Prioritário", "P", true);

		JsonNode first = issue(general, "  Maria Silva  ");
		JsonNode second = issue(general, null);
		JsonNode priority = issue(vip, "");

		assertEquals("A001", first.get("label").asText());
		assertEquals("Maria Silva", first.get("customerName").asText());
		assertEquals("A002", second.get("label").asText());
		assertTrue(second.get("customerName").isNull());
		assertEquals("P001", priority.get("label").asText());
		assertEquals("WAITING", first.get("status").asText());
		assertEquals("PANEL", first.get("source").asText());
		assertTrue(priority.get("priority").asBoolean());
	}

	@Test
	void anInactiveQueueIssuesNothingAndALongNameIsRefused() throws Exception {
		long general = queue("Geral", "A", false);
		mvc.perform(asAdmin(put("/api/v1/queues/" + general).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Geral", "prefix", "A", "active", false))))).andExpect(status().isOk());

		send("/api/v1/attendance/tickets", Map.of("queueId", general)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A fila \"Geral\" está desativada"));
		send("/api/v1/attendance/tickets", Map.of("queueId", 9999)).andExpect(status().isNotFound());
		send("/api/v1/attendance/tickets", Map.of("queueId", general, "customerName", "x".repeat(61))).andExpect(status().isBadRequest());
	}

	@Test
	void theNumberingStartsOverEachDayAndYesterdaysWaitingTicketsLeaveTheQueue() throws Exception {
		clock.freeze("2026-10-21T15:00:00Z");
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());
		long yesterday = issue(general, null).get("id").asLong();
		issue(general, null);

		clock.freeze("2026-10-22T15:00:00Z");
		JsonNode today = issue(general, null);

		assertEquals("A001", today.get("label").asText());
		JsonNode overview = overview();
		assertEquals(1, overview.get("waiting").size());
		assertEquals("A001", overview.get("waiting").get(0).get("label").asText());
		// quem sobrou de ontem foi cancelado: o guichê chama a de hoje
		assertEquals("A001", callNext(window).get("label").asText());
		act(yesterday, "recall").andExpect(status().isConflict());
	}

	// ---------- chamar ----------

	@Test
	void theCounterCallsTheOldestWaitingTicketFirst() throws Exception {
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());
		issue(general, "Ana");
		issue(general, "Bia");
		issue(general, "Caio");

		JsonNode first = callNext(window);

		assertEquals("A001", first.get("label").asText());
		assertEquals("CALLING", first.get("status").asText());
		assertEquals("Guichê 1", first.get("counterName").asText());
		assertEquals(1, first.get("callCount").asInt());
		assertEquals("Ana", first.get("customerName").asText());
		JsonNode overview = overview();
		assertEquals("A001", overview.get("counters").get(0).get("current").get("label").asText());
		assertEquals(2, overview.get("waiting").size());
	}

	@Test
	void aCounterThatIsBusyMustFinishBeforeCallingAgainAndAnEmptyQueueIsSaid() throws Exception {
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());

		send("/api/v1/attendance/counters/" + window + "/call-next", Map.of()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Não há senhas aguardando para este guichê"));

		issue(general, null);
		issue(general, null);
		long current = callNext(window).get("id").asLong();
		send("/api/v1/attendance/counters/" + window + "/call-next", Map.of()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("O guichê \"Guichê 1\" ainda está com a senha A001: conclua o atendimento antes de chamar a próxima"));

		act(current, "finish").andExpect(status().isOk());
		assertEquals("A002", callNext(window).get("label").asText());
	}

	@Test
	void aCounterOnlyCallsTheQueuesItServes() throws Exception {
		long general = queue("Geral", "A", false);
		long accounts = queue("Contas", "C", false);
		long window = counter("Guichê de contas", List.of(accounts));
		issue(general, null);

		send("/api/v1/attendance/counters/" + window + "/call-next", Map.of()).andExpect(status().isConflict());
		issue(accounts, null);

		assertEquals("C001", callNext(window).get("label").asText());
	}

	@Test
	void aDisabledCounterOrOneWithoutActiveQueuesCannotCall() throws Exception {
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of(general));
		issue(general, null);
		mvc.perform(asAdmin(put("/api/v1/counters/" + window).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Guichê 1", "queueIds", List.of(general), "active", false))))).andExpect(status().isOk());

		send("/api/v1/attendance/counters/" + window + "/call-next", Map.of()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("O guichê \"Guichê 1\" está desativado"));
	}

	@Test
	void priorityQueuesGoFirstButAfterThreeInARowANormalTicketIsCalled() throws Exception {
		long general = queue("Geral", "A", false);
		long vip = queue("Prioritário", "P", true);
		long window = counter("Guichê 1", List.of());
		issue(general, null);
		issue(general, null);
		for (int i = 0; i < 5; i++) {
			issue(vip, null);
		}

		List<String> order = serveAll(window, 7);

		assertEquals(List.of("P001", "P002", "P003", "A001", "P004", "P005", "A002"), order);
	}

	@Test
	void whenOnlyPriorityTicketsWaitTheyAreCalledInOrderAndWhenOnlyNormalOnesWaitTheyAre() throws Exception {
		long general = queue("Geral", "A", false);
		long vip = queue("Prioritário", "P", true);
		long window = counter("Guichê 1", List.of());
		for (int i = 0; i < 5; i++) {
			issue(vip, null);
		}
		assertEquals(List.of("P001", "P002", "P003", "P004", "P005"), serveAll(window, 5));
		issue(general, null);
		issue(general, null);
		assertEquals(List.of("A001", "A002"), serveAll(window, 2));
	}

	@Test
	void twoCountersCallingAtTheSameTimeNeverGetTheSameTicket() throws Exception {
		long general = queue("Geral", "A", false);
		List<Long> windows = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			windows.add(counter("Guichê " + i, List.of()));
			issue(general, null);
		}

		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			List<Future<Long>> calls = new ArrayList<>();
			for (long window : windows) {
				Callable<Long> task = () -> inTenant(2L, () -> service.callNext(window)).getId();
				calls.add(pool.submit(task));
			}
			Set<Long> distinct = new java.util.HashSet<>();
			for (Future<Long> call : calls) {
				distinct.add(call.get(30, TimeUnit.SECONDS));
			}
			assertEquals(6, distinct.size());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void twoCountersIssuingAtTheSameTimeNeverGetTheSameNumber() throws Exception {
		long general = queue("Geral", "A", false);

		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			List<Future<String>> issued = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				Callable<String> task = () -> inTenant(2L, () -> service.issue(general, null, "PANEL")).getLabel();
				issued.add(pool.submit(task));
			}
			Set<String> labels = new java.util.HashSet<>();
			for (Future<String> label : issued) {
				labels.add(label.get(30, TimeUnit.SECONDS));
			}
			assertEquals(12, labels.size());
			assertTrue(labels.contains("A001") && labels.contains("A012"));
		} finally {
			pool.shutdownNow();
		}
	}

	// ---------- atender ----------

	@Test
	void theTicketGoesThroughRecallStartAndFinish() throws Exception {
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());
		issue(general, null);
		long id = callNext(window).get("id").asLong();

		JsonNode again = json.readTree(act(id, "recall").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertEquals(2, again.get("callCount").asInt());
		assertEquals("CALLING", again.get("status").asText());

		JsonNode serving = json.readTree(act(id, "start").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertEquals("SERVING", serving.get("status").asText());
		act(id, "recall").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A senha A001 está em atendimento: esta ação não se aplica"));

		JsonNode done = json.readTree(act(id, "finish").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertEquals("DONE", done.get("status").asText());
		assertTrue(!done.get("finishedAt").isNull() && !done.get("startedAt").isNull());
		act(id, "finish").andExpect(status().isConflict());
		assertTrue(overview().get("counters").get(0).get("current").isNull());
	}

	@Test
	void aTicketCalledWithoutShowingUpIsMarkedAndFreesTheCounter() throws Exception {
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());
		issue(general, null);
		issue(general, null);
		long id = callNext(window).get("id").asLong();

		JsonNode missed = json.readTree(act(id, "no-show").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		assertEquals("NO_SHOW", missed.get("status").asText());
		assertEquals("A002", callNext(window).get("label").asText());
	}

	@Test
	void onlyAWaitingTicketCanBeCanceled() throws Exception {
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());
		long waiting = issue(general, null).get("id").asLong();
		long called = issue(general, null).get("id").asLong();
		// chama a primeira, para a segunda continuar esperando
		long first = callNext(window).get("id").asLong();
		assertEquals(waiting, first);

		act(called, "cancel").andExpect(status().isOk());
		act(first, "cancel").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A senha A001 está sendo chamada: esta ação não se aplica"));
		act(9999, "finish").andExpect(status().isNotFound());
		assertEquals(0, overview().get("waiting").size());
	}

	// ---------- números do dia ----------

	@Test
	void theOverviewCountsTheDayAndAveragesTheWaitAndTheService() throws Exception {
		clock.freeze("2026-10-21T15:00:00Z");
		long general = queue("Geral", "A", false);
		long window = counter("Guichê 1", List.of());
		long first = issue(general, null).get("id").asLong();
		long second = issue(general, null).get("id").asLong();
		issue(general, null);

		clock.freeze("2026-10-21T15:02:00Z"); // 2 minutos de espera
		callNext(window);
		act(first, "start").andExpect(status().isOk());
		clock.freeze("2026-10-21T15:07:00Z"); // 5 minutos de atendimento
		act(first, "finish").andExpect(status().isOk());
		callNext(window);
		act(second, "no-show").andExpect(status().isOk());

		JsonNode today = overview().get("today");
		assertEquals(3, today.get("issued").asInt());
		assertEquals(1, today.get("waiting").asInt());
		assertEquals(1, today.get("served").asInt());
		assertEquals(1, today.get("noShow").asInt());
		assertEquals(0, today.get("canceled").asInt());
		assertEquals(0, today.get("serving").asInt());
		assertEquals(270, today.get("averageWaitSeconds").asInt()); // (2 min + 7 min) / 2
		assertEquals(300, today.get("averageServiceSeconds").asInt());
	}

	// ---------- permissões ----------

	@Test
	void theViewerOnlyWatchesTheEditorOperatesAndOnlyTheAdministratorSetsUp() throws Exception {
		long tenant = createTenantRow("Cliente Fila", "interno", "ACTIVE");
		for (String role : List.of("viewer", "editor", "admin")) {
			createUserRow(tenant, role + "-fila-" + tenant + "@teste.com", role.toUpperCase(), "senha-forte-1");
		}
		String viewer = loginAs("viewer-fila-" + tenant + "@teste.com", "senha-forte-1");
		String editor = loginAs("editor-fila-" + tenant + "@teste.com", "senha-forte-1");
		String admin = loginAs("admin-fila-" + tenant + "@teste.com", "senha-forte-1");

		long queueId = idOf(mvc.perform(withToken(admin, post("/api/v1/queues").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Geral", "prefix", "A"))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		long counterId = idOf(mvc.perform(withToken(admin, post("/api/v1/counters").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Guichê 1"))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		// o Editor emite e chama, mas não monta filas nem guichês
		mvc.perform(withToken(editor, post("/api/v1/attendance/tickets").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("queueId", queueId))))).andExpect(status().isCreated());
		mvc.perform(withToken(editor, post("/api/v1/attendance/counters/" + counterId + "/call-next"))).andExpect(status().isOk());
		mvc.perform(withToken(editor, post("/api/v1/queues").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Outra", "prefix", "B"))))).andExpect(status().isForbidden());
		mvc.perform(withToken(editor, delete("/api/v1/counters/" + counterId))).andExpect(status().isForbidden());

		// o Visualizador só olha
		mvc.perform(withToken(viewer, get("/api/v1/attendance/overview"))).andExpect(status().isOk());
		mvc.perform(withToken(viewer, get("/api/v1/queues"))).andExpect(status().isOk());
		mvc.perform(withToken(viewer, post("/api/v1/attendance/tickets").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("queueId", queueId))))).andExpect(status().isForbidden());
		mvc.perform(withToken(viewer, post("/api/v1/attendance/counters/" + counterId + "/call-next"))).andExpect(status().isForbidden());
	}

	@Test
	void eachClientOnlySeesItsOwnQueuesAndTickets() throws Exception {
		long general = queue("Geral", "A", false);
		issue(general, "Maria");
		long other = createTenantRow("Outro cliente", "interno", "ACTIVE");
		createUserRow(other, "admin-outro-" + other + "@teste.com", "ADMIN", "senha-forte-1");
		String token = loginAs("admin-outro-" + other + "@teste.com", "senha-forte-1");

		mvc.perform(withToken(token, get("/api/v1/queues"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(withToken(token, get("/api/v1/attendance/overview"))).andExpect(status().isOk()).andExpect(jsonPath("$.waiting.length()").value(0));
		mvc.perform(withToken(token, post("/api/v1/attendance/tickets").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("queueId", general))))).andExpect(status().isNotFound());
	}

	// ---------- painel de chamadas na TV ----------

	private JsonNode board(Map<String, Object> settings) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("kind", "QUEUE");
		body.put("settings", settings);
		return json.readTree(send("/api/v1/widgets/preview", body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	@Test
	void theCallBoardListsTheLatestCallsWithTheCounterAndHowManyAreWaiting() throws Exception {
		clock.freeze("2026-10-21T15:00:00Z");
		long general = queue("Geral", "A", false);
		long window1 = counter("Guichê 1", List.of());
		long window2 = counter("Guichê 2", List.of());
		for (int i = 0; i < 5; i++) {
			issue(general, null);
		}
		callNext(window1);
		clock.freeze("2026-10-21T15:01:00Z");
		callNext(window2);

		JsonNode board = board(Map.of("calls", 5));

		assertEquals(5, board.get("settings").get("calls").asInt());
		JsonNode calls = board.get("data").get("calls");
		assertEquals(2, calls.size());
		assertEquals("A002", calls.get(0).get("label").asText());
		assertEquals("Guichê 2", calls.get(0).get("counter").asText());
		assertEquals("Geral", calls.get(0).get("queue").asText());
		assertEquals("CALLING", calls.get(0).get("status").asText());
		assertEquals(1, calls.get(0).get("callCount").asInt());
		assertEquals(java.time.Instant.parse("2026-10-21T15:01:00Z").toEpochMilli(), calls.get(0).get("calledAtMs").asLong());
		assertEquals("A001", calls.get(1).get("label").asText());
		assertEquals(3, board.get("data").get("waiting").asInt());
	}

	@Test
	void aRecallMovesTheTicketToTheTopWithAHigherCountSoTheTvCanBeepAgain() throws Exception {
		clock.freeze("2026-10-21T15:00:00Z");
		long general = queue("Geral", "A", false);
		long window1 = counter("Guichê 1", List.of());
		long window2 = counter("Guichê 2", List.of());
		issue(general, null);
		issue(general, null);
		long first = callNext(window1).get("id").asLong();
		clock.freeze("2026-10-21T15:00:30Z");
		callNext(window2);
		assertEquals("A002", board(Map.of()).get("data").get("calls").get(0).get("label").asText());

		clock.freeze("2026-10-21T15:01:00Z");
		act(first, "recall").andExpect(status().isOk());

		JsonNode top = board(Map.of()).get("data").get("calls").get(0);
		assertEquals("A001", top.get("label").asText());
		assertEquals(2, top.get("callCount").asInt());
	}

	@Test
	void theBoardFollowsTheChosenQueuesAndOldCallsDisappearTheNextDay() throws Exception {
		clock.freeze("2026-10-21T15:00:00Z");
		long general = queue("Geral", "A", false);
		long accounts = queue("Contas", "C", false);
		long window = counter("Guichê 1", List.of());
		issue(general, null);
		issue(accounts, null);
		issue(accounts, null);
		act(callNext(window).get("id").asLong(), "finish").andExpect(status().isOk()); // A001
		act(callNext(window).get("id").asLong(), "finish").andExpect(status().isOk()); // C001
		callNext(window); // C002

		JsonNode onlyAccounts = board(Map.of("queueIds", List.of(accounts))).get("data");

		assertEquals(2, onlyAccounts.get("calls").size());
		assertEquals("C002", onlyAccounts.get("calls").get(0).get("label").asText());
		assertEquals("C001", onlyAccounts.get("calls").get(1).get("label").asText());
		assertEquals(3, board(Map.of()).get("data").get("calls").size());

		// 13 horas depois, o painel de ontem não volta
		clock.freeze("2026-10-22T04:00:01Z");
		assertEquals(0, board(Map.of()).get("data").get("calls").size());
	}

	@Test
	void theBoardWidgetValidatesItsChoicesAndIsServedToTheTv() throws Exception {
		queue("Geral", "A", false);
		// escolhas inválidas
		send("/api/v1/widgets/preview", Map.of("kind", "QUEUE", "settings", Map.of("calls", 20))).andExpect(status().isBadRequest());
		send("/api/v1/widgets/preview", Map.of("kind", "QUEUE", "settings", Map.of("queueIds", List.of("x")))).andExpect(status().isBadRequest());

		// salvar e a TV buscar os dados
		JsonNode created = json.readTree(send("/api/v1/media/widget", Map.of("kind", "QUEUE", "settings", Map.of("title", "Chamadas", "sound", false)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		assertEquals("Painel de chamadas", created.get("name").asText());
		assertEquals("Chamadas", created.get("widget").get("settings").get("title").asText());
		assertEquals(false, created.get("widget").get("settings").get("sound").asBoolean());
		assertEquals(5, created.get("widget").get("settings").get("calls").asInt());
		assertEquals(15, created.get("durationSeconds").asInt());
		assertNotEquals(0, created.get("id").asLong());
	}

}
