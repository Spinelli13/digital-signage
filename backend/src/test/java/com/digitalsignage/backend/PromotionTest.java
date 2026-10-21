package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.promo.PublicPromotionService;
import com.fasterxml.jackson.databind.JsonNode;

/** Cupons para os clientes da loja: o QR Code da TV, o cupom do consumidor e a baixa no caixa. */
class PromotionTest extends AbstractApiTest {

	private static final String START = "2026-11-05T12:00:00Z";

	@Autowired
	private PublicPromotionService publicService;

	@BeforeEach
	void reset() {
		publicService.forgetClients();
		clock.freeze(START);
	}

	private record Shop(long tenantId, String admin, String editor, String viewer) {
	}

	private Shop shop(String name, String planCode) throws Exception {
		long tenant = createTenantRow(name, planCode, "ACTIVE");
		String tag = tenant + "";
		createUserRow(tenant, "admin" + tag + "@loja.com", "ADMIN", "senha-forte-1");
		createUserRow(tenant, "editor" + tag + "@loja.com", "EDITOR", "senha-forte-1");
		createUserRow(tenant, "viewer" + tag + "@loja.com", "VIEWER", "senha-forte-1");
		return new Shop(tenant, loginAs("admin" + tag + "@loja.com", "senha-forte-1"), loginAs("editor" + tag + "@loja.com", "senha-forte-1"),
				loginAs("viewer" + tag + "@loja.com", "senha-forte-1"));
	}

