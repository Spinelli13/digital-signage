package com.digitalsignage.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.digitalsignage.backend.privacy.PrivacyNotice;
import com.digitalsignage.backend.privacy.PrivacyService;
import com.digitalsignage.backend.promo.PublicPromotionService;
import com.digitalsignage.backend.queue.PublicQueueService;
import com.fasterxml.jackson.databind.JsonNode;

/** LGPD: prazos de guarda, aviso e concordância nas páginas públicas, e o atendimento de pedidos de titulares. */
class PrivacyTest extends AbstractApiTest {

	private static final String START = "2026-11-05T12:00:00Z";

	@Autowired
	private PublicQueueService publicQueues;

	@Autowired
	private PublicPromotionService publicPromotions;

	@Autowired
	private PrivacyService privacy;

	@BeforeEach
	void reset() {
		publicQueues.forgetClients();
		publicPromotions.forgetClients();
		clock.freeze(START);
	}

	private record Shop(long tenantId, String admin, String editor, String viewer) {
	}

	private Shop shop(String name) throws Exception {
		long tenant = createTenantRow(name, "interno", "ACTIVE");
		String tag = tenant + "";
		createUserRow(tenant, "admin" + tag + "@loja.com", "ADMIN", "senha-forte-1");
		createUserRow(tenant, "editor" + tag + "@loja.com", "EDITOR", "senha-forte-1");
		createUserRow(tenant, "viewer" + tag + "@loja.com", "VIEWER", "senha-forte-1");
		return new Shop(tenant, loginAs("admin" + tag + "@loja.com", "senha-forte-1"), loginAs("editor" + tag + "@loja.com", "senha-forte-1"),
				loginAs("viewer" + tag + "@loja.com", "senha-forte-1"));
	}

