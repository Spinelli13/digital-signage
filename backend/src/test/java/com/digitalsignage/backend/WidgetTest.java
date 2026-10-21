package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.digitalsignage.backend.monitoring.WebhookUrlPolicy;
import com.digitalsignage.backend.widget.WidgetKind;
import com.digitalsignage.backend.widget.WidgetSettings;
import com.fasterxml.jackson.databind.JsonNode;

/** Widgets: conteúdo dinâmico como mídia (criar, editar, ir para a TV) e os dados de clima, notícias e cotações com cache. */
class WidgetTest extends AbstractApiTest {

	private static final String NOON = "2026-11-05T15:00:00Z";

	private static final String WEATHER = """
			{"timezone":"America/Sao_Paulo","current":{"temperature_2m":22.5,"relative_humidity_2m":70,"weather_code":2,"is_day":1,"wind_speed_10m":12.3},
			"daily":{"time":["2026-11-05","2026-11-06","2026-11-07","2026-11-08"],"weather_code":[2,61,80,3],
			"temperature_2m_max":[27.1,25,24,26],"temperature_2m_min":[17,18,18.5,19],"precipitation_probability_max":[10,60,70,20]}}""";

	private static final String RATES = """
			{"base":"BRL","rates":{"2026-11-03":{"USD":0.2,"EUR":0.18},"2026-11-04":{"USD":0.19,"EUR":0.18}}}""";

	private static final String PLACES = """
			{"results":[{"name":"Campinas","admin1":"São Paulo","country":"Brasil","latitude":-22.90556,"longitude":-47.06083,"timezone":"America/Sao_Paulo"},
			{"name":"Campinorte","admin1":"Goiás","country":"Brasil","latitude":-14.31139,"longitude":-49.15,"timezone":"America/Sao_Paulo"}]}""";

	private static final String RSS = """
			<?xml version="1.0" encoding="UTF-8"?>
			<rss version="2.0"><channel><title>Feed</title>
			<item><title>Primeira manchete</title><pubDate>Thu, 05 Nov 2026 14:30:00 GMT</pubDate></item>
			<item><title><![CDATA[Segunda <b>manchete</b>   com   espaços]]></title><pubDate>Thu, 05 Nov 2026 13:00:00 GMT</pubDate></item>
			<item><title>Terceira manchete</title></item>
			</channel></rss>""";

	private static final String ATOM = """
			<?xml version="1.0" encoding="UTF-8"?>
			<feed xmlns="http://www.w3.org/2005/Atom"><title>Atom</title>
			<entry><title>Manchete Atom</title><updated>2026-11-05T14:00:00Z</updated></entry></feed>""";

	// ---------- montagem ----------

	private Map<String, Object> clockSettings(Map<String, Object> extra) {
		Map<String, Object> settings = new HashMap<>(Map.of("timezone", "America/Sao_Paulo"));
		settings.putAll(extra);
		return settings;
	}