	private ResultActions create(String token, String name, Integer claimDays, Integer maxClaims, String from, String until) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("description", "Vale na primeira compra");
		body.put("claimDays", claimDays);
		body.put("maxClaims", maxClaims);
		body.put("validFrom", from);
		body.put("validUntil", until);
		return mvc.perform(withToken(token, post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private JsonNode promotion(Shop shop, String name, Integer claimDays, Integer maxClaims) throws Exception {
		return json.readTree(create(shop.editor(), name, claimDays, maxClaims, null, null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Sem login: é como o celular de quem escaneou o QR Code chama. */
	private ResultActions claim(String token, String name, String device, String network) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/public/promotions/" + token + "/coupons").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(name == null ? Map.of() : Map.of("customerName", name)));
		if (device != null) {
			request.header("X-Client-Id", device);
		}
		if (network != null) {
			request.header("X-Forwarded-For", network);
		}
		return mvc.perform(request);
	}

	private JsonNode claimed(String token, String device) throws Exception {
		return json.readTree(claim(token, null, device, null).andReturn().getResponse().getContentAsString());
	}

	private ResultActions redeem(String token, String code) throws Exception {
		return mvc.perform(withToken(token, post("/api/v1/promotions/coupons/redeem").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", code)))));
	}

	private static String device(int n) {
		return "aparelho-" + String.format("%04d", n) + "-abcdef";
	}

	// ---------- montar a promoção ----------

	@Test
	void anEditorCreatesAPromotionAndTheSecretOfTheQrCodeOnlyGoesToEditors() throws Exception {
		Shop s = shop("Loja", "interno");
		JsonNode p = promotion(s, "10% na primeira compra", 7, 100);

		assertTrue(p.get("publicToken").asText().matches("[0-9a-f]{32}"));
		assertEquals("ACTIVE", p.get("state").asText());
		assertEquals(0, p.get("claimedCount").asInt());
		mvc.perform(withToken(s.editor(), get("/api/v1/promotions"))).andExpect(jsonPath("$[0].publicToken").value(p.get("publicToken").asText()));
		mvc.perform(withToken(s.viewer(), get("/api/v1/promotions"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("10% na primeira compra"))
				.andExpect(jsonPath("$[0].publicToken").value(nullValue()));
		// só quem edita monta e dá baixa
		create(s.viewer(), "Outra", 7, 10, null, null).andExpect(status().isForbidden());
		redeem(s.viewer(), "ABCD-2345").andExpect(status().isForbidden());
	}

	@Test
	void validatesThePromotion() throws Exception {
		Shop s = shop("Loja", "interno");
		promotion(s, "Frete grátis", 7, null);
		create(s.editor(), "frete GRÁTIS", 7, null, null, null).andExpect(status().isConflict());
		create(s.editor(), " ", 7, null, null, null).andExpect(status().isBadRequest());
		create(s.editor(), "Zero dias", 0, null, null, null).andExpect(status().isBadRequest());
		create(s.editor(), "Zero cupons", 7, 0, null, null).andExpect(status().isBadRequest());
		create(s.editor(), "Volta no tempo", 7, null, "2026-12-01T00:00:00Z", "2026-11-01T00:00:00Z").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("O fim da promoção precisa ser depois do início"));
	}

	@Test
	void theStateTellsIfThePromotionIsOpen() throws Exception {
		Shop s = shop("Loja", "interno");
		create(s.editor(), "Futura", 7, null, "2026-12-01T00:00:00Z", null).andExpect(jsonPath("$.state").value("SCHEDULED"));
		create(s.editor(), "Velha", 7, null, null, "2026-11-01T00:00:00Z").andExpect(jsonPath("$.state").value("ENDED"));
		JsonNode one = promotion(s, "Unico", 7, 1);
		claim(one.get("publicToken").asText(), null, device(1), null).andExpect(status().isCreated());
		mvc.perform(withToken(s.editor(), get("/api/v1/promotions"))).andExpect(jsonPath("$[?(@.name=='Unico')].state").value(org.hamcrest.Matchers.hasItem("EXHAUSTED")));
		mvc.perform(withToken(s.editor(), put("/api/v1/promotions/" + one.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Unico\",\"maxClaims\":5,\"active\":false}"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("OFF"));
	}

	@Test
	void aPromotionWithCouponsClaimedCannotBeDeletedOrGetALowerLimit() throws Exception {
		Shop s = shop("Loja", "interno");
		JsonNode p = promotion(s, "Promo", 7, 10);
		long id = p.get("id").asLong();
		claim(p.get("publicToken").asText(), null, device(1), null).andExpect(status().isCreated());
		claim(p.get("publicToken").asText(), null, device(2), null).andExpect(status().isCreated());

		mvc.perform(withToken(s.editor(), delete("/api/v1/promotions/" + id))).andExpect(status().isConflict());
		mvc.perform(withToken(s.editor(), put("/api/v1/promotions/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Promo\",\"maxClaims\":1}")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Já foram pegos 2 cupom(ns): o limite não pode ser menor que isso."));

		JsonNode empty = promotion(s, "Sem uso", 7, null);
		mvc.perform(withToken(s.editor(), delete("/api/v1/promotions/" + empty.get("id").asLong()))).andExpect(status().isNoContent());
	}

	@Test
	void thePlanDecidesIfTheShopHasCoupons() throws Exception {
		jdbc.update("INSERT INTO plans (code, name, price_cents, features) VALUES ('sem-cupons', 'Sem cupons', 0, 'CAMPAIGNS')");
		Shop s = shop("Loja", "sem-cupons");
		mvc.perform(withToken(s.editor(), get("/api/v1/promotions"))).andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value("O plano Sem cupons não inclui Cupons para clientes. Peça um plano que inclua este recurso."));
		create(s.editor(), "Promo", 7, null, null, null).andExpect(status().isPaymentRequired());
	}

	// ---------- o consumidor ----------

	@Test
	void aShopperScansTheQrCodeAndGetsACouponWithoutLoggingIn() throws Exception {
		Shop s = shop("Loja", "interno");
		JsonNode p = promotion(s, "10% na primeira compra", 7, 100);
		String token = p.get("publicToken").asText();

		mvc.perform(get("/api/v1/public/promotions/" + token)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("10% na primeira compra"))
				.andExpect(jsonPath("$.open").value(true)).andExpect(jsonPath("$.claimDays").value(7));

		claim(token, "  Maria  ", device(1), "10.0.0.1").andExpect(status().isCreated())
				.andExpect(jsonPath("$.code", matchesPattern("[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}"))).andExpect(jsonPath("$.status").value("ISSUED"))
				.andExpect(jsonPath("$.promotionName").value("10% na primeira compra")).andExpect(jsonPath("$.expiresAt").value("2026-11-12T12:00:00Z"))
				.andExpect(jsonPath("$.token", matchesPattern("[0-9a-f]{32}")));
		// o caixa vê o nome; a página pública nunca devolve o nome de ninguém
		mvc.perform(withToken(s.editor(), get("/api/v1/promotions/" + p.get("id").asLong() + "/coupons"))).andExpect(jsonPath("$[0].claimantName").value("Maria"));
		mvc.perform(withToken(s.editor(), get("/api/v1/promotions"))).andExpect(jsonPath("$[0].claimedCount").value(1));
	}

	@Test
	void theSameDeviceGetsTheSameCouponAndAnotherDeviceGetsANewOne() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Promo", 7, null).get("publicToken").asText();

		JsonNode first = claimed(token, device(1));
		JsonNode again = claimed(token, device(1));
		JsonNode other = claimed(token, device(2));

		assertEquals(first.get("code").asText(), again.get("code").asText());
		assertTrue(!first.get("code").asText().equals(other.get("code").asText()));
		assertEquals(2, jdbc.queryForObject("SELECT claimed_count FROM promotions WHERE tenant_id = ?", Integer.class, s.tenantId()));
		// sem código de aparelho (navegador que não guarda nada) cada pedido é um cupom novo, até o limite da rede
		JsonNode a = claimed(token, null);
		JsonNode b = claimed(token, null);
		assertTrue(!a.get("code").asText().equals(b.get("code").asText()));
	}

	@Test
	void theLimitOfCouponsHoldsAndAClosedPromotionRefuses() throws Exception {
		Shop s = shop("Loja", "interno");
		JsonNode p = promotion(s, "Poucos", 7, 2);
		String token = p.get("publicToken").asText();

		claim(token, null, device(1), null).andExpect(status().isCreated());
		claim(token, null, device(2), null).andExpect(status().isCreated());
		claim(token, null, device(3), null).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Os cupons desta promoção acabaram."));
		mvc.perform(get("/api/v1/public/promotions/" + token)).andExpect(jsonPath("$.open").value(false));
		// quem já tem o cupom continua tendo
		claim(token, null, device(1), null).andExpect(status().isCreated());

		mvc.perform(withToken(s.editor(), put("/api/v1/promotions/" + p.get("id").asLong()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Poucos\",\"maxClaims\":10,\"active\":false}")))
				.andExpect(status().isOk());
		claim(token, null, device(4), null).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Esta promoção não está aberta agora."));
	}

	@Test
	void theCouponLastsTheClaimDaysButNeverPastTheEndOfThePromotion() throws Exception {
		Shop s = shop("Loja", "interno");
		JsonNode longer = json.readTree(create(s.editor(), "Termina logo", 30, null, null, "2026-11-10T12:00:00Z").andReturn().getResponse().getContentAsString());
		JsonNode noDays = json.readTree(create(s.editor(), "Sem dias", null, null, null, "2026-11-20T12:00:00Z").andReturn().getResponse().getContentAsString());
		JsonNode forever = json.readTree(create(s.editor(), "Sem fim", null, null, null, null).andReturn().getResponse().getContentAsString());

		claim(longer.get("publicToken").asText(), null, device(1), null).andExpect(jsonPath("$.expiresAt").value("2026-11-10T12:00:00Z"));
		claim(noDays.get("publicToken").asText(), null, device(1), null).andExpect(jsonPath("$.expiresAt").value("2026-11-20T12:00:00Z"));
		claim(forever.get("publicToken").asText(), null, device(1), null).andExpect(jsonPath("$.expiresAt").value(nullValue()));
	}

	@Test
	void theShopperFollowsTheirOwnCouponThroughTheSecretOfTheCoupon() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Promo", 3, null).get("publicToken").asText();
		JsonNode coupon = claimed(token, device(1));
		String couponToken = coupon.get("token").asText();

		mvc.perform(get("/api/v1/public/coupons/" + couponToken)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"))
				.andExpect(jsonPath("$.code").value(coupon.get("code").asText()));

		redeem(s.editor(), coupon.get("code").asText()).andExpect(status().isOk());
		mvc.perform(get("/api/v1/public/coupons/" + couponToken)).andExpect(jsonPath("$.status").value("REDEEMED")).andExpect(jsonPath("$.redeemedAt").value(START));

		JsonNode second = claimed(token, device(2));
		clock.freeze("2026-11-09T12:00:00Z");
		mvc.perform(get("/api/v1/public/coupons/" + second.get("token").asText())).andExpect(jsonPath("$.status").value("EXPIRED"));
	}

	@Test
	void secretsThatAreNotRealGetANotFoundWithoutTouchingTheDatabase() throws Exception {
		mvc.perform(get("/api/v1/public/promotions/nao-existe")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/public/promotions/" + "0".repeat(32))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/public/coupons/" + "f".repeat(32))).andExpect(status().isNotFound());
		claim("0".repeat(32), null, device(1), null).andExpect(status().isNotFound());
	}

	// ---------- o caixa ----------

	@Test
	void theCashierChecksAndRedeemsACouponOnlyOnce() throws Exception {
		Shop s = shop("Loja", "interno");
		JsonNode p = promotion(s, "10% na primeira compra", 7, null);
		String code = claim(p.get("publicToken").asText(), "Maria", device(1), null).andReturn().getResponse().getContentAsString();
		String shown = json.readTree(code).get("code").asText();

		// o código vale digitado de qualquer jeito: minúsculas, sem hífen, com espaço
		for (String typed : new String[] { shown, shown.toLowerCase(), shown.replace("-", ""), shown.substring(0, 4) + " " + shown.substring(5) }) {
			mvc.perform(withToken(s.editor(), get("/api/v1/promotions/coupons/lookup").param("code", typed))).andExpect(status().isOk())
					.andExpect(jsonPath("$.code").value(shown)).andExpect(jsonPath("$.status").value("ISSUED")).andExpect(jsonPath("$.promotionName").value("10% na primeira compra"))
					.andExpect(jsonPath("$.claimantName").value("Maria"));
		}
		redeem(s.editor(), shown.toLowerCase()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REDEEMED")).andExpect(jsonPath("$.redeemedAt").value(START));
		redeem(s.editor(), shown).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Este cupom já foi usado em 05/11/2026 09:00."));
		mvc.perform(withToken(s.editor(), get("/api/v1/promotions"))).andExpect(jsonPath("$[0].redeemedCount").value(1));
		assertEquals("editor" + s.tenantId() + "@loja.com", jdbc.queryForObject("SELECT redeemed_by FROM promotion_coupons WHERE code = ?", String.class, shown));
	}

	@Test
	void anExpiredOrUnknownOrMalformedCodeIsRefusedWithAReason() throws Exception {
		Shop s = shop("Loja", "interno");
		String shown = json.readTree(claim(promotion(s, "Promo", 2, null).get("publicToken").asText(), null, device(1), null).andReturn().getResponse().getContentAsString()).get("code").asText();

		clock.freeze("2026-11-08T12:00:00Z");
		redeem(s.editor(), shown).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Este cupom venceu em 07/11/2026 09:00."));
		redeem(s.editor(), "ZZZZ-9999").andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Cupom não encontrado: confira o código."));
		redeem(s.editor(), "curto").andExpect(status().isBadRequest());
		mvc.perform(withToken(s.editor(), get("/api/v1/promotions/coupons/lookup").param("code", "!!!"))).andExpect(status().isBadRequest());
	}

	@Test
	void aShopNeverSeesOrRedeemsTheCouponsOfAnotherShop() throws Exception {
		Shop a = shop("Loja A", "interno");
		Shop b = shop("Loja B", "interno");
		JsonNode p = promotion(a, "Da loja A", 7, null);
		String shown = json.readTree(claim(p.get("publicToken").asText(), null, device(1), null).andReturn().getResponse().getContentAsString()).get("code").asText();

		mvc.perform(withToken(b.editor(), get("/api/v1/promotions"))).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(withToken(b.editor(), get("/api/v1/promotions/" + p.get("id").asLong() + "/coupons"))).andExpect(status().isNotFound());
		mvc.perform(withToken(b.editor(), get("/api/v1/promotions/coupons/lookup").param("code", shown))).andExpect(status().isNotFound());
		redeem(b.editor(), shown).andExpect(status().isNotFound());
		mvc.perform(withToken(b.editor(), put("/api/v1/promotions/" + p.get("id").asLong()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Roubada\"}"))).andExpect(status().isNotFound());
		redeem(a.editor(), shown).andExpect(status().isOk());
	}

	// ---------- abuso e corrida ----------

	@Test
	void aNetworkCannotFloodThePromotion() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Promo", 7, null).get("publicToken").asText();

		for (int i = 0; i < 60; i++) {
			claim(token, null, device(i), "192.168.0.9").andExpect(status().isCreated());
		}
		claim(token, null, device(61), "192.168.0.9").andExpect(status().isTooManyRequests());
		// outra rede não é afetada, e quem já tem cupom o recebe de novo mesmo com a rede no limite
		claim(token, null, device(62), "192.168.0.10").andExpect(status().isCreated());
		claim(token, null, device(1), "192.168.0.9").andExpect(status().isCreated());
	}

	@Test
	void theNameOfThePersonIsForgottenAfterAWhile() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Promo", 7, null).get("publicToken").asText();
		claim(token, "Maria", device(1), null).andExpect(status().isCreated());
		assertEquals("Maria", jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ?", String.class, s.tenantId()));

		clock.freeze("2027-02-10T12:00:00Z"); // mais de 90 dias depois
		claim(token, null, device(2), null).andExpect(status().isCreated());
		assertEquals(null, jdbc.queryForObject("SELECT claimant_name FROM promotion_coupons WHERE tenant_id = ? AND client_id = ?", String.class, s.tenantId(), device(1)));
	}

	@Test
	void twoCashiersReadingTheSameCouponAtTheSameTimeRedeemItOnce() throws Exception {
		Shop s = shop("Loja", "interno");
		String shown = json.readTree(claim(promotion(s, "Promo", 7, null).get("publicToken").asText(), null, device(1), null).andReturn().getResponse().getContentAsString()).get("code").asText();
		ExecutorService pool = Executors.newFixedThreadPool(6);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			results.add(pool.submit(() -> {
				go.await();
				return redeem(s.editor(), shown).andReturn().getResponse().getStatus();
			}));
		}
		go.countDown();
		int ok = 0;
		for (Future<Integer> result : results) {
			if (result.get() == 200) {
				ok++;
			}
		}
		pool.shutdown();
		assertEquals(1, ok);
	}

	@Test
	void theLastCouponGoesToOneShopperEvenWhenManyScanAtTheSameTime() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Último", 7, 1).get("publicToken").asText();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			int n = i;
			results.add(pool.submit(() -> {
				go.await();
				return claim(token, null, device(n), "10.1.1." + n).andReturn().getResponse().getStatus();
			}));
		}
		go.countDown();
		int created = 0;
		for (Future<Integer> result : results) {
			if (result.get() == 201) {
				created++;
			}
		}
		pool.shutdown();
		assertEquals(1, created);
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM promotion_coupons WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertEquals(1, jdbc.queryForObject("SELECT claimed_count FROM promotions WHERE tenant_id = ?", Integer.class, s.tenantId()));
	}

	@Test
	void theSameDeviceScanningTwiceAtTheSameTimeGetsOneCoupon() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Promo", 7, null).get("publicToken").asText();
		ExecutorService pool = Executors.newFixedThreadPool(5);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<String>> results = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			results.add(pool.submit(() -> {
				go.await();
				var response = claim(token, null, device(7), null).andReturn().getResponse();
				return response.getStatus() + ":" + (response.getStatus() < 300 ? json.readTree(response.getContentAsString()).get("code").asText() : response.getContentAsString());
			}));
		}
		go.countDown();
		java.util.Set<String> codes = new java.util.HashSet<>();
		for (Future<String> result : results) {
			String value = result.get();
			assertTrue(value.startsWith("201:"), value);
			codes.add(value.substring(4));
		}
		pool.shutdown();
		assertEquals(1, codes.size());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM promotion_coupons WHERE tenant_id = ?", Integer.class, s.tenantId()));
		assertEquals(1, jdbc.queryForObject("SELECT claimed_count FROM promotions WHERE tenant_id = ?", Integer.class, s.tenantId()));
	}

}