	private String queueToken(Shop shop) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("name", "Geral");
		body.put("prefix", "A");
		body.put("remoteEnabled", true);
		JsonNode queue = json.readTree(mvc.perform(withToken(shop.admin(), post("/api/v1/queues").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		return queue.get("publicToken").asText();
	}

	private String promotionToken(Shop shop, String name) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		JsonNode promotion = json.readTree(mvc.perform(withToken(shop.editor(), post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		return promotion.get("publicToken").asText();
	}

	/** Sem login: é como o celular de quem escaneou o QR Code chama. */
	private ResultActions takeTicket(String queueToken, String name, boolean consent, String ip) throws Exception {
		Map<String, Object> body = new HashMap<>();
		if (name != null) {
			body.put("customerName", name);
		}
		if (consent) {
			body.put("consent", true);
		}
		return mvc.perform(post("/api/v1/public/queues/" + queueToken + "/tickets").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", ip)
				.content(json.writeValueAsString(body)));
	}

	private ResultActions claimCoupon(String promotionToken, String name, boolean consent, String device) throws Exception {
		Map<String, Object> body = new HashMap<>();
		if (name != null) {
			body.put("customerName", name);
		}
		if (consent) {
			body.put("consent", true);
		}
		return mvc.perform(post("/api/v1/public/promotions/" + promotionToken + "/coupons").contentType(MediaType.APPLICATION_JSON)
				.header("X-Client-Id", device).header("X-Forwarded-For", "10.9.9.9").content(json.writeValueAsString(body)));
	}

	private ResultActions subject(String token, String path, String term, String note) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("term", term);
		body.put("note", note);
		return mvc.perform(withToken(token, post("/api/v1/privacy/" + path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	// ---------- configurações ----------

	@Test
	void onlyTheAdminOfTheClientSeesAndChangesThePrivacySettings() throws Exception {
		Shop s = shop("Loja Aurora");

		for (String token : new String[] { s.viewer(), s.editor() }) {
			mvc.perform(withToken(token, get("/api/v1/privacy/settings"))).andExpect(status().isForbidden());
			mvc.perform(withToken(token, put("/api/v1/privacy/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))).andExpect(status().isForbidden());
			mvc.perform(withToken(token, get("/api/v1/privacy/search").param("q", "maria"))).andExpect(status().isForbidden());
			mvc.perform(withToken(token, get("/api/v1/privacy/requests"))).andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/v1/privacy/settings")).andExpect(status().isUnauthorized());

		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/settings"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.ticketRetentionDays").value(30))
				.andExpect(jsonPath("$.couponNameRetentionDays").value(90))
				.andExpect(jsonPath("$.eventsRetentionDays").value(90))
				.andExpect(jsonPath("$.playLogRetentionDays").value(14))
				.andExpect(jsonPath("$.contactEmail").doesNotExist());
	}

	@Test
	void retentionAndContactAreChangedAndValidated() throws Exception {
		Shop s = shop("Loja Aurora");
		String body = "{\"contactName\":\"Ana Souza\",\"contactEmail\":\"privacidade@loja.com\",\"ticketRetentionDays\":10,\"couponNameRetentionDays\":45,\"eventsRetentionDays\":30,\"playLogRetentionDays\":20}";

		mvc.perform(withToken(s.admin(), put("/api/v1/privacy/settings").contentType(MediaType.APPLICATION_JSON).content(body))).andExpect(status().isOk())
				.andExpect(jsonPath("$.ticketRetentionDays").value(10))
				.andExpect(jsonPath("$.couponNameRetentionDays").value(45))
				.andExpect(jsonPath("$.contactEmail").value("privacidade@loja.com"));
		assertEquals(30, jdbc.queryForObject("SELECT events_retention_days FROM monitoring_settings WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertEquals(20, jdbc.queryForObject("SELECT play_log_retention_days FROM monitoring_settings WHERE tenant_id = ?", Integer.class, s.tenantId()));

		for (String invalid : new String[] { "{\"ticketRetentionDays\":0}", "{\"ticketRetentionDays\":366}", "{\"couponNameRetentionDays\":0}",
				"{\"eventsRetentionDays\":6}", "{\"playLogRetentionDays\":400}", "{\"contactEmail\":\"isso-nao-e-email\"}" }) {
			mvc.perform(withToken(s.admin(), put("/api/v1/privacy/settings").contentType(MediaType.APPLICATION_JSON).content(invalid))).andExpect(status().isBadRequest());
		}
		// em branco limpa o contato
		mvc.perform(withToken(s.admin(), put("/api/v1/privacy/settings").contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"\",\"contactName\":\"\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.contactEmail").doesNotExist()).andExpect(jsonPath("$.ticketRetentionDays").value(10));
	}

	// ---------- o aviso e a concordância nas páginas públicas ----------

	@Test
	void theQueuePageCarriesTheNoticeAndTheNameOnlyIsKeptWithConsent() throws Exception {
		Shop s = shop("Loja Aurora");
		String token = queueToken(s);
		mvc.perform(withToken(s.admin(), put("/api/v1/privacy/settings").contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"privacidade@loja.com\",\"ticketRetentionDays\":15}")))
				.andExpect(status().isOk());

		mvc.perform(get("/api/v1/public/queues/" + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.notice.controllerName").value("Loja Aurora"))
				.andExpect(jsonPath("$.notice.contactEmail").value("privacidade@loja.com"))
				.andExpect(jsonPath("$.notice.retentionDays").value(15))
				.andExpect(jsonPath("$.notice.version").value(PrivacyNotice.VERSION));

		takeTicket(token, "Maria Souza", false, "10.0.0.1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Para informar o nome, é preciso concordar com o aviso de privacidade"));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ?", Integer.class, s.tenantId()));

		takeTicket(token, "Maria Souza", true, "10.0.0.1").andExpect(status().isCreated());
		assertEquals("Maria Souza", jdbc.queryForObject("SELECT customer_name FROM tickets WHERE tenant_id = ?", String.class, s.tenantId()));
		assertNotNull(jdbc.queryForObject("SELECT consent_at FROM tickets WHERE tenant_id = ?", java.sql.Timestamp.class, s.tenantId()));
		assertEquals(PrivacyNotice.VERSION, jdbc.queryForObject("SELECT consent_notice FROM tickets WHERE tenant_id = ?", String.class, s.tenantId()));

		// sem nome não há dado pessoal: nem concordância nem registro dela
		takeTicket(token, null, false, "10.0.0.2").andExpect(status().isCreated());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ? AND customer_name IS NULL AND consent_at IS NULL", Integer.class, s.tenantId()));
	}

	@Test
	void theCouponPageCarriesTheNoticeAndANameWithoutConsentDoesNotSpendACoupon() throws Exception {
		Shop s = shop("Loja Aurora");
		String token = promotionToken(s, "10% na primeira compra");

		mvc.perform(get("/api/v1/public/promotions/" + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.notice.controllerName").value("Loja Aurora"))
				.andExpect(jsonPath("$.notice.retentionDays").value(90))
				.andExpect(jsonPath("$.notice.version").value(PrivacyNotice.VERSION));

		claimCoupon(token, "Maria Souza", false, "aparelho-0001").andExpect(status().isBadRequest());
		assertEquals(0, jdbc.queryForObject("SELECT claimed_count FROM promotions WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM promotion_coupons WHERE tenant_id = ?", Integer.class, s.tenantId()));

		claimCoupon(token, "Maria Souza", true, "aparelho-0001").andExpect(status().isCreated());
		assertEquals("Maria Souza", jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ?", String.class, s.tenantId()));
		assertEquals(PrivacyNotice.VERSION, jdbc.queryForObject("SELECT consent_notice FROM promotion_coupons WHERE tenant_id = ?", String.class, s.tenantId()));

		claimCoupon(token, null, false, "aparelho-0002").andExpect(status().isCreated());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM promotion_coupons WHERE tenant_id = ? AND claimant_name IS NULL AND consent_at IS NULL", Integer.class, s.tenantId()));
	}

	// ---------- pedidos de titulares ----------

	@Test
	void theAdminFindsExportsAndErasesThePersonalDataOfOnePerson() throws Exception {
		Shop s = shop("Loja Aurora");
		String queue = queueToken(s);
		String promotion = promotionToken(s, "10% na primeira compra");
		createUserRow(s.tenantId(), "maria.souza@loja.com", "VIEWER", "senha-forte-1");

		takeTicket(queue, "Maria Souza", true, "10.0.0.1").andExpect(status().isCreated());
		takeTicket(queue, "João Lima", true, "10.0.0.2").andExpect(status().isCreated());
		claimCoupon(promotion, "Maria Souza", true, "aparelho-0001").andExpect(status().isCreated());
		claimCoupon(promotion, "João Lima", true, "aparelho-0002").andExpect(status().isCreated());

		// achar: pelo nome (sem diferenciar maiúsculas), só o que é dela
		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/search").param("q", "maria souza"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.tickets", hasSize(1))).andExpect(jsonPath("$.tickets[0].name").value("Maria Souza")).andExpect(jsonPath("$.tickets[0].queue").value("Geral"))
				.andExpect(jsonPath("$.coupons", hasSize(1))).andExpect(jsonPath("$.coupons[0].promotion").value("10% na primeira compra"))
				.andExpect(jsonPath("$.users", hasSize(0)));
		// pelo e-mail, acha a pessoa da equipe
		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/search").param("q", "maria.souza@"))).andExpect(status().isOk()).andExpect(jsonPath("$.users", hasSize(1)))
				.andExpect(jsonPath("$.users[0].email").value("maria.souza@loja.com")).andExpect(jsonPath("$.users[0].password").doesNotExist())
				.andExpect(jsonPath("$.users[0].passwordHash").doesNotExist());
		// pouco texto não busca nada; curinga do LIKE não vale como curinga
		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/search").param("q", "ma"))).andExpect(status().isBadRequest());
		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/search").param("q", "%%%"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.tickets", hasSize(0))).andExpect(jsonPath("$.coupons", hasSize(0)));

		// o código do cupom também acha a pessoa
		String code = jdbc.queryForObject("SELECT code FROM promotion_coupons WHERE tenant_id = ? AND claimant_name = 'Maria Souza'", String.class, s.tenantId());
		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/search").param("q", code.toLowerCase().replace("-", " ")))).andExpect(status().isOk())
				.andExpect(jsonPath("$.coupons", hasSize(1))).andExpect(jsonPath("$.coupons[0].code").value(code));

		// exportar: arquivo JSON só com os dados dela, sem a senha de ninguém, e o pedido fica registrado
		subject(s.admin(), "export", "Maria Souza", "Protocolo 123").andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(header().string("Content-Disposition", containsString("dados-pessoais-")))
				.andExpect(jsonPath("$.controlador").value("Loja Aurora"))
				.andExpect(jsonPath("$.senhasDaFila", hasSize(1))).andExpect(jsonPath("$.senhasDaFila[0].nome").value("Maria Souza"))
				.andExpect(jsonPath("$.senhasDaFila[0].concordouComOAvisoEm").exists())
				.andExpect(jsonPath("$.cupons", hasSize(1))).andExpect(jsonPath("$.cupons[0].codigo").value(code))
				.andExpect(content().string(not(containsString("João"))))
				.andExpect(content().string(not(containsString("senha-forte"))));
		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/requests"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].kind").value("EXPORT")).andExpect(jsonPath("$[0].tickets").value(1)).andExpect(jsonPath("$[0].coupons").value(1))
				.andExpect(jsonPath("$[0].note").value("Protocolo 123")).andExpect(jsonPath("$[0].performedBy").value("admin" + s.tenantId() + "@loja.com"))
				.andExpect(content().string(not(containsString("Maria"))));

		// apagar: some o nome, o aparelho e a concordância; a senha e o cupom ficam, sem nada que identifique; a equipe só é contada
		subject(s.admin(), "erase", "Maria Souza", null).andExpect(status().isOk()).andExpect(jsonPath("$.tickets").value(1)).andExpect(jsonPath("$.coupons").value(1))
				.andExpect(jsonPath("$.usersFound").value(0));
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ? AND customer_name IS NULL AND consent_at IS NULL AND consent_notice IS NULL", Integer.class, s.tenantId()));
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM promotion_coupons WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertNull(jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ? AND code = ?", String.class, s.tenantId(), code));
		assertNull(jdbc.queryForObject("SELECT client_id FROM promotion_coupons WHERE tenant_id = ? AND code = ?", String.class, s.tenantId(), code));
		assertNull(jdbc.queryForObject("SELECT consent_at FROM promotion_coupons WHERE tenant_id = ? AND code = ?", java.sql.Timestamp.class, s.tenantId(), code));
		// o dado do João continua lá
		assertEquals("João Lima", jdbc.queryForObject("SELECT customer_name FROM tickets WHERE tenant_id = ? AND customer_name IS NOT NULL", String.class, s.tenantId()));
		assertEquals("João Lima", jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ? AND claimant_name IS NOT NULL", String.class, s.tenantId()));
		mvc.perform(withToken(s.admin(), get("/api/v1/privacy/requests"))).andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].kind").value("ERASE"));

		// de novo: não há mais o que apagar
		subject(s.admin(), "erase", "Maria Souza", null).andExpect(status().isOk()).andExpect(jsonPath("$.tickets").value(0)).andExpect(jsonPath("$.coupons").value(0));
		// quem não é ADMIN não pede cópia nem exclusão
		subject(s.editor(), "export", "João Lima", null).andExpect(status().isForbidden());
		subject(s.editor(), "erase", "João Lima", null).andExpect(status().isForbidden());
		subject(s.admin(), "erase", "ab", null).andExpect(status().isBadRequest());
	}

	@Test
	void aClientNeverSeesOrErasesTheDataOfAnotherClient() throws Exception {
		Shop a = shop("Loja A");
		Shop b = shop("Loja B");
		String queueA = queueToken(a);
		takeTicket(queueA, "Maria Souza", true, "10.0.0.1").andExpect(status().isCreated());

		mvc.perform(withToken(b.admin(), get("/api/v1/privacy/search").param("q", "Maria"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.tickets", hasSize(0))).andExpect(jsonPath("$.coupons", hasSize(0))).andExpect(jsonPath("$.users", hasSize(0)));
		subject(b.admin(), "erase", "Maria Souza", null).andExpect(status().isOk()).andExpect(jsonPath("$.tickets").value(0));
		assertEquals("Maria Souza", jdbc.queryForObject("SELECT customer_name FROM tickets WHERE tenant_id = ?", String.class, a.tenantId()));
		mvc.perform(withToken(b.admin(), get("/api/v1/privacy/requests"))).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(withToken(a.admin(), get("/api/v1/privacy/requests"))).andExpect(jsonPath("$", hasSize(0)));
	}

	// ---------- prazos ----------

	@Test
	void whatPassedTheClientsRetentionIsErasedEvenWithNobodyIssuingTicketsOrCoupons() throws Exception {
		Shop s = shop("Loja Aurora");
		String queue = queueToken(s);
		String promotion = promotionToken(s, "10% na primeira compra");
		mvc.perform(withToken(s.admin(), put("/api/v1/privacy/settings").contentType(MediaType.APPLICATION_JSON).content("{\"ticketRetentionDays\":10,\"couponNameRetentionDays\":20}")))
				.andExpect(status().isOk());

		takeTicket(queue, "Maria Souza", true, "10.0.0.1").andExpect(status().isCreated());
		claimCoupon(promotion, "Maria Souza", true, "aparelho-0001").andExpect(status().isCreated());

		clock.freeze("2026-11-12T12:00:00Z"); // 7 dias depois: dentro dos dois prazos
		inTenant(s.tenantId(), () -> privacy.purgeExpired(clock.instant()));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertEquals("Maria Souza", jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ?", String.class, s.tenantId()));

		clock.freeze("2026-11-17T12:00:00Z"); // 12 dias: a senha passou de 10; o nome do cupom ainda não (20)
		inTenant(s.tenantId(), () -> privacy.purgeExpired(clock.instant()));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertEquals("Maria Souza", jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ?", String.class, s.tenantId()));

		clock.freeze("2026-11-27T12:00:00Z"); // 22 dias: o nome do cupom some, o cupom fica
		inTenant(s.tenantId(), () -> privacy.purgeExpired(clock.instant()));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM promotion_coupons WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertNull(jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ?", String.class, s.tenantId()));
		assertNull(jdbc.queryForObject("SELECT consent_at FROM promotion_coupons WHERE tenant_id = ?", java.sql.Timestamp.class, s.tenantId()));
	}

	@Test
	void theRetentionOfOneClientDoesNotTouchTheOther() throws Exception {
		Shop a = shop("Loja A");
		Shop b = shop("Loja B");
		takeTicket(queueToken(a), "Maria", true, "10.0.0.1").andExpect(status().isCreated());
		takeTicket(queueToken(b), "Ana", true, "10.0.0.2").andExpect(status().isCreated());
		mvc.perform(withToken(a.admin(), put("/api/v1/privacy/settings").contentType(MediaType.APPLICATION_JSON).content("{\"ticketRetentionDays\":1}"))).andExpect(status().isOk());

		clock.freeze("2026-11-08T12:00:00Z");
		inTenant(a.tenantId(), () -> privacy.purgeExpired(clock.instant()));
		inTenant(b.tenantId(), () -> privacy.purgeExpired(clock.instant()));

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ?", Integer.class, a.tenantId()));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tickets WHERE tenant_id = ?", Integer.class, b.tenantId()));
	}

	// ---------- quem responde pelo tratamento (plataforma) ----------

	@Test
	void thePlatformPublishesWhoIsResponsibleForTheData() throws Exception {
		mvc.perform(get("/api/v1/public/privacy")).andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Vyzzu"))
				.andExpect(jsonPath("$.legalName").doesNotExist()).andExpect(jsonPath("$.version").value(PrivacyNotice.VERSION));

		String body = "{\"legalName\":\"Vyzzu Tecnologia Ltda\",\"legalDocument\":\"00.000.000/0001-00\",\"privacyContactName\":\"Encarregado de Dados\",\"privacyContactEmail\":\"privacidade@vyzzu.com\"}";
		mvc.perform(asAdmin(put("/api/v1/platform/settings").contentType(MediaType.APPLICATION_JSON).content(body))).andExpect(status().isOk())
				.andExpect(jsonPath("$.legalName").value("Vyzzu Tecnologia Ltda")).andExpect(jsonPath("$.privacyContactEmail").value("privacidade@vyzzu.com"));

		// qualquer pessoa, sem login, vê quem é o responsável
		mvc.perform(get("/api/v1/public/privacy")).andExpect(status().isOk()).andExpect(jsonPath("$.legalName").value("Vyzzu Tecnologia Ltda"))
				.andExpect(jsonPath("$.legalDocument").value("00.000.000/0001-00")).andExpect(jsonPath("$.contactName").value("Encarregado de Dados"))
				.andExpect(jsonPath("$.contactEmail").value("privacidade@vyzzu.com"));
	}

}
