package com.digitalsignage.backend;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Permissões por plano: quais recursos o plano inclui e o tamanho máximo de cada arquivo. */
class PlanFeaturesTest extends AbstractApiTest {

	private static final List<String> ALL = List.of("CAMPAIGNS", "LAYOUTS", "WIDGETS", "PRICES", "QUEUE", "PROOF_OF_PLAY", "COUPONS");

	private long createPlan(String code, List<String> features, Integer maxUploadMb) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("name", "Plano " + code);
		body.put("priceCents", 0);
		body.put("features", features);
		body.put("maxUploadMb", maxUploadMb);
		return idOf(mvc.perform(asAdmin(post("/api/v1/platform/plans").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private String clientAdmin(String planCode) throws Exception {
		long tenant = createTenantRow("Cliente " + planCode, planCode, "ACTIVE");
		String email = "admin-" + tenant + "@teste.com";
		createUserRow(tenant, email, "ADMIN", "senha-forte-1");
		return loginAs(email, "senha-forte-1");
	}

	private ResultActions saveFeatures(long planId, String code, List<String> features) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("name", "Plano " + code);
		body.put("priceCents", 0);
		body.put("features", features);
		return mvc.perform(asAdmin(put("/api/v1/platform/plans/" + planId).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	@Test
	void aNewPlanWithoutAFeatureListIncludesEverythingAndTheSeedPlansKeepEverything() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/platform/plans").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"completo\",\"name\":\"Completo\",\"priceCents\":100}")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.features", containsInAnyOrder(ALL.toArray())));
		for (String seed : List.of("interno", "gratis", "basico", "profissional")) {
			mvc.perform(asAdmin(get("/api/v1/platform/plans"))).andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.code=='" + seed + "')].features[0]").value(hasItem("CAMPAIGNS")));
		}
	}

	@Test
	void aFeatureTheAdminDoesNotHaveIsRefusedWithAnExplanation() throws Exception {
		createPlan("so-campanhas", List.of("CAMPAIGNS"), null);
		String client = clientAdmin("so-campanhas");

		// recursos que o plano não tem: 402 e o nome do recurso na mensagem
		mvc.perform(withToken(client, get("/api/v1/queues"))).andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value("O plano Plano so-campanhas não inclui Fila de atendimento. Peça um plano que inclua este recurso."));
		mvc.perform(withToken(client, get("/api/v1/attendance/overview"))).andExpect(status().isPaymentRequired());
		mvc.perform(withToken(client, get("/api/v1/reports/proof"))).andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value("O plano Plano so-campanhas não inclui Comprovação de exibição. Peça um plano que inclua este recurso."));
		mvc.perform(withToken(client, post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}")))
				.andExpect(status().isPaymentRequired());
		mvc.perform(withToken(client, post("/api/v1/price-lists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}")))
				.andExpect(status().isPaymentRequired());
		mvc.perform(withToken(client, get("/api/v1/widgets/catalog"))).andExpect(status().isPaymentRequired());

		// consultar layouts, preços e campanhas continua liberado (outras telas dependem dessas listas)
		mvc.perform(withToken(client, get("/api/v1/layouts"))).andExpect(status().isOk());
		mvc.perform(withToken(client, get("/api/v1/price-lists"))).andExpect(status().isOk());
		// o que o plano tem funciona normalmente
		mvc.perform(withToken(client, get("/api/v1/campaigns"))).andExpect(status().isOk());
		mvc.perform(withToken(client, get("/api/v1/devices"))).andExpect(status().isOk());
	}

