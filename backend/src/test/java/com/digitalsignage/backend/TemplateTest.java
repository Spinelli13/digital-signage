package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Modelos prontos por segmento: um clique cria os avisos, a tabela de preços, as playlists e o layout do segmento (só o que o plano do
 * cliente inclui), com os textos que a pessoa conferiu, e tudo continua editável como se fosse feito à mão.
 */
class TemplateTest extends AbstractApiTest {

	private record Shop(long tenantId, String admin, String editor, String viewer) {
	}

	private Shop shop(String name, String plan) throws Exception {
		long tenant = createTenantRow(name, plan, "ACTIVE");
		createUserRow(tenant, "admin" + tenant + "@loja.com", "ADMIN", "senha-forte-1");
		createUserRow(tenant, "editor" + tenant + "@loja.com", "EDITOR", "senha-forte-1");
		createUserRow(tenant, "viewer" + tenant + "@loja.com", "VIEWER", "senha-forte-1");
		return new Shop(tenant, loginAs("admin" + tenant + "@loja.com", "senha-forte-1"), loginAs("editor" + tenant + "@loja.com", "senha-forte-1"),
				loginAs("viewer" + tenant + "@loja.com", "senha-forte-1"));
	}

	private ResultActions apply(String token, String template, Map<String, Object> body) throws Exception {
		return mvc.perform(withToken(token, post("/api/v1/templates/" + template + "/apply").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private JsonNode applied(String token, String template, Map<String, Object> body) throws Exception {
		return json.readTree(apply(token, template, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private static Map<String, Object> saoPaulo() {
		return Map.of("name", "São Paulo", "latitude", -23.55, "longitude", -46.63, "timezone", "America/Sao_Paulo");
	}

	private JsonNode fetch(String token, String path) throws Exception {
		return json.readTree(mvc.perform(withToken(token, get(path))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private long tv(Shop shop, String name) throws Exception {
		String deviceToken = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", deviceToken, "playerVersion", "0.9.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		return idOf(mvc.perform(withToken(shop.admin(), post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private static List<String> names(JsonNode array) {
		List<String> names = new ArrayList<>();
		array.forEach(node -> names.add(node.get("name").asText()));
		return names;
	}

	@Test
	void listsTheFiveSegmentsWithTheirTextsAndWhatThePlanAllows() throws Exception {
		Shop full = shop("Completa", "interno");
		Shop start = shop("Start", "start");

		JsonNode catalog = fetch(full.viewer(), "/api/v1/templates");

		assertEquals(List.of("barbearia", "restaurante", "clinica", "academia", "loja"), catalog.get("templates").findValuesAsText("id"));
		JsonNode barber = catalog.get("templates").get(0);
		assertEquals("Barbearia", barber.get("name").asText());
		assertEquals("name", barber.get("fields").get(0).get("key").asText());
		assertEquals("Barbearia", barber.get("fields").get(0).get("defaultValue").asText());
		assertTrue(barber.get("hasPrices").asBoolean());
		assertTrue(catalog.get("available").get("layouts").asBoolean());
		assertTrue(catalog.get("available").get("prices").asBoolean());
		JsonNode limited = fetch(start.viewer(), "/api/v1/templates").get("available");
		assertTrue(limited.get("widgets").asBoolean());
		assertFalse(limited.get("layouts").asBoolean());
		assertFalse(limited.get("prices").asBoolean());
	}

	@Test
	void theBarbershopCreatesWidgetsPricesPlaylistsAndALayoutWithTheTypedTexts() throws Exception {
		Shop s = shop("Loja", "interno");
		Map<String, Object> body = new HashMap<>();
		body.put("fields", Map.of("name", "Barbearia do Zé", "promo", "Quarta: barba grátis no corte", "hours", "Todo dia 10h às 19h"));
		body.put("city", saoPaulo());

		JsonNode result = applied(s.editor(), "barbearia", body);

		assertEquals("barbearia", result.get("templateId").asText());
		// 2 avisos + tabela + relógio + clima + 2 avisos laterais
		assertEquals(7, result.get("widgets").size());
		assertEquals(4, result.get("playlists").size());
		assertEquals("Barbearia do Zé - Layout", result.get("layout").get("name").asText());
		assertEquals("Barbearia do Zé - Nossos serviços", result.get("priceList").get("name").asText());
		assertEquals(0, result.get("notes").size());

		// os textos que a pessoa digitou estão nos avisos, e o que ela deixou em branco usou o padrão
		JsonNode media = fetch(s.viewer(), "/api/v1/media");
		String all = media.toString();
		assertTrue(all.contains("Barbearia do Zé"));
		assertTrue(all.contains("Quarta: barba grátis no corte"));
		assertTrue(all.contains("Todo dia 10h às 19h"));
		assertTrue(all.contains("Agende pelo WhatsApp")); // o campo "contact" ficou com o padrão

		// a tabela de preços tem os serviços do segmento
		JsonNode list = fetch(s.viewer(), "/api/v1/price-lists/" + result.get("priceList").get("id").asLong());
		assertEquals(6, list.get("items").size());
		assertEquals("Corte de cabelo", list.get("items").get(0).get("name").asText());

		// o layout: principal à esquerda e 3 zonas na faixa; versão em pé também
		JsonNode layout = fetch(s.viewer(), "/api/v1/layouts/" + result.get("layout").get("id").asLong());
		assertEquals(4, layout.get("zones").size());
		assertEquals(4, layout.get("portraitZones").size());
		assertEquals(List.of("Principal", "Relógio", "Clima", "Faixa lateral"), names(layout.get("zones")));
		assertTrue(layout.get("zones").get(0).get("main").asBoolean());
		// as zonas da faixa já têm a playlist delas
		assertNotEquals(0L, layout.get("zones").get(1).get("playlistId").asLong());

		// a playlist principal: boas-vindas, promoção e tabela, nessa ordem, cada uma pelo tempo do widget
		JsonNode main = fetch(s.viewer(), "/api/v1/playlists/" + result.get("playlists").get(0).get("id").asLong());
		assertEquals(3, main.get("items").size());
		assertEquals("Barbearia do Zé - Boas-vindas", main.get("items").get(0).get("mediaName").asText());
		assertEquals("Barbearia do Zé - Promoção", main.get("items").get(1).get("mediaName").asText());
		assertEquals("Barbearia do Zé - Nossos serviços", main.get("items").get(2).get("mediaName").asText());
		assertEquals(20, main.get("items").get(2).get("durationSeconds").asInt());
	}

	@Test
	void withoutACityThereIsNoWeatherAndTheLayoutHasThreeZones() throws Exception {
		Shop s = shop("Loja", "interno");

		JsonNode result = applied(s.editor(), "barbearia", Map.of());

		assertEquals(6, result.get("widgets").size());
		assertEquals(3, result.get("playlists").size());
		assertEquals(1, result.get("notes").size());
		assertTrue(result.get("notes").get(0).asText().contains("previsão do tempo"));
		JsonNode layout = fetch(s.viewer(), "/api/v1/layouts/" + result.get("layout").get("id").asLong());
		assertEquals(List.of("Principal", "Relógio", "Faixa lateral"), names(layout.get("zones")));
		assertFalse(fetch(s.viewer(), "/api/v1/media").toString().contains("Clima"));
	}

	@Test
	void aPlanWithOnlyWidgetsGetsOnePlaylistAndSaysWhatWasLeftOut() throws Exception {
		Shop s = shop("Start", "start");

		JsonNode result = applied(s.editor(), "barbearia", Map.of("city", saoPaulo()));

		assertTrue(result.get("layout").isNull());
		assertTrue(result.get("priceList").isNull());
		assertEquals(1, result.get("playlists").size());
		// 2 avisos + relógio + clima + 2 laterais, todos numa playlist só
		assertEquals(6, fetch(s.viewer(), "/api/v1/playlists/" + result.get("playlists").get(0).get("id").asLong()).get("items").size());
		String notes = result.get("notes").toString();
		assertTrue(notes.contains("tabela de preços"));
		assertTrue(notes.contains("layout"));
		assertEquals(0, fetch(s.viewer(), "/api/v1/layouts").size());
	}

	@Test
	void aPlanWithoutWidgetsIsRefusedWithTheUsualExplanation() throws Exception {
		jdbc.update("INSERT INTO plans (code, name, price_cents, max_devices, max_storage_mb, max_users, sort_order, features, max_upload_mb) VALUES ('sem-widgets', 'Sem widgets', 0, 2, 100, 2, 99, 'CAMPAIGNS', 50)");
		Shop s = shop("Sem", "sem-widgets");

		apply(s.editor(), "barbearia", Map.of()).andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("não inclui")));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM playlists", Integer.class));
	}

	@Test
	void usingTheSameTemplateTwiceNeverCollidesOnUniqueNames() throws Exception {
		Shop s = shop("Loja", "interno");

		JsonNode first = applied(s.editor(), "restaurante", Map.of("fields", Map.of("name", "Cantina")));
		JsonNode second = applied(s.editor(), "restaurante", Map.of("fields", Map.of("name", "Cantina")));

		assertEquals("Cantina - Layout", first.get("layout").get("name").asText());
		assertEquals("Cantina - Layout 2", second.get("layout").get("name").asText());
		assertEquals("Cantina - Cardápio", first.get("priceList").get("name").asText());
		assertEquals("Cantina - Cardápio 2", second.get("priceList").get("name").asText());
	}

	@Test
	void theTextsAreTrimmedAndLimited() throws Exception {
		Shop s = shop("Loja", "interno");

		apply(s.editor(), "loja", Map.of("fields", Map.of("name", "x".repeat(61)))).andExpect(status().isBadRequest());
		apply(s.editor(), "loja", Map.of("fields", Map.of("promo", "y".repeat(121)))).andExpect(status().isBadRequest());
		JsonNode ok = applied(s.editor(), "loja", Map.of("fields", Map.of("name", "   ", "promo", "  Tudo pela metade  ", "campoQueNaoExiste", "ignorado")));

		assertEquals("Loja - Layout", ok.get("layout").get("name").asText()); // nome em branco usa o padrão
		assertTrue(fetch(s.viewer(), "/api/v1/media").toString().contains("Tudo pela metade"));
		assertFalse(fetch(s.viewer(), "/api/v1/media").toString().contains("ignorado"));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM playlists WHERE name LIKE 'xxx%'", Integer.class));
	}

	@Test
	void everySegmentAppliesCleanlyOnAFullPlan() throws Exception {
		Shop s = shop("Loja", "interno");

		for (String id : List.of("barbearia", "restaurante", "clinica", "academia", "loja")) {
			JsonNode result = applied(s.editor(), id, Map.of("city", saoPaulo()));
			assertNotEquals(0, result.get("widgets").size(), id);
			assertEquals(4, result.get("playlists").size(), id);
			assertEquals(!id.equals("clinica") && !id.equals("loja"), !result.get("priceList").isNull(), id);
		}
	}

	@Test
	void canPutTheResultOnTheChosenTvsAndTheTvReceivesTheLayout() throws Exception {
		Shop s = shop("Loja", "interno");
		long tv = tv(s, "TV Salão");

		JsonNode result = applied(s.editor(), "barbearia", Map.of("deviceIds", List.of(tv)));

		assertEquals(1, result.get("devices").asInt());
		JsonNode device = fetch(s.viewer(), "/api/v1/devices/" + tv);
		assertEquals(result.get("playlists").get(0).get("id").asLong(), device.get("currentPlaylistId").asLong());
		assertEquals(result.get("layout").get("id").asLong(), device.get("layoutId").asLong());
	}

	@Test
	void onlyEditorsApplyEveryoneSeesTheCatalogAndAnUnknownModelIs404() throws Exception {
		Shop s = shop("Loja", "interno");

		apply(s.viewer(), "barbearia", Map.of()).andExpect(status().isForbidden());
		apply(s.editor(), "padaria", Map.of()).andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/templates/barbearia/apply").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		fetch(s.viewer(), "/api/v1/templates");
	}

	@Test
	void whatIsCreatedBelongsOnlyToTheClientThatAsked() throws Exception {
		Shop a = shop("A", "interno");
		Shop b = shop("B", "interno");

		applied(a.editor(), "barbearia", Map.of());

		assertEquals(0, fetch(b.admin(), "/api/v1/playlists").size());
		assertEquals(0, fetch(b.admin(), "/api/v1/layouts").size());
		assertEquals(0, fetch(b.admin(), "/api/v1/price-lists").size());
		assertTrue(fetch(a.admin(), "/api/v1/playlists").size() >= 3);
	}
}
