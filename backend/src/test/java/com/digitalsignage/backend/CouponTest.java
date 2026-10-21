package com.digitalsignage.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

/** Cupons de assinatura: o gestor cria, o administrador do cliente resgata, e o ciclo de cobrança aplica. */
class CouponTest extends AbstractApiTest {

	private static final String START = "2026-11-05T12:00:00Z";

	private record Client(long id, String token) {
	}

	private long planId(String code) {
		return jdbc.queryForObject("SELECT id FROM plans WHERE code = ?", Long.class, code);
	}

	private Client newClient(String name, String planCode, String email) throws Exception {
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", name, "planId", planId(planCode), "adminName", "Dono", "adminEmail", email, "adminPassword", "senha-forte-1")))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		return new Client(id, loginAs(email, "senha-forte-1"));
	}

	private ResultActions create(String code, String kind, int value, Integer months, Long upgradePlan, String until, Integer max) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("kind", kind);
		body.put("value", value);
		body.put("durationMonths", months);
		body.put("upgradePlanId", upgradePlan);
		body.put("validUntil", until);
		body.put("maxRedemptions", max);
		return mvc.perform(asAdmin(post("/api/v1/platform/coupons").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private ResultActions redeem(Client client, String code) throws Exception {
		return mvc.perform(withToken(client.token(), post("/api/v1/billing/coupon").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("code", code)))));
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

	// ---------- o gestor cria ----------

	@Test
	void theManagerCreatesCouponsWithTheirRulesAndTheCodeIsNormalized() throws Exception {
		create("ola20", "PERCENT_OFF", 20, 3, null, null, 50).andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("OLA20")).andExpect(jsonPath("$.kind").value("PERCENT_OFF"))
				.andExpect(jsonPath("$.durationMonths").value(3)).andExpect(jsonPath("$.state").value("ACTIVE")).andExpect(jsonPath("$.redeemedCount").value(0));
		create("OLA20", "FREE_DAYS", 5, null, null, null, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Já existe um cupom com o código \"OLA20\""));
		// descontos valem por uma fatura se nada for dito; os outros tipos não guardam duração
		create("UMA", "AMOUNT_OFF", 1000, null, null, null, null).andExpect(jsonPath("$.durationMonths").value(1));
		create("DIAS", "FREE_DAYS", 10, 4, null, null, null).andExpect(jsonPath("$.durationMonths").doesNotExist());
		mvc.perform(asAdmin(get("/api/v1/platform/coupons"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].code", hasItem("DIAS")));
	}

	@Test
	void validatesTheCouponRules() throws Exception {
		create("AB", "PERCENT_OFF", 10, 1, null, null, null).andExpect(status().isBadRequest()); // código curto
		create("com espaco", "PERCENT_OFF", 10, 1, null, null, null).andExpect(status().isBadRequest());
		create("MUITO", "PERCENT_OFF", 101, 1, null, null, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("A porcentagem de desconto vai de 1 a 100"));
		create("ZERO", "FREE_DAYS", 0, null, null, null, null).andExpect(status().isBadRequest());
		create("SEMPLANO", "PLAN_UPGRADE", 7, null, null, null, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Escolha o plano que o cupom libera"));
		create("PLANO404", "PLAN_UPGRADE", 7, null, 99999L, null, null).andExpect(status().isNotFound());
		mvc.perform(asAdmin(post("/api/v1/platform/coupons").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"VOLTA\",\"kind\":\"FREE_DAYS\",\"value\":5,\"validFrom\":\"2026-12-01T00:00:00Z\",\"validUntil\":\"2026-11-01T00:00:00Z\"}")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("O fim da validade precisa ser depois do início"));
	}

	@Test
	void onlyTheManagerCreatesCoupons() throws Exception {
		Client c = newClient("Cliente", "basico", "dono@c.com");
		mvc.perform(withToken(c.token(), get("/api/v1/platform/coupons"))).andExpect(status().isForbidden());
		mvc.perform(withToken(c.token(), post("/api/v1/platform/coupons").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"HACK\",\"kind\":\"PERCENT_OFF\",\"value\":100}"))).andExpect(status().isForbidden());
	}

	// ---------- desconto ----------

	@Test
	void aPercentDiscountReducesTheNextInvoicesAndThenStops() throws Exception {
		clock.freeze(START);
		Client c = newClient("Pagante", "basico", "pagante@c.com"); // R$ 49,00, em teste por 14 dias
		create("DEZ", "PERCENT_OFF", 10, 2, null, null, null).andExpect(status().isCreated());

		redeem(c, "dez").andExpect(status().isOk()).andExpect(jsonPath("$.code").value("DEZ")).andExpect(jsonPath("$.invoicesLeft").value(2))
				.andExpect(jsonPath("$.active").value(true));

		cycleAt("2026-11-19T12:00:00Z"); // acabou o teste: primeira fatura, com 10% de desconto
		JsonNode first = invoices(c).get(0);
		assertEquals(4410, first.get("amountCents").asInt());
		org.junit.jupiter.api.Assertions.assertTrue(first.get("description").asText().contains("cupom DEZ (-R$ 4,90)"), first.get("description").asText());
		assertEquals("OPEN", first.get("status").asText());

		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + first.get("id").asLong() + "/pay").contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"pix\"}")))
				.andExpect(status().isOk());
		cycleAt("2026-12-15T12:00:00Z"); // segunda fatura: ainda com desconto
		assertEquals(4410, invoices(c).get(0).get("amountCents").asInt());
		mvc.perform(asAdmin(post("/api/v1/platform/invoices/" + invoices(c).get(0).get("id").asLong() + "/pay").contentType(MediaType.APPLICATION_JSON).content("{}")))
				.andExpect(status().isOk());
		cycleAt("2027-01-16T12:00:00Z"); // terceira: o desconto acabou
		assertEquals(4900, invoices(c).get(0).get("amountCents").asInt());
		assertEquals(0, jdbc.queryForObject("SELECT invoices_left FROM coupon_redemptions WHERE tenant_id = ?", Integer.class, c.id()));
		mvc.perform(withToken(c.token(), get("/api/v1/billing"))).andExpect(jsonPath("$.coupons[0].active").value(false));
	}

	@Test
	void anAmountDiscountNeverGoesBelowZeroAndACoveredInvoiceIsSettledByTheCoupon() throws Exception {
		clock.freeze(START);
		Client c = newClient("Cobertura", "basico", "cobertura@c.com");
		create("TUDO", "AMOUNT_OFF", 999999, 1, null, null, null).andExpect(status().isCreated());
		redeem(c, "TUDO").andExpect(status().isOk());

		cycleAt("2026-11-19T12:00:00Z");
		JsonNode invoice = invoices(c).get(0);
		assertEquals(0, invoice.get("amountCents").asInt());
		assertEquals("PAID", invoice.get("status").asText());
		assertEquals("CUPOM", invoice.get("paymentMethod").asText());
		assertEquals("ACTIVE", statusOf(c)); // o teste acabou e o período já está pago
		mvc.perform(asAdmin(get("/api/v1/platform/tenants/" + c.id()))).andExpect(jsonPath("$.currentPeriodEnd").value("2026-12-19T12:00:00Z"));
	}

	@Test
	void onlyOneDiscountAtATimeAndFreePlansHaveNothingToDiscount() throws Exception {
		clock.freeze(START);
		Client c = newClient("Pagante", "basico", "pagante@c.com");
		create("DESC1", "PERCENT_OFF", 10, 1, null, null, null);
		create("DESC2", "AMOUNT_OFF", 500, 1, null, null, null);
		redeem(c, "DESC1").andExpect(status().isOk());
		redeem(c, "DESC2").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Você já tem um desconto ativo. Use outro cupom de desconto quando ele acabar."));

		Client free = newClient("Gratuito", "gratis", "gratis@c.com");
		redeem(free, "DESC1").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("O seu plano é gratuito: não há mensalidade para o cupom abater."));
	}

	// ---------- dias grátis ----------

	@Test
	void freeDaysExtendTheTrialOrThePaidPeriod() throws Exception {
		clock.freeze(START);
		Client c = newClient("Teste", "basico", "teste@c.com"); // teste até 19/11
		create("MAIS10", "FREE_DAYS", 10, null, null, null, null);
		redeem(c, "MAIS10").andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("FREE_DAYS")).andExpect(jsonPath("$.active").value(false));
		mvc.perform(asAdmin(get("/api/v1/platform/tenants/" + c.id()))).andExpect(jsonPath("$.trialEndsAt").value("2026-11-29T12:00:00Z"));

		// a primeira fatura só sai depois do novo fim do teste
		cycleAt("2026-11-22T12:00:00Z");
		assertEquals(0, invoices(c).size());
		cycleAt("2026-11-29T12:00:00Z");
		assertEquals(1, invoices(c).size());

		// cliente em dia: o período pago ganha os dias
		Client paid = newClient("Em dia", "basico", "emdia@c.com");
		jdbc.update("UPDATE tenants SET status = 'ACTIVE', current_period_end = '2026-12-01T12:00:00Z' WHERE id = ?", paid.id());
		create("MAIS5", "FREE_DAYS", 5, null, null, null, null);
		redeem(paid, "MAIS5").andExpect(status().isOk());
		mvc.perform(asAdmin(get("/api/v1/platform/tenants/" + paid.id()))).andExpect(jsonPath("$.currentPeriodEnd").value("2026-12-06T12:00:00Z"));
	}

	@Test
	void freeDaysAreRefusedWithAnOpenInvoice() throws Exception {
		clock.freeze(START);
		Client c = newClient("Devendo", "basico", "devendo@c.com");
		cycleAt("2026-11-19T12:00:00Z"); // primeira fatura em aberto
		create("DIAS", "FREE_DAYS", 10, null, null, null, null);
		redeem(c, "DIAS").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Há uma fatura em aberto. Regularize antes de usar um cupom de dias grátis."));
	}

	// ---------- plano liberado ----------

	@Test
	void anUpgradeCouponUnlocksTheBiggerPlanForAWhileWithoutChangingWhatIsBilled() throws Exception {
		clock.freeze(START);
		Client c = newClient("Subindo", "gratis", "sobe@c.com"); // grátis: 2 TVs
		long pro = planId("profissional");
		create("PRO30", "PLAN_UPGRADE", 30, null, pro, null, null).andExpect(status().isCreated()).andExpect(jsonPath("$.upgradePlanName").value("Profissional"));

		redeem(c, "PRO30").andExpect(status().isOk()).andExpect(jsonPath("$.planName").value("Profissional")).andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.until").value("2026-12-05T12:00:00Z"));

		// o que vale agora é o plano liberado; o contratado continua o mesmo
		mvc.perform(withToken(c.token(), get("/api/v1/auth/me"))).andExpect(jsonPath("$.tenant.plan.code").value("gratis"))
				.andExpect(jsonPath("$.tenant.effectivePlan.code").value("profissional")).andExpect(jsonPath("$.tenant.bonusUntil").value("2026-12-05T12:00:00Z"));
		// o plano grátis só aceita 2 usuários; com o plano liberado cabem mais
		for (int i = 0; i < 3; i++) {
			mvc.perform(withToken(c.token(), post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"U" + i + "\",\"email\":\"u" + i + "@c.com\",\"password\":\"senha-forte-1\",\"role\":\"VIEWER\"}"))).andExpect(status().isCreated());
		}
		assertEquals(0, invoices(c).size());

		// o plano liberado acabou: volta ao contratado
		jdbc.update("UPDATE tenants SET bonus_plan_until = '2026-11-01T00:00:00Z' WHERE id = ?", c.id());
		mvc.perform(withToken(c.token(), get("/api/v1/auth/me"))).andExpect(jsonPath("$.tenant.effectivePlan.code").value("gratis")).andExpect(jsonPath("$.tenant.bonusUntil").doesNotExist());
		mvc.perform(withToken(c.token(), post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Extra\",\"email\":\"extra@c.com\",\"password\":\"senha-forte-1\",\"role\":\"VIEWER\"}"))).andExpect(status().isPaymentRequired());
		cycleAt("2026-12-10T12:00:00Z");
		assertEquals(null, jdbc.queryForObject("SELECT bonus_plan_id FROM tenants WHERE id = ?", Long.class, c.id()));
	}

	@Test
	void anUpgradeCouponRefusesTheSamePlanAndASecondOneWhileTheFirstLasts() throws Exception {
		clock.freeze(START);
		Client c = newClient("Subindo", "basico", "sobe@c.com");
		create("IGUAL", "PLAN_UPGRADE", 30, null, planId("basico"), null, null);
		create("PRO", "PLAN_UPGRADE", 30, null, planId("profissional"), null, null);
		create("PRO2", "PLAN_UPGRADE", 30, null, planId("profissional"), null, null);

		redeem(c, "IGUAL").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Este cupom libera o plano que você já tem."));
		redeem(c, "PRO").andExpect(status().isOk());
		redeem(c, "PRO2").andExpect(status().isConflict()).andExpect(jsonPath("$.error", containsString("Você já usa um plano liberado por cupom até 05/12/2026")));
	}

	// ---------- regras de resgate ----------

	@Test
	void unknownInactiveExpiredAndNotYetValidCouponsAreAllJustInvalid() throws Exception {
		clock.freeze(START);
		Client c = newClient("Cliente", "basico", "c@c.com");
		create("OFF", "FREE_DAYS", 5, null, null, null, null);
		long off = idOf(mvc.perform(asAdmin(get("/api/v1/platform/coupons"))).andReturn().getResponse().getContentAsString().replaceAll("^\\[", "").replaceAll("\\].*$", ""));
		mvc.perform(asAdmin(put("/api/v1/platform/coupons/" + off).contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"OFF\",\"kind\":\"FREE_DAYS\",\"value\":5,\"active\":false}"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("OFF"));
		create("VELHO", "FREE_DAYS", 5, null, null, "2026-11-01T00:00:00Z", null);
		mvc.perform(asAdmin(post("/api/v1/platform/coupons").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"FUTURO\",\"kind\":\"FREE_DAYS\",\"value\":5,\"validFrom\":\"2026-12-01T00:00:00Z\"}"))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.state").value("SCHEDULED"));

		for (String code : new String[] { "NAOEXISTE", "OFF", "VELHO", "FUTURO" }) {
			redeem(c, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Cupom inválido ou expirado."));
		}
		redeem(c, "   ").andExpect(status().isBadRequest());
	}

	@Test
	void aClientUsesEachCouponOnceAndTheUsageLimitHolds() throws Exception {
		clock.freeze(START);
		Client a = newClient("Cliente A", "basico", "a@c.com");
		Client b = newClient("Cliente B", "basico", "b@c.com");
		Client d = newClient("Cliente D", "basico", "d@c.com");
		create("DOIS", "FREE_DAYS", 5, null, null, null, 2).andExpect(status().isCreated());

		redeem(a, "DOIS").andExpect(status().isOk());
		redeem(a, "DOIS").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Este cupom já foi resgatado por este cliente."));
		redeem(b, "DOIS").andExpect(status().isOk());
		// acabaram os usos: parece inválido para quem chega depois
		redeem(d, "DOIS").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Cupom inválido ou expirado."));
		assertEquals(2, jdbc.queryForObject("SELECT redeemed_count FROM coupons WHERE code = 'DOIS'", Integer.class));
		mvc.perform(asAdmin(get("/api/v1/platform/coupons"))).andExpect(jsonPath("$[0].state").value("EXHAUSTED"));
	}

	@Test
	void theLastUseOfACouponGoesToOneClientOnlyEvenWhenSeveralRedeemAtTheSameTime() throws Exception {
		clock.freeze(START);
		create("ULTIMO", "FREE_DAYS", 5, null, null, null, 1).andExpect(status().isCreated());
		java.util.List<Client> clients = new java.util.ArrayList<>();
		for (int i = 0; i < 6; i++) {
			clients.add(newClient("Corrida " + i, "basico", "corrida" + i + "@c.com"));
		}
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
		java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
		java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
		for (Client client : clients) {
			results.add(pool.submit(() -> {
				go.await();
				return redeem(client, "ULTIMO").andReturn().getResponse().getStatus();
			}));
		}
		go.countDown();
		int ok = 0;
		for (java.util.concurrent.Future<Integer> result : results) {
			if (result.get() == 200) {
				ok++;
			}
		}
		pool.shutdown();
		assertEquals(1, ok);
		assertEquals(1, jdbc.queryForObject("SELECT redeemed_count FROM coupons WHERE code = 'ULTIMO'", Integer.class));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM coupon_redemptions", Integer.class));
	}

	@Test
	void onlyTheClientAdministratorRedeems() throws Exception {
		clock.freeze(START);
		Client c = newClient("Cliente", "basico", "c@c.com");
		createUserRow(c.id(), "editor@c.com", "EDITOR", "senha-forte-1");
		create("DIAS", "FREE_DAYS", 5, null, null, null, null);
		String editor = loginAs("editor@c.com", "senha-forte-1");
		mvc.perform(withToken(editor, post("/api/v1/billing/coupon").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"DIAS\"}"))).andExpect(status().isForbidden());
	}

	@Test
	void guessingCodesIsStoppedAfterTooManyMisses() throws Exception {
		clock.freeze(START);
		Client c = newClient("Cliente", "basico", "c@c.com");
		create("CERTO", "FREE_DAYS", 5, null, null, null, null);
		for (int i = 0; i < 10; i++) {
			redeem(c, "errado" + i).andExpect(status().isBadRequest());
		}
		redeem(c, "errado11").andExpect(status().isTooManyRequests());
		redeem(c, "CERTO").andExpect(status().isTooManyRequests()); // nem o certo passa durante o bloqueio
		clock.freeze("2026-11-05T13:30:00Z"); // uma hora depois volta ao normal
		redeem(c, "CERTO").andExpect(status().isOk());
	}

	@Test
	void theBillingScreenListsWhatTheClientRedeemed() throws Exception {
		clock.freeze(START);
		Client c = newClient("Cliente", "basico", "c@c.com");
		create("DIAS", "FREE_DAYS", 5, null, null, null, null);
		mvc.perform(withToken(c.token(), get("/api/v1/billing"))).andExpect(jsonPath("$.coupons", hasSize(0)));
		redeem(c, "DIAS").andExpect(status().isOk());
		mvc.perform(withToken(c.token(), get("/api/v1/billing"))).andExpect(jsonPath("$.coupons", hasSize(1)))
				.andExpect(jsonPath("$.coupons[0].code").value("DIAS")).andExpect(jsonPath("$.coupons[0].kind").value("FREE_DAYS"));
	}

	// ---------- edição, exclusão, lote e histórico ----------

	@Test
	void aUsedCouponKeepsWhatItGivesButStillTakesNewLimitsAndCannotBeDeleted() throws Exception {
		clock.freeze(START);
		Client c = newClient("Cliente", "basico", "c@c.com");
		long id = idOf(create("DIAS", "FREE_DAYS", 5, null, null, null, 10).andReturn().getResponse().getContentAsString());
		redeem(c, "DIAS").andExpect(status().isOk());

		mvc.perform(asAdmin(put("/api/v1/platform/coupons/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"DIAS\",\"kind\":\"FREE_DAYS\",\"value\":99}")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error", containsString("Este cupom já foi usado")));
		mvc.perform(asAdmin(put("/api/v1/platform/coupons/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"OUTRO\",\"kind\":\"FREE_DAYS\",\"value\":5}")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("O código de um cupom não pode ser alterado"));
		mvc.perform(asAdmin(put("/api/v1/platform/coupons/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"DIAS\",\"kind\":\"FREE_DAYS\",\"value\":5,\"maxRedemptions\":3,\"description\":\"Feira\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.maxRedemptions").value(3)).andExpect(jsonPath("$.description").value("Feira"));
		mvc.perform(asAdmin(delete("/api/v1/platform/coupons/" + id))).andExpect(status().isConflict());

		long unused = idOf(create("LIVRE", "FREE_DAYS", 5, null, null, null, null).andReturn().getResponse().getContentAsString());
		mvc.perform(asAdmin(put("/api/v1/platform/coupons/" + unused).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"LIVRE\",\"kind\":\"PERCENT_OFF\",\"value\":15}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("PERCENT_OFF")).andExpect(jsonPath("$.durationMonths").value(1));
		mvc.perform(asAdmin(delete("/api/v1/platform/coupons/" + unused))).andExpect(status().isNoContent());
	}

	@Test
	void theManagerSeesWhoRedeemedACoupon() throws Exception {
		clock.freeze(START);
		Client c = newClient("Loja do Zé", "basico", "ze@c.com");
		long id = idOf(create("DIAS", "FREE_DAYS", 5, null, null, null, null).andReturn().getResponse().getContentAsString());
		redeem(c, "DIAS").andExpect(status().isOk());
		mvc.perform(asAdmin(get("/api/v1/platform/coupons/" + id + "/redemptions"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].tenantName").value("Loja do Zé")).andExpect(jsonPath("$[0].redeemedBy").value("ze@c.com"));
	}

	@Test
	void generatesABatchOfSingleUseCodes() throws Exception {
		clock.freeze(START);
		String body = "{\"prefix\":\"feira\",\"quantity\":25,\"batch\":\"Feira de outubro\",\"kind\":\"FREE_DAYS\",\"value\":15,\"validUntil\":\"2026-12-31T00:00:00Z\"}";
		String response = mvc.perform(asAdmin(post("/api/v1/platform/coupons/batch").contentType(MediaType.APPLICATION_JSON).content(body)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.batch").value("Feira de outubro")).andExpect(jsonPath("$.coupons", hasSize(25)))
				.andExpect(jsonPath("$.coupons[0].code", matchesPattern("FEIRA-[A-Z2-9]{4}-[A-Z2-9]{4}"))).andExpect(jsonPath("$.coupons[0].maxRedemptions").value(1))
				.andReturn().getResponse().getContentAsString();
		Set<String> codes = new HashSet<>();
		json.readTree(response).get("coupons").forEach(coupon -> codes.add(coupon.get("code").asText()));
		assertEquals(25, codes.size()); // todos diferentes

		// cada código vale para um cliente só
		String code = codes.iterator().next();
		Client a = newClient("Cliente A", "basico", "a@c.com");
		Client b = newClient("Cliente B", "basico", "b@c.com");
		redeem(a, code.toLowerCase()).andExpect(status().isOk());
		redeem(b, code).andExpect(status().isBadRequest());

		mvc.perform(asAdmin(post("/api/v1/platform/coupons/batch").contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":501,\"kind\":\"FREE_DAYS\",\"value\":5}"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(post("/api/v1/platform/coupons/batch").contentType(MediaType.APPLICATION_JSON)
				.content("{\"prefix\":\"a b\",\"quantity\":2,\"kind\":\"FREE_DAYS\",\"value\":5}"))).andExpect(status().isBadRequest());
	}

}
