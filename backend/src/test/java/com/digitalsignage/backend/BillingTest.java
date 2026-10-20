package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

/** O ciclo de cobrança: teste, fatura, atraso, suspensão, pagamento e renovação. */
class BillingTest extends AbstractApiTest {

	private static final String START = "2026-11-05T12:00:00Z";

	private long planId(String code) {
		return jdbc.queryForObject("SELECT id FROM plans WHERE code = ?", Long.class, code);
	}

	private record Client(long id, String token) {
	}

	private Client newClient(String name, String planCode, String email) throws Exception {
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", name, "planId", planId(planCode), "adminName", "Dono", "adminEmail", email, "adminPassword", "senha-forte-1")))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		return new Client(id, loginAs(email, "senha-forte-1"));
	}

	private void cycleAt(String iso) throws Exception {
		clock.freeze(iso);
		mvc.perform(asAdmin(post("/api/v1/platform/billing/run"))).andExpect(status().isOk());
	}

	private JsonNode invoices(Client client) throws Exception {
		return json.readTree(mvc.perform(asAdmin(get("/api/v1/platform/invoices?tenantId=" + client.id()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private String statusOf(Client client) {
		return jdbc.queryForObject("SELECT status FROM tenants WHERE id = ?", String.class, client.id());
	}

	private long onlyInvoice(Client client) throws Exception {
		JsonNode all = invoices(client);
		assertEquals(1, all.size());
		return all.get(0).get("id").asLong();
	}

	// ---------- o ciclo inteiro ----------

	@Test
	void trialInvoiceOverdueSuspensionPaymentAndRenewal() throws Exception {
		clock.freeze(START);
		Client c = newClient("Pagante", "basico", "pagante@c.com");
		assertEquals("TRIAL", statusOf(c));

		// durante o teste não há cobrança, por mais que o ciclo rode
		cycleAt("2026-11-12T12:00:00Z");
		assertEquals(0, invoices(c).size());

		// o teste acaba (14 dias): a primeira fatura é emitida — uma só, mesmo rodando o ciclo de novo
		cycleAt("2026-11-19T12:00:00Z");
		cycleAt("2026-11-19T15:00:00Z");
		JsonNode first = invoices(c).get(0);
		assertEquals(1, invoices(c).size());
		assertEquals(4900, first.get("amountCents").asInt());
		assertEquals("OPEN", first.get("status").asText());
		assertEquals("2026-11-24", first.get("dueDate").asText()); // 5 dias para pagar
		assertEquals("2026-11-19T12:00:00Z", first.get("periodStart").asText());
		assertEquals("2026-12-19T12:00:00Z", first.get("periodEnd").asText());
		org.junit.jupiter.api.Assertions.assertTrue(first.get("number").asText().matches("2026-\\d{6}"), first.get("number").asText());
		assertEquals(false, first.get("overdue").asBoolean());
		assertEquals("TRIAL", statusOf(c));

		// venceu e não pagou: em atraso, mas ainda usa o painel
		cycleAt("2026-11-25T12:00:00Z");
		assertEquals("PAST_DUE", statusOf(c));
		mvc.perform(withToken(c.token(), get("/api/v1/devices"))).andExpect(status().isOk());
		assertEquals(true, invoices(c).get(0).get("overdue").asBoolean());

		// passada a carência (7 dias), suspende: o painel fecha
		cycleAt("2026-11-30T12:00:00Z");
		assertEquals("PAST_DUE", statusOf(c)); // 6 dias de atraso: ainda carência
		cycleAt("2026-12-02T12:00:00Z");
		assertEquals("SUSPENDED", statusOf(c));
		mvc.perform(withToken(c.token(), get("/api/v1/devices"))).andExpect(status().isPaymentRequired());

		// pagamento: a fatura é baixada, o período vai até 19/12 e o cliente volta
		long invoice = onlyInvoice(c);
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + invoice + "/pay").contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"pix\",\"note\":\"comprovante 123\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.paymentMethod").value("PIX"))
				.andExpect(jsonPath("$.paymentNote").value("comprovante 123"));
		assertEquals("ACTIVE", statusOf(c));
		mvc.perform(withToken(c.token(), get("/api/v1/devices"))).andExpect(status().isOk());
		mvc.perform(asAdmin(get("/api/v1/platform/tenants/" + c.id()))).andExpect(jsonPath("$.currentPeriodEnd").value("2026-12-19T12:00:00Z"));
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + invoice + "/pay"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Esta fatura já foi paga"));

		// 5 dias antes do fim do período emite a fatura seguinte (uma só) e o cliente continua em dia
		cycleAt("2026-12-10T12:00:00Z");
		assertEquals(1, invoices(c).size());
		cycleAt("2026-12-14T12:00:00Z");
		cycleAt("2026-12-15T12:00:00Z");
		JsonNode all = invoices(c);
		assertEquals(2, all.size());
		JsonNode second = all.get(0);
		assertEquals("2026-12-19T12:00:00Z", second.get("periodStart").asText());
		assertEquals("2027-01-19T12:00:00Z", second.get("periodEnd").asText());
		assertEquals("2026-12-19", second.get("dueDate").asText()); // vence no fim do período atual
		assertEquals("ACTIVE", statusOf(c));

		// não pagou: atrasa no dia seguinte ao vencimento; paga e renova
		cycleAt("2026-12-20T12:00:00Z");
		assertEquals("PAST_DUE", statusOf(c));
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + second.get("id").asLong() + "/pay"))).andExpect(status().isOk());
		assertEquals("ACTIVE", statusOf(c));
		mvc.perform(asAdmin(get("/api/v1/platform/tenants/" + c.id()))).andExpect(jsonPath("$.currentPeriodEnd").value("2027-01-19T12:00:00Z"));
	}

	@Test
	void aFreePlanIsNeverBilled() throws Exception {
		clock.freeze(START);
		Client c = newClient("Gratuito", "gratis", "g@c.com");

		cycleAt("2027-06-01T12:00:00Z");

		assertEquals(0, invoices(c).size());
		assertEquals("ACTIVE", statusOf(c));
	}

	@Test
	void movingToAFreePlanSettlesAClientThatWasLate() throws Exception {
		clock.freeze(START);
		Client c = newClient("Pagante", "basico", "p@c.com");
		cycleAt("2026-11-19T12:00:00Z");
		cycleAt("2026-12-05T12:00:00Z");
		assertEquals("SUSPENDED", statusOf(c));

		mvc.perform(asAdmin(put("/api/v1/platform/tenants/" + c.id()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("planId", planId("gratis"))))))
				.andExpect(status().isOk());
		cycleAt("2026-12-05T13:00:00Z");

		assertEquals("ACTIVE", statusOf(c));
	}

	@Test
	void withoutATrialTheFirstCycleInvoicesAtOnce() throws Exception {
		clock.freeze(START);
		mvc.perform(asAdmin(put("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content("{\"trialDays\":0}"))).andExpect(status().isOk());
		Client c = newClient("Sem teste", "basico", "s@c.com");
		assertEquals("ACTIVE", statusOf(c));

		cycleAt(START);

		JsonNode invoice = invoices(c).get(0);
		assertEquals("2026-11-05T12:00:00Z", invoice.get("periodStart").asText());
		assertEquals("2026-11-05", invoice.get("dueDate").asText());
	}

	// ---------- faturas avulsas e canceladas ----------

	@Test
	void manualInvoicesCanBeCreatedPaidOrVoided() throws Exception {
		clock.freeze(START);
		Client c = newClient("Cliente", "gratis", "c@c.com");

		long id = idOf(mvc.perform(asAdmin(post("/api/v1/platform/tenants/" + c.id() + "/invoices").contentType(MediaType.APPLICATION_JSON)
				.content("{\"description\":\"Instalação das TVs\",\"amountCents\":25000,\"dueDate\":\"2026-11-20\"}")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.amountCents").value(25000))
				.andExpect(jsonPath("$.tenantName").value("Cliente")).andReturn().getResponse().getContentAsString());
		long other = idOf(mvc.perform(asAdmin(post("/api/v1/platform/tenants/" + c.id() + "/invoices").contentType(MediaType.APPLICATION_JSON)
				.content("{\"description\":\"Cobrança errada\",\"amountCents\":1,\"dueDate\":\"2026-11-20\"}"))).andReturn().getResponse().getContentAsString());

		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + other + "/void"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID"));
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + other + "/pay"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Esta fatura foi cancelada e não pode ser paga"));
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + id + "/pay"))).andExpect(status().isOk());
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + id + "/void"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Uma fatura paga não pode ser cancelada"));

		mvc.perform(asAdmin(get("/api/v1/platform/invoices?status=void"))).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(asAdmin(get("/api/v1/platform/invoices?status=paid"))).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/999999/pay"))).andExpect(status().isNotFound());
		mvc.perform(asAdmin(post("/api/v1/platform/tenants/999999/invoices").contentType(MediaType.APPLICATION_JSON)
				.content("{\"description\":\"x\",\"amountCents\":1,\"dueDate\":\"2026-11-20\"}"))).andExpect(status().isNotFound());
	}

	@Test
	void voidingTheOverdueInvoiceLiftsTheSuspension() throws Exception {
		clock.freeze(START);
		Client c = newClient("Pagante", "basico", "p@c.com");
		cycleAt("2026-11-19T12:00:00Z");
		cycleAt("2026-12-05T12:00:00Z");
		assertEquals("SUSPENDED", statusOf(c));

		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + onlyInvoice(c) + "/void"))).andExpect(status().isOk());

		assertEquals("ACTIVE", statusOf(c));
	}

	// ---------- o que o cliente vê ----------

	@Test
	void aClientSeesItsOwnInvoicesPlanAndHowToPay() throws Exception {
		clock.freeze(START);
		Client a = newClient("Cliente A", "basico", "a@c.com");
		Client b = newClient("Cliente B", "basico", "b@c.com");
		mvc.perform(asAdmin(put("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"companyName\":\"Sinalização Ltda\",\"paymentInstructions\":\"PIX 12.345.678/0001-90\",\"supportEmail\":\"suporte@sinal.com\"}"))).andExpect(status().isOk());
		cycleAt("2026-11-19T12:00:00Z");

		mvc.perform(withToken(a.token(), get("/api/v1/billing")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenant.name").value("Cliente A"))
				.andExpect(jsonPath("$.tenant.plan.code").value("basico"))
				.andExpect(jsonPath("$.invoices", hasSize(1)))
				.andExpect(jsonPath("$.invoices[0].tenantName").value("Cliente A"))
				.andExpect(jsonPath("$.companyName").value("Sinalização Ltda"))
				.andExpect(jsonPath("$.paymentInstructions").value("PIX 12.345.678/0001-90"))
				.andExpect(jsonPath("$.supportEmail").value("suporte@sinal.com"))
				// os planos para comparar: os que a operação vende (sem o plano interno)
				.andExpect(jsonPath("$.plans[*].code", hasItems("gratis", "basico", "profissional")))
				.andExpect(jsonPath("$.plans[?(@.code=='interno')]").isEmpty());
		// o B tem as faturas dele, nunca as do A
		mvc.perform(withToken(b.token(), get("/api/v1/billing"))).andExpect(jsonPath("$.invoices", hasSize(1))).andExpect(jsonPath("$.invoices[0].tenantName").value("Cliente B"));
		long aInvoice = onlyInvoice(a);
		long bInvoice = onlyInvoice(b);
		assertEquals(false, aInvoice == bInvoice);
	}

	@Test
	void onlyTheClientAdministratorOpensBillingAndASuspendedOneCanStillReadIt() throws Exception {
		clock.freeze(START);
		Client a = newClient("Cliente A", "basico", "a@c.com");
		createUserRow(a.id(), "editor@a.com", "EDITOR", "senha-forte-1");
		createUserRow(a.id(), "viewer@a.com", "VIEWER", "senha-forte-1");
		mvc.perform(withToken(loginAs("editor@a.com", "senha-forte-1"), get("/api/v1/billing"))).andExpect(status().isForbidden());
		mvc.perform(withToken(loginAs("viewer@a.com", "senha-forte-1"), get("/api/v1/billing"))).andExpect(status().isForbidden());

		cycleAt("2026-11-19T12:00:00Z");
		cycleAt("2026-12-05T12:00:00Z");
		assertEquals("SUSPENDED", statusOf(a));
		// suspenso: vê a cobrança (para regularizar), mas não altera nada
		mvc.perform(withToken(a.token(), get("/api/v1/billing"))).andExpect(status().isOk()).andExpect(jsonPath("$.invoices[0].overdue").value(true));
		mvc.perform(withToken(a.token(), put("/api/v1/billing/profile").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Outro nome\"}"))).andExpect(status().isPaymentRequired());
	}

	@Test
	void theClientEditsItsNameAndBillingContact() throws Exception {
		clock.freeze(START);
		Client a = newClient("Cliente A", "gratis", "a@c.com");
		newClient("Cliente B", "gratis", "b@c.com");

		mvc.perform(withToken(a.token(), put("/api/v1/billing/profile").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Cliente A Ltda\",\"billingEmail\":\"financeiro@a.com\",\"document\":\"12.345.678/0001-90\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Cliente A Ltda")).andExpect(jsonPath("$.billingEmail").value("financeiro@a.com"));
		// o painel mostra o CPF/CNPJ cadastrado (o resumo do cliente não o traz)
		mvc.perform(withToken(a.token(), get("/api/v1/billing"))).andExpect(status().isOk()).andExpect(jsonPath("$.document").value("12.345.678/0001-90"));
		mvc.perform(withToken(a.token(), put("/api/v1/billing/profile").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"cliente b\"}"))).andExpect(status().isConflict());
		mvc.perform(withToken(a.token(), put("/api/v1/billing/profile").contentType(MediaType.APPLICATION_JSON).content("{\"billingEmail\":\"nao-e-email\"}"))).andExpect(status().isBadRequest());
	}

	// ---------- configurações da plataforma ----------

	@Test
	void theOperatorTunesTheBillingRules() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/platform/settings"))).andExpect(jsonPath("$.trialDays").value(14)).andExpect(jsonPath("$.graceDays").value(7)).andExpect(jsonPath("$.invoiceLeadDays").value(5));

		mvc.perform(asAdmin(put("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content("{\"trialDays\":30,\"graceDays\":3,\"invoiceLeadDays\":10}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.trialDays").value(30)).andExpect(jsonPath("$.graceDays").value(3)).andExpect(jsonPath("$.invoiceLeadDays").value(10));
		mvc.perform(asAdmin(put("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content("{\"trialDays\":365}"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(put("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content("{\"graceDays\":-1}"))).andExpect(status().isBadRequest());
	}

	@Test
	void theGraceDaysSettingDecidesWhenTheSuspensionHappens() throws Exception {
		clock.freeze(START);
		mvc.perform(asAdmin(put("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content("{\"graceDays\":1}"))).andExpect(status().isOk());
		Client c = newClient("Pagante", "basico", "p@c.com");
		cycleAt("2026-11-19T12:00:00Z"); // vence em 24/11

		cycleAt("2026-11-25T12:00:00Z"); // 1 dia de atraso: dentro da carência de 1 dia
		assertEquals("PAST_DUE", statusOf(c));
		cycleAt("2026-11-26T12:00:00Z"); // 2 dias: passou
		assertEquals("SUSPENDED", statusOf(c));
	}

}