	@Test
	void editingThePlanTakesEffectOnTheNextRequest() throws Exception {
		long plan = createPlan("muda-depois", List.of(), null);
		String client = clientAdmin("muda-depois");
		mvc.perform(withToken(client, get("/api/v1/queues"))).andExpect(status().isPaymentRequired());

		saveFeatures(plan, "muda-depois", List.of("QUEUE")).andExpect(status().isOk()).andExpect(jsonPath("$.features[0]").value("QUEUE"));
		mvc.perform(withToken(client, get("/api/v1/queues"))).andExpect(status().isOk());

		// sem enviar a lista, a edição não mexe nos recursos
		mvc.perform(asAdmin(put("/api/v1/platform/plans/" + plan).contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"muda-depois\",\"name\":\"Novo nome\",\"priceCents\":5}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.features[0]").value("QUEUE"));
		mvc.perform(withToken(client, get("/api/v1/queues"))).andExpect(status().isOk());
	}

	@Test
	void thePlatformOperatorIsNeverBlockedEvenWhenActingInAClientWithoutTheFeature() throws Exception {
		createPlan("sem-nada", List.of(), null);
		long tenant = createTenantRow("Sem nada", "sem-nada", "ACTIVE");
		mvc.perform(asAdmin(get("/api/v1/queues")).header("X-Tenant-Id", tenant)).andExpect(status().isOk());
	}

	@Test
	void losingAFeatureDoesNotDeleteWhatAlreadyExists() throws Exception {
		// rebaixar de plano não apaga nada: o que existe continua consultável, só o painel deixa de oferecer o recurso
		long plan = createPlan("rebaixa", ALL, null);
		String client = clientAdmin("rebaixa");
		String layout = "{\"name\":\"Meu layout\",\"zones\":[{\"name\":\"A\",\"x\":0,\"y\":0,\"width\":100,\"height\":100,\"main\":true}]}";
		mvc.perform(withToken(client, post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON).content(layout))).andExpect(status().isCreated());

		saveFeatures(plan, "rebaixa", List.of()).andExpect(status().isOk());
		mvc.perform(withToken(client, post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON).content(layout))).andExpect(status().isPaymentRequired());
		mvc.perform(withToken(client, get("/api/v1/layouts"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Meu layout"));
		mvc.perform(withToken(client, get("/api/v1/devices"))).andExpect(status().isOk());
	}

	@Test
	void theMaximumFileSizeFollowsThePlan() throws Exception {
		createPlan("arquivo-pequeno", ALL, 1);
		String client = clientAdmin("arquivo-pequeno");

		MockMultipartFile small = new MockMultipartFile("file", "ok.png", "image/png", new byte[200 * 1024]);
		mvc.perform(withToken(client, multipart("/api/v1/media").file(small))).andExpect(status().isCreated());

		MockMultipartFile big = new MockMultipartFile("file", "grande.png", "image/png", new byte[2 * 1024 * 1024]);
		mvc.perform(withToken(client, multipart("/api/v1/media").file(big))).andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value("O plano Plano arquivo-pequeno aceita arquivos de até 1 MB e este é maior. Comprima o arquivo ou peça um plano maior."));
		assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM media WHERE name = 'ok.png'", Long.class));
	}

	@Test
	void theAccountShowsTheFeaturesAndTheUploadLimit() throws Exception {
		createPlan("conta", List.of("QUEUE", "PRICES"), 20);
		String client = clientAdmin("conta");
		mvc.perform(withToken(client, get("/api/v1/auth/me"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.tenant.plan.features", containsInAnyOrder("QUEUE", "PRICES")))
				.andExpect(jsonPath("$.tenant.plan.maxUploadMb").value(20));
		// a lista de planos que o cliente compara traz o mesmo
		mvc.perform(withToken(client, get("/api/v1/billing"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.plans[?(@.code=='conta')].features[0]").value(hasItem("PRICES")));
	}

	@Test
	void validatesTheFeatureListAndTheUploadLimit() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/platform/plans").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"invalido\",\"name\":\"X\",\"priceCents\":1,\"features\":[\"VOAR\"]}"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(post("/api/v1/platform/plans").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"zero\",\"name\":\"X\",\"priceCents\":1,\"maxUploadMb\":0}"))).andExpect(status().isBadRequest());
	}

}
