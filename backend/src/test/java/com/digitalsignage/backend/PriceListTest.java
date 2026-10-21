package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

/** Tabelas de preços: editar, importar/exportar planilha, preços por horário e o painel de preços na TV. */
class PriceListTest extends AbstractApiTest {

	private static final String NOON = "2026-11-05T15:00:00Z"; // quinta, 12:00 em SP
	private static final String SIX_PM = "2026-11-05T21:00:00Z";

	// ---------- montagem ----------

	private static Map<String, Object> item(String id, String name, Object price) {
		Map<String, Object> item = new HashMap<>();
		if (id != null) {
			item.put("id", id);
		}
		item.put("name", name);
		item.put("price", price);
		item.put("category", "Bebidas");
		return item;
	}

	private Map<String, Object> body(String name, List<Map<String, Object>> items, List<Map<String, Object>> rules) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("items", items);
		body.put("rules", rules);
		return body;
	}

	private static Map<String, Object> happyHour() {
		Map<String, Object> rule = new HashMap<>();
		rule.put("name", "Happy hour");
		rule.put("days", List.of(1, 2, 3, 4, 5));
		rule.put("start", "17:00");
		rule.put("end", "20:00");
		rule.put("percent", -20);
		rule.put("itemIds", List.of("beer"));
		return rule;
	}

	private ResultActions submit(Map<String, Object> body) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/price-lists").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private long create(String name) throws Exception {
		return idOf(submit(body(name, List.of(item("beer", "Cerveja", 10), item("juice", "Suco", "8.5")), List.of(happyHour())))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long priceWidget(long listId) throws Exception {
		Map<String, Object> request = Map.of("kind", "PRICES", "settings", Map.of("priceListId", listId, "title", "Cardápio"));
		return idOf(mvc.perform(asAdmin(post("/api/v1/media/widget").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private record Tv(String token, long id) {
	}

	private Tv tv(String name, long playlistId) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.12.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assignPlaylist(id, name, playlistId);
		return new Tv(token, id);
	}

	private JsonNode config(Tv tv) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tv.token())).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private ResultActions importCsv(long id, byte[] bytes, String mode) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "precos.csv", "text/csv", bytes);
		return mvc.perform(asAdmin(multipart("/api/v1/price-lists/" + id + "/import").file(file).param("mode", mode)));
	}

	// ---------- CRUD ----------

	@Test
	void createsAListNormalizesItAndReadsItBack() throws Exception {
		long id = create("Cardápio do bar");

		mvc.perform(asAdmin(get("/api/v1/price-lists/" + id))).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Cardápio do bar"))
				.andExpect(jsonPath("$.currency").value("BRL"))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].id").value("beer"))
				.andExpect(jsonPath("$.items[1].price").value(8.5))
				.andExpect(jsonPath("$.items[1].available").value(true))
				.andExpect(jsonPath("$.items[1].highlight").value(false))
				.andExpect(jsonPath("$.rules[0].name").value("Happy hour"))
				.andExpect(jsonPath("$.rules[0].start").value("17:00"))
				.andExpect(jsonPath("$.widgetCount").value(0));
		mvc.perform(asAdmin(get("/api/v1/price-lists"))).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void generatesAnIdForItemsWithoutOne() throws Exception {
		long id = idOf(submit(body("Sem ids", List.of(item(null, "Café", 5), item(null, "Pão", 3)), List.of()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		JsonNode list = json.readTree(mvc.perform(asAdmin(get("/api/v1/price-lists/" + id))).andReturn().getResponse().getContentAsString());

		assertEquals(8, list.get("items").get(0).get("id").asText().length());
		assertNotEquals(list.get("items").get(0).get("id").asText(), list.get("items").get(1).get("id").asText());
	}

	@Test
	void refusesWhatMakesNoSense() throws Exception {
		submit(body(" ", List.of(), List.of())).andExpect(status().isBadRequest());
		submit(body("Preço ruim", List.of(item("a", "X", -1)), List.of())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Preço inválido em \"X\": use um valor entre 0 e 999.999,99"));
		submit(body("Preço enorme", List.of(item("a", "X", 1_000_000)), List.of())).andExpect(status().isBadRequest());
		submit(body("Repetido", List.of(item("a", "X", 1), item("a", "Y", 2)), List.of())).andExpect(status().isBadRequest());

		Map<String, Object> noEffect = new HashMap<>(happyHour());
		noEffect.put("percent", 0);
		submit(body("Sem efeito", List.of(item("beer", "Cerveja", 10)), List.of(noEffect))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("A variação \"Happy hour\" não muda nenhum preço: informe um percentual ou preços fixos"));

		Map<String, Object> badItem = new HashMap<>(happyHour());
		badItem.put("itemIds", List.of("fantasma"));
		submit(body("Item fantasma", List.of(item("beer", "Cerveja", 10)), List.of(badItem))).andExpect(status().isBadRequest());

		Map<String, Object> badTime = new HashMap<>(happyHour());
		badTime.put("start", "25:00");
		submit(body("Hora ruim", List.of(item("beer", "Cerveja", 10)), List.of(badTime))).andExpect(status().isBadRequest());

		Map<String, Object> sameTime = new HashMap<>(happyHour());
		sameTime.put("end", "17:00");
		submit(body("Mesma hora", List.of(item("beer", "Cerveja", 10)), List.of(sameTime))).andExpect(status().isBadRequest());

		Map<String, Object> badDay = new HashMap<>(happyHour());
		badDay.put("days", List.of(8));
		submit(body("Dia ruim", List.of(item("beer", "Cerveja", 10)), List.of(badDay))).andExpect(status().isBadRequest());

		Map<String, Object> badPercent = new HashMap<>(happyHour());
		badPercent.put("percent", -101);
		submit(body("Percentual", List.of(item("beer", "Cerveja", 10)), List.of(badPercent))).andExpect(status().isBadRequest());

		Map<String, Object> request = body("Moeda", List.of(), List.of());
		request.put("currency", "XYZ");
		submit(request).andExpect(status().isBadRequest());
		request.put("currency", "BRL");
		request.put("timezone", "Marte/Olimpo");
		submit(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Fuso horário desconhecido: Marte/Olimpo"));
		mvc.perform(asAdmin(get("/api/v1/price-lists"))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void namesAreUniqueIgnoringCase() throws Exception {
		create("Cardápio");

		submit(body("CARDÁPIO", List.of(), List.of())).andExpect(status().isConflict());
	}

	@Test
	void updatesAList() throws Exception {
		long id = create("Cardápio");

		mvc.perform(asAdmin(put("/api/v1/price-lists/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body("Cardápio novo", List.of(item("beer", "Cerveja artesanal", 15)), List.of())))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Cardápio novo"))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.rules.length()").value(0));
	}

	// ---------- widget e TV ----------

	@Test
	void aPriceWidgetNeedsAnExistingListAndBlocksDeletingIt() throws Exception {
		long list = create("Cardápio");
		Map<String, Object> missing = Map.of("kind", "PRICES", "settings", Map.of("priceListId", 9999));
		mvc.perform(asAdmin(post("/api/v1/media/widget").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(missing))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("A tabela de preços escolhida não existe"));
		mvc.perform(asAdmin(post("/api/v1/media/widget").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("kind", "PRICES", "settings", Map.of())))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Escolha a tabela de preços"));

		priceWidget(list);

		mvc.perform(asAdmin(get("/api/v1/price-lists/" + list))).andExpect(jsonPath("$.widgetCount").value(1));
		mvc.perform(asAdmin(delete("/api/v1/price-lists/" + list))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("A tabela \"Cardápio\" está em 1 widget(s) da biblioteca. Exclua ou troque os widgets antes."));
	}

	@Test
	void aFreeListCanBeDeleted() throws Exception {
		long list = create("Cardápio");

		mvc.perform(asAdmin(delete("/api/v1/price-lists/" + list))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/price-lists/" + list))).andExpect(status().isNotFound());
	}

	@Test
	void theTvAsksForThePricesOfTheNext24HoursInIntervals() throws Exception {
		clock.freeze(NOON);
		long list = create("Cardápio");
		long widget = priceWidget(list);
		long playlist = createPlaylist("Lista", List.of(widget));
		Tv tv = tv("TV 1", playlist);

		JsonNode body = json.readTree(mvc.perform(get("/api/v1/player/widgets/" + widget + "/data").header("X-Device-Token", tv.token()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		assertEquals("PRICES", body.get("kind").asText());
		assertTrue(body.get("error").isNull());
		JsonNode data = body.get("data");
		assertEquals("Cardápio", data.get("list").get("name").asText());
		assertEquals("BRL", data.get("list").get("currency").asText());
		JsonNode segments = data.get("segments");
		assertEquals(3, segments.size());
		// 12:00 a 17:00: preço de base
		assertEquals(10.0, segments.get(0).get("items").get(0).get("price").asDouble(), 0.001);
		assertTrue(segments.get(0).get("rule").isNull());
		// 17:00 às 20:00: happy hour (cerveja -20%), com o preço de base ao lado
		JsonNode happy = segments.get(1);
		assertEquals("Happy hour", happy.get("rule").asText());
		assertEquals(8.0, happy.get("items").get(0).get("price").asDouble(), 0.001);
		assertEquals(10.0, happy.get("items").get(0).get("basePrice").asDouble(), 0.001);
		assertEquals(8.5, happy.get("items").get(1).get("price").asDouble(), 0.001);
		assertEquals(java.time.Instant.parse("2026-11-05T20:00:00Z").toEpochMilli(), happy.get("fromMs").asLong());
		assertEquals(java.time.Instant.parse("2026-11-05T23:00:00Z").toEpochMilli(), happy.get("toMs").asLong());
		assertEquals(clock.millis(), data.get("fetchedAtMs").asLong());
	}

	@Test
	void changingAPriceTellsTheTvsThatShowTheBoard() throws Exception {
		clock.freeze(NOON);
		long list = create("Cardápio");
		long widget = priceWidget(list);
		Tv tv = tv("TV 1", createPlaylist("Lista", List.of(widget)));
		long before = config(tv).get("configVersion").asLong();

		mvc.perform(asAdmin(put("/api/v1/price-lists/" + list).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body("Cardápio", List.of(item("beer", "Cerveja", 12), item("juice", "Suco", 9)), List.of())))))
				.andExpect(status().isOk());

		assertNotEquals(before, config(tv).get("configVersion").asLong());
		JsonNode data = json.readTree(mvc.perform(get("/api/v1/player/widgets/" + widget + "/data").header("X-Device-Token", tv.token()))
				.andReturn().getResponse().getContentAsString()).get("data");
		assertEquals(12.0, data.get("segments").get(0).get("items").get(0).get("price").asDouble(), 0.001);
		assertEquals(1, data.get("segments").size());
	}

	@Test
	void whenTheListIsGoneTheTvGetsAnEmptyAnswerWithTheReason() throws Exception {
		long list = create("Cardápio");
		long widget = priceWidget(list);
		Tv tv = tv("TV 1", createPlaylist("Lista", List.of(widget)));
		jdbc.update("DELETE FROM price_lists WHERE id = ?", list);

		JsonNode body = json.readTree(mvc.perform(get("/api/v1/player/widgets/" + widget + "/data").header("X-Device-Token", tv.token()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		assertTrue(body.get("data").isNull());
		assertEquals("A tabela de preços deste widget não existe mais", body.get("error").asText());
	}

	@Test
	void thePanelPreviewsAnUnsavedListWithoutSavingIt() throws Exception {
		clock.freeze(SIX_PM);

		mvc.perform(asAdmin(post("/api/v1/price-lists/preview").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body("Rascunho", List.of(item("beer", "Cerveja", 10)), List.of(happyHour()))))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.segments[0].rule").value("Happy hour"))
				.andExpect(jsonPath("$.segments[0].items[0].price").value(8.0));
		mvc.perform(asAdmin(get("/api/v1/price-lists"))).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(asAdmin(post("/api/v1/price-lists/preview").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body("Ruim", List.of(item("a", "X", -5)), List.of()))))).andExpect(status().isBadRequest());
	}

	// ---------- planilha ----------

	@Test
	void exportsASpreadsheetThatExcelOpensInPortuguese() throws Exception {
		long id = idOf(submit(body("Cardápio do Bar", List.of(item("beer", "Cerveja", "12.5"), item("x", "=SOMA(A1)", 3)), List.of()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		String csv = mvc.perform(asAdmin(get("/api/v1/price-lists/" + id + "/export"))).andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("precos-cardapio-do-bar.csv")))
				.andExpect(content().contentTypeCompatibleWith("text/csv"))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertTrue(csv.startsWith("﻿Categoria;Nome;Descrição;Preço;Unidade;Disponível;Destaque"), csv);
		assertTrue(csv.contains("Bebidas;Cerveja;;12,50;;sim;não"), csv);
		assertTrue(csv.contains("'=SOMA(A1)"), csv); // injeção de fórmula neutralizada
	}

	@Test
	void importsASpreadsheetWithCommaDecimalsQuotesAndBom() throws Exception {
		long id = idOf(submit(body("Cardápio", List.of(item("old", "Antigo", 1)), List.of())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		String csv = "﻿Categoria;Nome;Descrição;Preço;Unidade;Disponível;Destaque\r\n"
				+ "Pratos;\"Filé; à parmegiana\";Com fritas;R$ 38,90;un;sim;sim\r\n"
				+ "Pratos;Risoto;;1.234,56;;não;\r\n"
				+ "Bebidas;Suco;;7.5;;;\r\n";

		importCsv(id, csv.getBytes(StandardCharsets.UTF_8), "replace").andExpect(status().isOk())
				.andExpect(jsonPath("$.imported").value(3))
				.andExpect(jsonPath("$.skipped.length()").value(0))
				.andExpect(jsonPath("$.list.items.length()").value(3))
				.andExpect(jsonPath("$.list.items[0].name").value("Filé; à parmegiana"))
				.andExpect(jsonPath("$.list.items[0].price").value(38.9))
				.andExpect(jsonPath("$.list.items[0].highlight").value(true))
				.andExpect(jsonPath("$.list.items[1].price").value(1234.56))
				.andExpect(jsonPath("$.list.items[1].available").value(false))
				.andExpect(jsonPath("$.list.items[2].price").value(7.5))
				.andExpect(jsonPath("$.list.items[2].available").value(true));
	}

	@Test
	void importsAWindows1252FileFromExcelAndACommaSeparatedOne() throws Exception {
		long id = create("Cardápio");

		importCsv(id, "Nome;Preço\r\nCafé coado;4,50\r\nPão de açúcar;2,00\r\n".getBytes(Charset.forName("windows-1252")), "replace")
				.andExpect(jsonPath("$.list.items[0].name").value("Café coado"))
				.andExpect(jsonPath("$.list.items[1].name").value("Pão de açúcar"));
		importCsv(id, "name,price\nCoffee,3.25\n".getBytes(StandardCharsets.UTF_8), "replace")
				.andExpect(jsonPath("$.list.items[0].name").value("Coffee"))
				.andExpect(jsonPath("$.list.items[0].price").value(3.25));
	}

	@Test
	void appendsInsteadOfReplacingAndKeepsTheRulesOfTheItemsThatStay() throws Exception {
		long id = create("Cardápio");

		importCsv(id, "Nome;Preço\r\nBatata;15,00\r\n".getBytes(StandardCharsets.UTF_8), "append").andExpect(status().isOk())
				.andExpect(jsonPath("$.list.items.length()").value(3))
				.andExpect(jsonPath("$.list.rules.length()").value(1));

		// trocar tudo: a variação citava a cerveja, que saiu: ela some junto
		importCsv(id, "Nome;Preço\r\nBatata;15,00\r\n".getBytes(StandardCharsets.UTF_8), "replace").andExpect(status().isOk())
				.andExpect(jsonPath("$.list.items.length()").value(1))
				.andExpect(jsonPath("$.list.rules.length()").value(0));
	}

	@Test
	void skipsBadRowsAndSaysWhich() throws Exception {
		long id = create("Cardápio");
		String csv = "Nome;Preço\r\nBom;5,00\r\n;3,00\r\nSem preço;abc\r\nGrande;99999999\r\n\r\nOutro;2\r\n";

		importCsv(id, csv.getBytes(StandardCharsets.UTF_8), "replace").andExpect(status().isOk())
				.andExpect(jsonPath("$.imported").value(2))
				.andExpect(jsonPath("$.skipped.length()").value(3))
				.andExpect(jsonPath("$.skipped[0]").value("Linha 3: sem nome"))
				.andExpect(jsonPath("$.skipped[1]").value("Linha 4 (Sem preço): preço ilegível"))
				.andExpect(jsonPath("$.skipped[2]").value("Linha 5 (Grande): preço fora do limite"));
	}

	@Test
	void refusesASpreadsheetWithoutTheNeededColumnsOrEmpty() throws Exception {
		long id = create("Cardápio");

		importCsv(id, "Produto;Quantidade\r\nX;3\r\n".getBytes(StandardCharsets.UTF_8), "replace").andExpect(status().isBadRequest());
		importCsv(id, new byte[0], "replace").andExpect(status().isBadRequest());
		importCsv(id, "Nome;Preço\r\nA;1\r\n".getBytes(StandardCharsets.UTF_8), "mesclar").andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/price-lists/" + id))).andExpect(jsonPath("$.items.length()").value(2)); // nada mudou
	}

	// ---------- quem pode ----------

	@Test
	void viewersReadButOnlyEditorsChange() throws Exception {
		long id = create("Cardápio");
		createUserRow(DEFAULT_TENANT, "viewer-precos@teste.local", "VIEWER", "senha-do-viewer");
		String viewer = loginAs("viewer-precos@teste.local", "senha-do-viewer");

		mvc.perform(withToken(viewer, get("/api/v1/price-lists"))).andExpect(status().isOk());
		mvc.perform(withToken(viewer, post("/api/v1/price-lists").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body("X", List.of(), List.of())))))
				.andExpect(status().isForbidden());
		mvc.perform(withToken(viewer, delete("/api/v1/price-lists/" + id))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/price-lists")).andExpect(status().isUnauthorized());
	}

	@Test
	void anotherClientNeverSeesMyPrices() throws Exception {
		long id = create("Cardápio");
		long other = createTenantRow("Outro cliente", "interno", "ACTIVE");
		createUserRow(other, "outro@teste.local", "ADMIN", "senha-do-outro");
		String token = loginAs("outro@teste.local", "senha-do-outro");

		mvc.perform(withToken(token, get("/api/v1/price-lists"))).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(withToken(token, get("/api/v1/price-lists/" + id))).andExpect(status().isNotFound());
		mvc.perform(withToken(token, delete("/api/v1/price-lists/" + id))).andExpect(status().isNotFound());
	}

}
