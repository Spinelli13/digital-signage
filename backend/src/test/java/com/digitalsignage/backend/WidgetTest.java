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

	/**
	 * A previsão do MET Norway para Campinas (UTC-3), com "agora" às 15:00 UTC de 5/11: hora em hora só no começo e depois de 6
	 * em 6 horas, como o MET entrega. Dia 6: 5,0 + 3,2 mm em 2 dos 4 blocos de 6 h; dia 7: pancadas; dia 8: céu limpo.
	 */
	private static final String WEATHER = metSeries(
			metEntry("2026-11-05T15:00:00Z", 22.5, 70, 3.4, "partlycloudy_day", 0.0, true),
			metEntry("2026-11-05T21:00:00Z", 27.1, 60, 2.0, "cloudy", 0.0, false),
			metEntry("2026-11-06T00:00:00Z", 17.0, 80, 1.0, "cloudy", 0.0, false),
			metEntry("2026-11-06T06:00:00Z", 18.0, 90, 1.0, "rain", 5.0, false),
			metEntry("2026-11-06T12:00:00Z", 24.0, 75, 2.0, "rain", 3.2, false),
			metEntry("2026-11-06T18:00:00Z", 25.0, 65, 2.0, "cloudy", 0.0, false),
			metEntry("2026-11-07T00:00:00Z", 21.0, 70, 2.0, "cloudy", 0.0, false),
			metEntry("2026-11-07T12:00:00Z", 24.0, 80, 2.0, "rainshowers_day", 10.0, false),
			metEntry("2026-11-07T18:00:00Z", 26.0, 70, 2.0, "cloudy", 0.0, false),
			metEntry("2026-11-08T12:00:00Z", 26.0, 50, 2.0, "clearsky_day", 0.0, false));

	private static String metEntry(String time, double temperature, int humidity, double windMs, String symbol, double rain6h, boolean hourly) {
		String oneHour = hourly ? ",\"next_1_hours\":{\"summary\":{\"symbol_code\":\"" + symbol + "\"},\"details\":{\"precipitation_amount\":0.0}}" : "";
		return "{\"time\":\"" + time + "\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":" + temperature
				+ ",\"relative_humidity\":" + humidity + ",\"wind_speed\":" + windMs + ",\"cloud_area_fraction\":50.0}},"
				+ "\"next_12_hours\":{\"summary\":{\"symbol_code\":\"" + symbol + "\"},\"details\":{}},"
				+ "\"next_6_hours\":{\"summary\":{\"symbol_code\":\"" + symbol + "\"},\"details\":{\"precipitation_amount\":" + rain6h + "}}"
				+ oneHour + "}}";
	}

	private static String metSeries(String... entries) {
		return "{\"type\":\"Feature\",\"properties\":{\"meta\":{},\"timeseries\":[" + String.join(",", entries) + "]}}";
	}

	private static final String RATES = """
			{"base":"BRL","rates":{"2026-11-03":{"USD":0.2,"EUR":0.18},"2026-11-04":{"USD":0.19,"EUR":0.18}}}""";

	private static final String PLACES = """
			[{"name":"Campinas","lat":"-22.9056391","lon":"-47.0608201","address":{"city":"Campinas","state":"São Paulo","country":"Brasil","country_code":"br","ISO3166-2-lvl4":"BR-SP"}},
			{"name":"Campinorte","lat":"-14.3113","lon":"-49.15","address":{"state":"Goiás","country":"Brasil","country_code":"br","ISO3166-2-lvl4":"BR-GO"}},
			{"name":"Sem posição","address":{"state":"Goiás"}}]""";

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
	void thePersonCanPickTheColorsOfAnyWidgetAndTheServerKeepsThemCleanOrRefusesThem() throws Exception {
		createWidget("CLOCK", clockSettings(Map.of("theme", "green", "bgColor", "#0B0710", "textColor", " #FFFFFF ", "accentColor", "")), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.widget.settings.theme").value("green"))
				.andExpect(jsonPath("$.widget.settings.bgColor").value("#0b0710"))
				.andExpect(jsonPath("$.widget.settings.textColor").value("#ffffff"))
				.andExpect(jsonPath("$.widget.settings.accentColor").doesNotExist());
		createWidget("NOTICE", Map.of("title", "Fechado", "accent", "brand", "accentColor", "#D946EF"), null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.widget.settings.accentColor").value("#d946ef"));
		for (String invalid : List.of("roxo", "#fff", "#12345g", "rgb(1,2,3)", "#8B5CF6; background: url(x)")) {
			createWidget("CLOCK", clockSettings(Map.of("bgColor", invalid)), null).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("Cor inválida: use o formato #RRGGBB (por exemplo, #8B5CF6)"));
		}
		createWidget("CLOCK", clockSettings(Map.of("accentColor", "azul")), null).andExpect(status().isBadRequest());
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
		widgetHttp.on("api.met.no", WEATHER);
		long id = widget("WEATHER", weatherSettings());
		Tv tv = tvWith(id);

		JsonNode first = playerData(tv, id);

		assertEquals("WEATHER", first.get("kind").asText());
		assertTrue(first.get("error").isNull());
		JsonNode data = first.get("data");
		assertEquals(22.5, data.get("current").get("temperature").asDouble(), 0.001);
		assertEquals(2, data.get("current").get("code").asInt());
		assertTrue(data.get("current").get("isDay").asBoolean());
		assertEquals(12.2, data.get("current").get("windKmh").asDouble(), 0.001); // 3,4 m/s
		assertEquals(70, data.get("current").get("humidity").asInt());
		assertEquals("MET Norway", data.get("credit").asText()); // a licença (CC BY 4.0) pede a citação da fonte
		assertEquals("America/Sao_Paulo", data.get("timezone").asText());
		assertEquals(4, data.get("daily").size());
		assertEquals("2026-11-06", data.get("daily").get(1).get("date").asText());
		assertEquals(63, data.get("daily").get(1).get("code").asInt());
		assertEquals(50, data.get("daily").get(1).get("rainChance").asInt()); // metade do dia com chuva prevista
		assertEquals(8.2, data.get("daily").get(1).get("rainMm").asDouble(), 0.001);
		assertEquals(18.0, data.get("daily").get(1).get("min").asDouble(), 0.001);
		assertEquals(25.0, data.get("daily").get(1).get("max").asDouble(), 0.001);
		assertEquals(81, data.get("daily").get(2).get("code").asInt()); // pancadas
		assertEquals(0, data.get("daily").get(3).get("code").asInt()); // céu limpo
		assertEquals(clock.millis(), data.get("fetchedAtMs").asLong());

		playerData(tv, id);
		clock.freeze("2026-11-05T15:29:00Z");
		playerData(tv, id);
		assertEquals(1, widgetHttp.callsTo("api.met.no"));
		assertTrue(widgetHttp.calls().get(0).contains("lat=-22.91&lon=-47.06"), widgetHttp.calls().get(0));

		clock.freeze("2026-11-05T15:31:00Z");
		playerData(tv, id);
		assertEquals(2, widgetHttp.callsTo("api.met.no"));
	}

	@Test
	void whenTheProviderFailsTheLastAnswerIsKeptForADayThenThereIsNothingAndTheReasonSaysWhy() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("api.met.no", WEATHER);
		long id = widget("WEATHER", weatherSettings());
		Tv tv = tvWith(id);
		JsonNode fresh = playerData(tv, id);

		widgetHttp.failEverything(true);
		clock.freeze("2026-11-05T20:00:00Z"); // 5 h depois: o guardado vale
		JsonNode stale = playerData(tv, id);
		assertEquals(27.1, stale.get("data").get("current").get("temperature").asDouble(), 0.001); // o "agora" anda com o relógio: a hora mais próxima da previsão guardada
		assertEquals(fresh.get("data").get("fetchedAtMs").asLong(), stale.get("data").get("fetchedAtMs").asLong()); // a TV vê que é velho

		clock.freeze("2026-11-06T16:00:00Z"); // mais de 24 h
		JsonNode gone = playerData(tv, id);
		assertTrue(gone.get("data").isNull());
		assertEquals("Não foi possível buscar o clima agora", gone.get("error").asText());
	}

	@Test
	void weatherAsksTheProviderIfItChangedAndNeverAsksBeforeItsExpiresHeader() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("api.met.no", WEATHER, "Thu, 05 Nov 2026 14:50:00 GMT", "Thu, 05 Nov 2026 15:45:00 GMT");
		long id = widget("WEATHER", weatherSettings());
		Tv tv = tvWith(id);

		playerData(tv, id);
		clock.freeze("2026-11-05T15:31:00Z"); // passou dos 30 min nossos, mas o MET só renova às 15:45
		playerData(tv, id);
		assertEquals(1, widgetHttp.callsTo("api.met.no"));

		clock.freeze("2026-11-05T15:46:00Z");
		JsonNode again = playerData(tv, id);
		assertEquals(2, widgetHttp.callsTo("api.met.no"));
		assertEquals(null, widgetHttp.conditions().get(0)); // a primeira vez não tem o que perguntar
		assertEquals("Thu, 05 Nov 2026 14:50:00 GMT", widgetHttp.conditions().get(1)); // a segunda pergunta se mudou desde então
		assertEquals(22.5, again.get("data").get("current").get("temperature").asDouble(), 0.001); // 304: segue valendo o guardado
		assertEquals(clock.millis(), again.get("data").get("fetchedAtMs").asLong());
	}

	@Test
	void afterAFailureTheProviderIsLeftAloneForAWhileInsteadOfBeingHammered() throws Exception {
		clock.freeze(NOON);
		widgetHttp.failEverything(true);
		long id = widget("WEATHER", weatherSettings());
		Tv tv = tvWith(id);

		assertTrue(playerData(tv, id).get("data").isNull());
		clock.freeze("2026-11-05T15:03:00Z");
		assertTrue(playerData(tv, id).get("data").isNull());
		assertEquals(1, widgetHttp.callsTo("api.met.no"));

		widgetHttp.failEverything(false);
		widgetHttp.on("api.met.no", WEATHER);
		clock.freeze("2026-11-05T15:06:00Z"); // 5 min depois tenta de novo
		assertEquals(22.5, playerData(tv, id).get("data").get("current").get("temperature").asDouble(), 0.001);
	}

	@Test
	void theTimezoneOfTheChosenCityDecidesWhereTheForecastDaysStartAndABadOneIsRefused() throws Exception {
		clock.freeze("2026-11-06T01:30:00Z"); // 22:30 do dia 5 em Brasília, já 6/11 em Auckland
		widgetHttp.on("api.met.no", WEATHER);
		Map<String, Object> settings = new HashMap<>(weatherSettings());
		settings.put("timezone", "Pacific/Auckland");
		long id = widget("WEATHER", settings);

		JsonNode data = playerData(tvWith(id), id).get("data");

		assertEquals("Pacific/Auckland", data.get("timezone").asText());
		assertEquals("2026-11-06", data.get("daily").get(0).get("date").asText());

		settings.put("timezone", "Marte/Olympus");
		createWidget("WEATHER", settings, null).andExpect(status().isBadRequest());
	}

	@Test
	void aProviderWithGarbageIsAFailureNotACrash() throws Exception {
		clock.freeze(NOON);
		widgetHttp.on("api.met.no", "<html>manutenção</html>");
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
	void theSenateAgencyFeedWithItsStylesheetDeclarationIsRead() throws Exception {
		clock.freeze(NOON);
		String feed = """
				<?xml version="1.0" encoding="utf-8" ?>
				<?xml-stylesheet type="text/xsl" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" href="/noticias/rss.xsl" ?>
				<rss version="2.0"><channel><title>Senado Federal</title>
				<item><title>Arquivistas do Senado ajudam a guardar a história</title><pubDate>Wed, 21 Oct 2026 14:30:00 -0300</pubDate></item>
				</channel></rss>""";
		widgetHttp.on("www12.senado.leg.br", 200, feed.getBytes(StandardCharsets.UTF_8), "text/xml;charset=utf-8");
		long id = widget("NEWS", Map.of("source", "agenciasenado", "maxItems", 3));

		JsonNode data = playerData(tvWith(id), id).get("data");

		assertEquals("Agência Senado", data.get("source").asText()); // a citação da fonte que a política de uso do Senado pede
		assertEquals("Arquivistas do Senado ajudam a guardar a história", data.get("items").get(0).get("title").asText());
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
		widgetHttp.on("nominatim.openstreetmap.org", PLACES);
		widgetHttp.on("api.met.no", WEATHER);

		mvc.perform(asAdmin(get("/api/v1/widgets/places?q=Campin"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Campinas"))
				.andExpect(jsonPath("$[0].region").value("São Paulo"))
				.andExpect(jsonPath("$[0].latitude").value(-22.9056391))
				.andExpect(jsonPath("$[0].timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.length()").value(2)); // o resultado sem posição é descartado
		mvc.perform(asAdmin(get("/api/v1/widgets/places?q=C"))).andExpect(jsonPath("$.length()").value(0)); // curto demais
		mvc.perform(asAdmin(get("/api/v1/widgets/places?q=Campin"))).andExpect(status().isOk());
		assertEquals(1, widgetHttp.callsTo("nominatim")); // a busca repetida vem do cache

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