	private ResultActions createWidget(String kind, Map<String, Object> settings, Integer seconds) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("kind", kind);
		body.put("settings", settings);
		if (seconds != null) {
			body.put("durationSeconds", seconds);
		}
		return mvc.perform(asAdmin(post("/api/v1/media/widget").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
	}

	private long widget(String kind, Map<String, Object> settings) throws Exception {
		return idOf(createWidget(kind, settings, null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private Map<String, Object> weatherSettings() {
		return Map.of("city", "Campinas", "latitude", -22.9056, "longitude", -47.0608);
	}

	private record Tv(String token, long id) {
	}

	private Tv tv(String name, long playlistId) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.11.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assignPlaylist(id, name, playlistId);
		return new Tv(token, id);
	}

	private JsonNode playerData(Tv tv, long mediaId) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/player/widgets/" + mediaId + "/data").header("X-Device-Token", tv.token()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private Tv tvWith(long mediaId) throws Exception {
		return tv("TV 1", createPlaylist("Lista", List.of(mediaId)));
	}

	// ---------- criar e editar ----------

	@Test
	void createsEveryKindWithCleanSettingsAndSensibleDefaults() throws Exception {
		createWidget("CLOCK", clockSettings(Map.of("showSeconds", true, "ignorado", "x")), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("WIDGET"))
				.andExpect(jsonPath("$.name").value("Relógio e data"))
				.andExpect(jsonPath("$.durationSeconds").value(10))
				.andExpect(jsonPath("$.widget.kind").value("CLOCK"))
				.andExpect(jsonPath("$.widget.settings.showSeconds").value(true))
				.andExpect(jsonPath("$.widget.settings.showDate").value(true))
				.andExpect(jsonPath("$.widget.settings.theme").value("dark"))
				.andExpect(jsonPath("$.widget.settings.ignorado").doesNotExist())
				.andExpect(jsonPath("$.url").doesNotExist())
				.andExpect(jsonPath("$.fileSize").value(0));

		createWidget("WEATHER", weatherSettings(), 20).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Previsão do tempo - Campinas"))
				.andExpect(jsonPath("$.durationSeconds").value(20))
				.andExpect(jsonPath("$.widget.settings.days").value(3));
		createWidget("NEWS", Map.of("source", "agenciabrasil", "maxItems", 5), null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.widget.settings.source").value("agenciabrasil"))
				.andExpect(jsonPath("$.widget.settings.maxItems").value(5));
		createWidget("RATES", Map.of("currencies", List.of("usd", "EUR", "usd")), null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.widget.settings.currencies[0]").value("USD"))
				.andExpect(jsonPath("$.widget.settings.currencies.length()").value(2));
		createWidget("NOTICE", Map.of("title", "Fechado", "message", "Voltamos amanhã às 9h.\nObrigado!", "accent", "red"), null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.widget.settings.accent").value("red"));
		createWidget("COUNTDOWN", Map.of("label", "Natal", "target", "2026-12-25"), null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.widget.settings.target").value("2026-12-25T00:00"))
				.andExpect(jsonPath("$.widget.settings.finishedText").value("Chegou o dia!"));

		mvc.perform(asAdmin(get("/api/v1/media"))).andExpect(jsonPath("$.length()").value(6));
	}

	@Test
	void refusesWhatMakesNoSense() throws Exception {
		createWidget("WEATHER", Map.of("city", "Campinas"), null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Escolha a cidade da previsão do tempo"));
		createWidget("WEATHER", Map.of("city", "X", "latitude", 200, "longitude", 0), null).andExpect(status().isBadRequest());
		createWidget("NOTICE", Map.of("title", " ", "message", ""), null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Escreva o título ou a mensagem do aviso"));
		createWidget("COUNTDOWN", Map.of("label", "Natal", "target", "amanhã"), null).andExpect(status().isBadRequest());
		createWidget("COUNTDOWN", Map.of("target", "2026-12-25"), null).andExpect(status().isBadRequest());
		createWidget("CLOCK", Map.of("timezone", "Marte/Olimpo"), null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Fuso horário desconhecido: Marte/Olimpo"));
		createWidget("RATES", Map.of("currencies", List.of("USD", "EUR", "GBP", "JPY", "CAD")), null).andExpect(status().isBadRequest());
		createWidget("RATES", Map.of("currencies", List.of("ZZZ")), null).andExpect(status().isBadRequest());
		createWidget("NEWS", Map.of("source", "jornal-que-nao-existe"), null).andExpect(status().isBadRequest());
		createWidget("NEWS", Map.of("source", "custom"), null).andExpect(status().isBadRequest());
		createWidget("FOGUETE", Map.of(), null).andExpect(status().isBadRequest());
		createWidget("CLOCK", Map.of("theme", "neon"), null).andExpect(status().isBadRequest());
		createWidget("NOTICE", Map.of("title", "x".repeat(81)), null).andExpect(status().isBadRequest());
		createWidget("CLOCK", Map.of(), 2).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/media"))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aCustomFeedMustBeHttpsAndPublic() {
		WebhookUrlPolicy strict = new WebhookUrlPolicy(false);

		for (String url : new String[] { "http://exemplo.com/feed", "https://localhost/feed", "https://127.0.0.1/feed", "https://10.0.0.5/feed",
				"https://usuario:senha@exemplo.com/feed", "ftp://exemplo.com/feed", "" }) {
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> WidgetSettings.sanitize(WidgetKind.NEWS, Map.of("source", "custom", "customUrl", url), strict), url);
			assertTrue(error.getMessage().contains("feed de notícias"), error.getMessage());
		}
	}

	@Test
	void anEditChangesTheWidgetKeepsItsKindAndTellsTheTvs() throws Exception {
		long id = widget("NOTICE", Map.of("title", "Promoção", "message", "Leve 3 pague 2"));
		long playlist = createPlaylist("Lista", List.of(id));
		Tv tv = tv("TV 1", playlist);
		JsonNode before = json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tv.token())).andReturn().getResponse().getContentAsString());

		mvc.perform(asAdmin(put("/api/v1/media/" + id + "/widget").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
				Map.of("name", "Aviso da loja", "kind", "NOTICE", "settings", Map.of("title", "Promoção", "message", "Leve 4 pague 3"), "durationSeconds", 15)))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Aviso da loja"))
				.andExpect(jsonPath("$.durationSeconds").value(15))
				.andExpect(jsonPath("$.widget.settings.message").value("Leve 4 pague 3"));

		JsonNode after = json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tv.token())).andReturn().getResponse().getContentAsString());
		assertNotEquals(before.get("configVersion").asLong(), after.get("configVersion").asLong());
		assertEquals("Leve 4 pague 3", after.get("items").get(0).get("widget").get("settings").get("message").asText());

		// o tipo não troca
		mvc.perform(asAdmin(put("/api/v1/media/" + id + "/widget").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("kind", "CLOCK", "settings", Map.of())))))
				.andExpect(status().isBadRequest());
		// e uma imagem não é widget
		long png = uploadPng("foto");
		mvc.perform(asAdmin(put("/api/v1/media/" + png + "/widget").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("kind", "CLOCK", "settings", Map.of())))))
				.andExpect(status().isBadRequest());
	}

	// ---------- na TV ----------

	@Test
	void theTvReceivesTheWidgetAsAnItemWithItsSettingsAndNoFile() throws Exception {
		long id = widget("COUNTDOWN", Map.of("label", "Inauguração", "target", "2026-12-01T09:00"));
		Tv tv = tvWith(id);

		JsonNode item = json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tv.token())).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString()).get("items").get(0);

		assertEquals("WIDGET", item.get("type").asText());
		assertEquals("/api/v1/player/widgets/" + id + "/data", item.get("url").asText());
		assertEquals(0, item.get("fileSize").asLong());
		assertTrue(item.get("checksum").isNull());
		assertEquals("COUNTDOWN", item.get("widget").get("kind").asText());
		assertEquals("Inauguração", item.get("widget").get("settings").get("label").asText());
		assertEquals(10, item.get("durationSeconds").asInt());
	}

	@Test
	void aWidgetHasNoFileToDownloadOrPreview() throws Exception {
		long id = widget("CLOCK", Map.of());

		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/file"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Esta mídia é um widget: não há arquivo para baixar"));
		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/url"))).andExpect(status().isBadRequest());
	}

	@Test
	void aWidgetCanBeDeletedWhenNoPlaylistUsesIt() throws Exception {
		long free = widget("CLOCK", Map.of());
		long used = widget("NOTICE", Map.of("title", "x"));
		createPlaylist("Lista", List.of(used));

		mvc.perform(asAdmin(delete("/api/v1/media/" + free))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(delete("/api/v1/media/" + used))).andExpect(status().is4xxClientError());
	}

	// ---------- dados ----------

	@Test
	void weatherComesFromTheProviderAndIsKeptFor30MinutesForEveryTvInTheSameCity() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("api.open-meteo.com", WEATHER);
		long id = widget("WEATHER", weatherSettings());
		Tv tv = tvWith(id);

		JsonNode first = playerData(tv, id);

		assertEquals("WEATHER", first.get("kind").asText());
		assertTrue(first.get("error").isNull());
		JsonNode data = first.get("data");
		assertEquals(22.5, data.get("current").get("temperature").asDouble(), 0.001);
		assertEquals(2, data.get("current").get("code").asInt());
		assertTrue(data.get("current").get("isDay").asBoolean());
		assertEquals(4, data.get("daily").size());
		assertEquals(60, data.get("daily").get(1).get("rainChance").asInt());
		assertEquals(18.0, data.get("daily").get(1).get("min").asDouble(), 0.001);
		assertEquals(clock.millis(), data.get("fetchedAtMs").asLong());

		playerData(tv, id);
		clock.freeze("2026-11-05T15:29:00Z");
		playerData(tv, id);
		assertEquals(1, widgetHttp.callsTo("api.open-meteo.com"));
		assertTrue(widgetHttp.calls().get(0).contains("latitude=-22.91&longitude=-47.06"), widgetHttp.calls().get(0));

		clock.freeze("2026-11-05T15:31:00Z");
		playerData(tv, id);
		assertEquals(2, widgetHttp.callsTo("api.open-meteo.com"));
	}

	@Test
	void whenTheProviderFailsTheLastAnswerIsKeptForADayThenThereIsNothingAndTheReasonSaysWhy() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("api.open-meteo.com", WEATHER);
		long id = widget("WEATHER", weatherSettings());
		Tv tv = tvWith(id);
		JsonNode fresh = playerData(tv, id);

		widgetHttp.failEverything(true);
		clock.freeze("2026-11-05T20:00:00Z"); // 5 h depois: o guardado vale
		JsonNode stale = playerData(tv, id);
		assertEquals(22.5, stale.get("data").get("current").get("temperature").asDouble(), 0.001);
		assertEquals(fresh.get("data").get("fetchedAtMs").asLong(), stale.get("data").get("fetchedAtMs").asLong()); // a TV vê que é velho

		clock.freeze("2026-11-06T16:00:00Z"); // mais de 24 h
		JsonNode gone = playerData(tv, id);
		assertTrue(gone.get("data").isNull());
		assertEquals("Não foi possível buscar o clima agora", gone.get("error").asText());
	}

	@Test
	void aProviderWithGarbageIsAFailureNotACrash() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("api.open-meteo.com", "<html>manutenção</html>");
		long id = widget("WEATHER", weatherSettings());

		JsonNode body = playerData(tvWith(id), id);

		assertTrue(body.get("data").isNull());
		assertFalse(body.get("error").isNull());
	}

	@Test
	void ratesAreRealsPerUnitWithTheChangeSinceTheDayBefore() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("api.frankfurter.dev", RATES);
		long id = widget("RATES", Map.of("currencies", List.of("USD", "EUR")));

		JsonNode data = playerData(tvWith(id), id).get("data");

		assertEquals("BRL", data.get("base").asText());
		assertEquals("2026-11-04", data.get("date").asText());
		assertEquals("USD", data.get("rates").get(0).get("code").asText());
		assertEquals(5.2632, data.get("rates").get(0).get("value").asDouble(), 0.0001);
		assertEquals(5.26, data.get("rates").get(0).get("changePercent").asDouble(), 0.001);
		assertEquals(5.5556, data.get("rates").get(1).get("value").asDouble(), 0.0001);
		assertEquals(0.0, data.get("rates").get(1).get("changePercent").asDouble(), 0.001);
		assertTrue(widgetHttp.calls().get(0).contains("symbols=USD,EUR"), widgetHttp.calls().get(0));
	}

	@Test
	void newsAreHeadlinesWithTheSourceNamedAndTheLimitRespected() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("agenciabrasil.ebc.com.br", 200, RSS.getBytes(StandardCharsets.UTF_8), "application/rss+xml; charset=utf-8");
		long id = widget("NEWS", Map.of("source", "agenciabrasil", "maxItems", 3));

		JsonNode data = playerData(tvWith(id), id).get("data");

		assertEquals("Agência Brasil", data.get("source").asText());
		assertEquals(3, data.get("items").size());
		assertEquals("Primeira manchete", data.get("items").get(0).get("title").asText());
		assertEquals("Segunda manchete com espaços", data.get("items").get(1).get("title").asText()); // sem HTML, sem espaços repetidos
		assertEquals(java.time.Instant.parse("2026-11-05T14:30:00Z").toEpochMilli(), data.get("items").get(0).get("publishedAtMs").asLong());
		assertTrue(data.get("items").get(2).get("publishedAtMs").isNull());
	}

	@Test
	void eachWidgetShowsOnlyAsManyHeadlinesAsItAskedEvenWithOneSharedCache() throws Exception {
		clock.freeze(NOON);
		StringBuilder feed = new StringBuilder("<?xml version=\"1.0\"?><rss><channel>");
		for (int i = 1; i <= 8; i++) {
			feed.append("<item><title>Manchete ").append(i).append("</title></item>");
		}
		feed.append("</channel></rss>");
		widgetHttp.on("agenciabrasil.ebc.com.br", 200, feed.toString().getBytes(StandardCharsets.UTF_8), "application/rss+xml");
		long three = widget("NEWS", Map.of("source", "agenciabrasil", "maxItems", 3));
		long five = widget("NEWS", Map.of("source", "agenciabrasil", "maxItems", 5));
		Tv tv = tvWith(three);

		assertEquals(3, playerData(tv, three).get("data").get("items").size());
		assertEquals(5, playerData(tv, five).get("data").get("items").size());
		assertEquals(1, widgetHttp.callsTo("agenciabrasil")); // as duas mostram o mesmo feed: uma consulta só
	}

	@Test
	void understandsAtomAndFeedsWithoutADeclaredEncoding() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("bbci.co.uk", 200, ATOM.getBytes(StandardCharsets.UTF_8), "application/atom+xml");
		long atom = widget("NEWS", Map.of("source", "bbc"));
		Tv tv = tvWith(atom);
		assertEquals("Manchete Atom", playerData(tv, atom).get("data").get("items").get(0).get("title").asText());

		String latin = "<rss><channel><item><title>Notícias de hoje: ação rápida</title></item></channel></rss>";
		widgetHttp.on("rss.uol.com.br", 200, latin.getBytes(StandardCharsets.ISO_8859_1), "text/xml;charset=ISO-8859-1");
		long uol = widget("NEWS", Map.of("source", "uol"));
		assertEquals("Notícias de hoje: ação rápida", playerData(tv, uol).get("data").get("items").get(0).get("title").asText());
	}

	@Test
	void aFeedWithAnExternalEntityIsRefusedNotFetched() throws Exception {
		clock.freeze(NOON);
		String xxe = """
				<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY segredo SYSTEM "file:///etc/passwd">]>
				<rss><channel><item><title>&segredo;</title></item></channel></rss>""";
		widgetHttp.on("g1.globo.com", 200, xxe.getBytes(StandardCharsets.UTF_8), "application/xml");
		long id = widget("NEWS", Map.of("source", "g1"));

		JsonNode body = playerData(tvWith(id), id);

		assertTrue(body.get("data").isNull());
		assertTrue(body.get("error").asText().startsWith("Não foi possível buscar as notícias"), body.toString());
	}

	@Test
	void widgetsWithoutExternalDataNeverCallTheInternet() throws Exception {
		long clockId = widget("CLOCK", Map.of());
		long noticeId = widget("NOTICE", Map.of("title", "Olá"));
		Tv tv = tvWith(clockId);

		assertEquals(0, playerData(tv, clockId).get("data").size());
		assertEquals(0, playerData(tv, noticeId).get("data").size());
		assertTrue(widgetHttp.calls().isEmpty());
	}

	// ---------- painel ----------

	@Test
	void thePanelSearchesCitiesAndPreviewsWithoutSaving() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("geocoding-api.open-meteo.com", PLACES);
		widgetHttp.on("api.open-meteo.com/v1/forecast", WEATHER);

		mvc.perform(asAdmin(get("/api/v1/widgets/places?q=Campin"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Campinas"))
				.andExpect(jsonPath("$[0].region").value("São Paulo"))
				.andExpect(jsonPath("$[0].latitude").value(-22.90556))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(asAdmin(get("/api/v1/widgets/places?q=C"))).andExpect(jsonPath("$.length()").value(0)); // curto demais
		mvc.perform(asAdmin(get("/api/v1/widgets/places?q=Campin"))).andExpect(status().isOk());
		assertEquals(1, widgetHttp.callsTo("geocoding-api")); // a busca repetida vem do cache

		mvc.perform(asAdmin(post("/api/v1/widgets/preview").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("kind", "WEATHER", "settings", weatherSettings())))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.settings.days").value(3))
				.andExpect(jsonPath("$.data.current.temperature").value(22.5))
				.andExpect(jsonPath("$.error").doesNotExist());
		mvc.perform(asAdmin(get("/api/v1/media"))).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(asAdmin(post("/api/v1/widgets/preview").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("kind", "WEATHER", "settings", Map.of())))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theCatalogListsKindsNewsSourcesAndCurrencies() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/widgets/catalog"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.kinds.length()").value(8))
				.andExpect(jsonPath("$.kinds[1].kind").value("WEATHER"))
				.andExpect(jsonPath("$.kinds[1].needsData").value(true))
				.andExpect(jsonPath("$.newsSources[0].id").value("g1"))
				.andExpect(jsonPath("$.currencies[0]").value("USD"));
	}

	// ---------- quem pode ----------

	@Test
	void onlyEditorsMakeWidgetsAndTvsCannotUseThePanelApi() throws Exception {
		createPlaceholderViewer();
		String viewer = loginAs("viewer@teste.local", "senha-do-viewer");

		mvc.perform(withToken(viewer, post("/api/v1/media/widget").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("kind", "CLOCK", "settings", Map.of()))))).andExpect(status().isForbidden());
		mvc.perform(withToken(viewer, post("/api/v1/widgets/preview").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("kind", "CLOCK", "settings", Map.of()))))).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/media/widget").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

		long id = widget("CLOCK", Map.of());
		Tv tv = tvWith(id);
		mvc.perform(get("/api/v1/widgets/catalog").header("X-Device-Token", tv.token())).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/player/widgets/" + id + "/data")).andExpect(status().isUnauthorized());
	}

	@Test
	void aTvOfAnotherClientNeverSeesMyWidget() throws Exception {
		long id = widget("NOTICE", Map.of("title", "Segredo da loja"));
		long other = createTenantRow("Outro cliente", "interno", "ACTIVE");
		createUserRow(other, "outro@teste.local", "ADMIN", "senha-do-outro");
		String token = loginAs("outro@teste.local", "senha-do-outro");

		mvc.perform(withToken(token, get("/api/v1/media"))).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(withToken(token, put("/api/v1/media/" + id + "/widget").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("kind", "NOTICE", "settings", Map.of("title", "hackeado"))))))
				.andExpect(status().isNotFound());
	}

	private void createPlaceholderViewer() {
		createUserRow(DEFAULT_TENANT, "viewer@teste.local", "VIEWER", "senha-do-viewer");
	}

}
