package com.digitalsignage.backend;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** Os planos da oferta comercial (V27): limites, recursos e preços de cada um, sem mexer nos planos antigos. */
class StandardPlansTest extends AbstractApiTest {

	private static final List<String> ALL = List.of("CAMPAIGNS", "LAYOUTS", "WIDGETS", "PRICES", "QUEUE", "PROOF_OF_PLAY", "COUPONS");
	private static final List<String> PRO = List.of("WIDGETS", "PRICES", "QUEUE", "COUPONS");

	private record Expected(String code, int cents, Integer devices, int storageMb, List<String> features) {
	}

	private static final List<Expected> OFFER = List.of(
			new Expected("start", 3900, 2, 3072, List.of("WIDGETS")),
			new Expected("pro", 8900, 5, 10240, PRO),
			new Expected("business", 17900, 15, 30720, ALL),
			new Expected("rede", 34900, 40, 81920, ALL),
			new Expected("comodato-basico", 7900, 1, 3072, PRO),
			new Expected("comodato-completo", 12900, 1, 3072, PRO),
			new Expected("comodato-premium", 18900, 1, 3072, PRO));

	private JsonNode plans() throws Exception {
		return json.readTree(mvc.perform(asAdmin(get("/api/v1/platform/plans"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode planOf(String code) throws Exception {
		for (JsonNode plan : plans()) {
			if (code.equals(plan.get("code").asText())) {
				return plan;
			}
		}
		throw new AssertionError("plano não encontrado: " + code);
	}

	@Test
	void everyPlanOfTheOfferExistsWithItsLimitsFeaturesAndPrice() throws Exception {
		for (Expected expected : OFFER) {
			JsonNode plan = planOf(expected.code());
			assertEquals(expected.cents(), plan.get("priceCents").asInt(), expected.code() + ": preço");
			assertEquals(expected.devices(), plan.get("maxDevices").asInt(), expected.code() + ": TVs");
			assertEquals(expected.storageMb(), plan.get("maxStorageMb").asInt(), expected.code() + ": espaço");
			assertEquals(50, plan.get("maxUploadMb").asInt(), expected.code() + ": arquivo (o bucket recusa mais de 50 MB)");
			assertTrue(plan.get("active").asBoolean(), expected.code() + ": ativo");
			List<String> features = new java.util.ArrayList<>();
			plan.get("features").forEach(feature -> features.add(feature.asText()));
			org.hamcrest.MatcherAssert.assertThat(expected.code() + ": recursos", features, containsInAnyOrder(expected.features().toArray()));
		}
	}

	@Test
	void theOldPlansStayAsTheyWereAndEveryPlanNowHasTheUploadLimit() throws Exception {
		// Grátis, Básico e Profissional: mesmos preços, TVs e recursos de antes
		assertEquals(0, planOf("gratis").get("priceCents").asInt());
		assertEquals(4900, planOf("basico").get("priceCents").asInt());
		assertEquals(14900, planOf("profissional").get("priceCents").asInt());
		assertEquals(25, planOf("profissional").get("maxDevices").asInt());
		assertEquals(7, planOf("basico").get("features").size());
		assertTrue(planOf("interno").get("maxDevices").isNull(), "o plano interno segue sem limite de TVs");
		for (JsonNode plan : plans()) {
			assertEquals(50, plan.get("maxUploadMb").asInt(), plan.get("code").asText() + ": todos os planos têm o limite de arquivo do bucket");
		}
	}

	@Test
	void aClientOnTheComodatoPlanGetsOneTvAndTheStartPlanHasNoQueue() throws Exception {
		long comodato = createTenantRow("Barbearia em comodato", "comodato-basico", "ACTIVE");
		createUserRow(comodato, "dono-comodato@teste.com", "ADMIN", "senha-forte-1");
		String owner = loginAs("dono-comodato@teste.com", "senha-forte-1");

		mvc.perform(withToken(owner, get("/api/v1/auth/me"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.tenant.plan.code").value("comodato-basico"))
				.andExpect(jsonPath("$.tenant.plan.maxDevices").value(1))
				.andExpect(jsonPath("$.tenant.plan.features", containsInAnyOrder(PRO.toArray())));
		// a fila de senhas está no Pro e nos de comodato, não no Start
		mvc.perform(withToken(owner, get("/api/v1/queues"))).andExpect(status().isOk());
		long start = createTenantRow("Cliente Start", "start", "ACTIVE");
		createUserRow(start, "dono-start@teste.com", "ADMIN", "senha-forte-1");
		mvc.perform(withToken(loginAs("dono-start@teste.com", "senha-forte-1"), get("/api/v1/queues"))).andExpect(status().isPaymentRequired());
	}
}
