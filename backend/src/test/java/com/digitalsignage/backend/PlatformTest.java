package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** A operação da plataforma: planos, clientes e a visão geral. */
class PlatformTest extends AbstractApiTest {

	private long planId(String code) {
		return jdbc.queryForObject("SELECT id FROM plans WHERE code = ?", Long.class, code);
	}

	private ResultActions createPlan(String code, int price, Integer devices, Integer storage, Integer users) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("name", "Plano " + code);
		body.put("priceCents", price);
		body.put("maxDevices", devices);
		body.put("maxStorageMb", storage);
		body.put("maxUsers", users);
		return mvc.perform(asAdmin(post("/api/v1/platform/plans").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private ResultActions createTenant(String name, long planId, String adminEmail) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", name, "planId", planId, "adminName", "Dona " + name, "adminEmail", adminEmail, "adminPassword", "senha-forte-1")))));
	}

	// ---------- planos ----------

	@Test
	void shipsTheSevenOfferPlansAfterTheFourOriginalOnesInOrderWithTheirLimits() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/platform/plans")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].code", contains("interno", "gratis", "basico", "profissional", "start", "pro", "business", "rede", "comodato-basico", "comodato-completo", "comodato-premium")))
				.andExpect(jsonPath("$[0].maxDevices").doesNotExist()) // interno: sem limite
				.andExpect(jsonPath("$[1].maxDevices").value(2))
				.andExpect(jsonPath("$[2].priceCents").value(4900))
				.andExpect(jsonPath("$[0].tenantCount").value(2)); // a reserva e o cliente original
	}

	@Test
	void createsEditsAndDeletesAPlan() throws Exception {
		long id = idOf(createPlan("escola", 7900, 10, 5000, 8).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Plano escola")).andExpect(jsonPath("$.currency").value("BRL"))
				.andReturn().getResponse().getContentAsString());

		mvc.perform(asAdmin(put("/api/v1/platform/plans/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"code", "escola", "name", "Escola Plus", "priceCents", 9900, "maxDevices", 20, "active", true, "sortOrder", 9)))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Escola Plus")).andExpect(jsonPath("$.priceCents").value(9900))
				.andExpect(jsonPath("$.maxDevices").value(20)).andExpect(jsonPath("$.maxStorageMb").doesNotExist());
		mvc.perform(asAdmin(put("/api/v1/platform/plans/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"code", "outro-codigo", "name", "x", "priceCents", 1)))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("O código de um plano não pode ser alterado"));
		mvc.perform(asAdmin(delete("/api/v1/platform/plans/" + id))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/platform/plans"))).andExpect(jsonPath("$", hasSize(11))); // os 4 originais e os 7 da oferta, sem o plano de teste apagado
	}

	@Test
	void validatesPlans() throws Exception {
		createPlan("basico", 100, 1, 1, 1).andExpect(status().isConflict()); // código já existe
		createPlan("Código Inválido", 100, 1, 1, 1).andExpect(status().isBadRequest());
		createPlan("negativo", -5, 1, 1, 1).andExpect(status().isBadRequest());
		createPlan("sem-limites", 0, null, null, null).andExpect(status().isCreated()).andExpect(jsonPath("$.maxDevices").doesNotExist());
	}

	@Test
	void aPlanInUseCannotBeDeleted() throws Exception {
		mvc.perform(asAdmin(delete("/api/v1/platform/plans/" + planId("interno")))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("O plano \"Interno (sem limites)\" é usado por 2 cliente(s). Troque o plano deles ou desative este plano em vez de excluir."));
	}

	// ---------- clientes ----------

	@Test
	void aPaidPlanStartsAClientOnATrialAndTheFirstAdministratorCanLogIn() throws Exception {
		clock.freeze("2026-11-05T12:00:00Z");

		createTenant("Padaria do Zé", planId("basico"), "ze@padaria.com").andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Padaria do Zé"))
				.andExpect(jsonPath("$.slug").value("padaria-do-ze"))
				.andExpect(jsonPath("$.status").value("TRIAL"))
				.andExpect(jsonPath("$.trialEndsAt").value("2026-11-19T12:00:00Z")) // 14 dias
				.andExpect(jsonPath("$.plan.code").value("basico"))
				.andExpect(jsonPath("$.usage.users").value(1))
				.andExpect(jsonPath("$.usage.devices").value(0));

		String token = loginAs("ze@padaria.com", "senha-forte-1");
		mvc.perform(withToken(token, get("/api/v1/auth/me")))
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.tenant.name").value("Padaria do Zé"))
				.andExpect(jsonPath("$.platformAdmin").value(false));
		// e já está isolado: não vê nada do cliente original
		mvc.perform(withToken(token, get("/api/v1/devices"))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void aFreePlanStartsActiveAndTheSlugStaysUnique() throws Exception {
		createTenant("Loja Teste", planId("gratis"), "a@loja.com").andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.trialEndsAt").doesNotExist());
		// outro cliente com nome diferente que gera o mesmo slug
		createTenant("Loja  Teste!", planId("gratis"), "b@loja.com").andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value("loja-teste-2"));
	}

	@Test
	void validatesTheNewClient() throws Exception {
		createTenant("Cliente X", planId("gratis"), "x@x.com").andExpect(status().isCreated());

		createTenant("cliente x", planId("gratis"), "y@x.com").andExpect(status().isConflict()); // nome (sem diferenciar maiúsculas)
		createTenant("Cliente Y", planId("gratis"), "X@x.com").andExpect(status().isConflict()); // e-mail do administrador, global
		createTenant("Cliente Z", 999999, "z@x.com").andExpect(status().isNotFound());
		mvc.perform(asAdmin(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Cliente W", "planId", planId("gratis"), "adminName", "W", "adminEmail", "w@x.com", "adminPassword", "curta")))))
				.andExpect(status().isBadRequest());
		// plano desativado não é oferecido
		jdbc.update("UPDATE plans SET active = false WHERE code = 'gratis'");
		createTenant("Cliente V", planId("gratis"), "v@x.com").andExpect(status().isBadRequest());
		jdbc.update("UPDATE plans SET active = true WHERE code = 'gratis'");
	}

	@Test
	void listsTheClientsWithoutTheReserveAndOnlyForTheOperator() throws Exception {
		createTenant("Cliente A", planId("gratis"), "a@a.com").andExpect(status().isCreated());

		mvc.perform(asAdmin(get("/api/v1/platform/tenants")))
				.andExpect(jsonPath("$[*].name", hasItems("Minha empresa", "Cliente A")))
				.andExpect(jsonPath("$[?(@.slug=='pool')]").isEmpty());
		mvc.perform(asAdmin(get("/api/v1/platform/tenants/1"))).andExpect(status().isNotFound()); // a reserva não é um cliente
		mvc.perform(get("/api/v1/platform/tenants")).andExpect(status().isUnauthorized());
	}

	@Test
	void changesPlanAndStatusButRefusesAPlanTheClientAlreadyOutgrew() throws Exception {
		long id = idOf(createTenant("Cliente A", planId("profissional"), "a@a.com").andReturn().getResponse().getContentAsString());
		String adminToken = loginAs("a@a.com", "senha-forte-1");
		for (int i = 0; i < 3; i++) { // 4 usuários ativos no total, mais que o plano grátis (2)
			mvc.perform(withToken(adminToken, post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
					"name", "U" + i, "email", "u" + i + "@a.com", "password", "senha-forte-1", "role", "VIEWER"))))).andExpect(status().isCreated());
		}

		mvc.perform(asAdmin(put("/api/v1/platform/tenants/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("planId", planId("gratis"))))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("O cliente tem 4 usuário(s) ativos e o plano Grátis permite 2."));
		mvc.perform(asAdmin(put("/api/v1/platform/tenants/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("planId", planId("basico"))))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.plan.code").value("basico"));

		mvc.perform(asAdmin(put("/api/v1/platform/tenants/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"status", "SUSPENDED", "notes", "Pagamento em disputa", "billingEmail", "financeiro@a.com")))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED")).andExpect(jsonPath("$.billingEmail").value("financeiro@a.com"));
		mvc.perform(withToken(adminToken, get("/api/v1/devices"))).andExpect(status().isPaymentRequired());
		mvc.perform(asAdmin(put("/api/v1/platform/tenants/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SYSTEM\"}"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(put("/api/v1/platform/tenants/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))).andExpect(status().isOk());
		mvc.perform(withToken(adminToken, get("/api/v1/devices"))).andExpect(status().isOk());
	}

	@Test
	void theOperatorEntersAClientToHelpAndTheClientsDataStaysThere() throws Exception {
		long id = idOf(createTenant("Cliente A", planId("gratis"), "a@a.com").andReturn().getResponse().getContentAsString());

		// o operador "entra" no cliente e cria um local lá
		mvc.perform(asAdmin(post("/api/v1/locations")).header("X-Tenant-Id", String.valueOf(id)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Criada pela operação\"}"))
				.andExpect(status().isCreated());

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM locations WHERE tenant_id = ?", Integer.class, id));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM locations WHERE tenant_id = ?", Integer.class, DEFAULT_TENANT));
		mvc.perform(withToken(loginAs("a@a.com", "senha-forte-1"), get("/api/v1/locations"))).andExpect(jsonPath("$", hasSize(1)));
		// e o operador pode gerir a equipe do cliente em que entrou
		mvc.perform(asAdmin(get("/api/v1/users")).header("X-Tenant-Id", String.valueOf(id))).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].email").value("a@a.com"));
	}

	// ---------- visão geral ----------

	@Test
	void theOverviewCountsClientsDevicesAndRevenue() throws Exception {
		clock.freeze("2026-11-05T12:00:00Z");
		createTenant("Pagante", planId("profissional"), "p@p.com").andExpect(status().isCreated()); // TRIAL (não conta no MRR)
		long active = idOf(createTenant("Ativo", planId("basico"), "b@b.com").andReturn().getResponse().getContentAsString());
		mvc.perform(asAdmin(put("/api/v1/platform/tenants/" + active).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))).andExpect(status().isOk());
		createTenant("Gratuito", planId("gratis"), "g@g.com").andExpect(status().isCreated()); // ACTIVE, preço 0

		mvc.perform(asAdmin(get("/api/v1/platform/overview")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenants").value(4)) // + o cliente original
				.andExpect(jsonPath("$.tenantsByStatus.TRIAL").value(1))
				.andExpect(jsonPath("$.tenantsByStatus.ACTIVE").value(3))
				.andExpect(jsonPath("$.monthlyRecurringCents").value(4900))
				.andExpect(jsonPath("$.openInvoices").value(0));
	}

	@Test
	void everyoneButTheOperatorIsRefused() throws Exception {
		long tenant = createTenantRow("Cliente Comum", "interno", "ACTIVE");
		createUserRow(tenant, "adm-" + UUID.randomUUID() + "@c.com", "ADMIN", "senha-forte-1");
		String email = jdbc.queryForObject("SELECT email FROM users WHERE tenant_id = ?", String.class, tenant);
		String token = loginAs(email, "senha-forte-1");

		for (String path : new String[] { "/platform/plans", "/platform/tenants", "/platform/invoices", "/platform/settings", "/platform/overview" }) {
			mvc.perform(withToken(token, get("/api/v1" + path))).andExpect(status().isForbidden());
		}
	}

}
