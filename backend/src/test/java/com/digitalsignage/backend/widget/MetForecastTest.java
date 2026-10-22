package com.digitalsignage.backend.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A tradução da previsão do MET Norway para o que o widget do Player entende, e o fuso de cada cidade. */
class MetForecastTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static JsonNode series(String... entries) throws IOException {
		return JSON.readTree("{\"properties\":{\"timeseries\":[" + String.join(",", entries) + "]}}");
	}

	private static String entry(String time, double temperature, String symbol1h, String symbol12h, double cloud) {
		String oneHour = symbol1h == null ? "" : ",\"next_1_hours\":{\"summary\":{\"symbol_code\":\"" + symbol1h
				+ "\"},\"details\":{\"precipitation_amount\":0.0}}";
		String twelveHours = symbol12h == null ? "" : ",\"next_12_hours\":{\"summary\":{\"symbol_code\":\"" + symbol12h + "\"},\"details\":{}}";
		return "{\"time\":\"" + time + "\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":" + temperature
				+ ",\"relative_humidity\":55,\"wind_speed\":5.0,\"cloud_area_fraction\":" + cloud + "}}" + oneHour + twelveHours + "}}";
	}

	@Test
	void everyMetSymbolBecomesAWmoCodeTheWidgetKnows() {
		Map<String, Integer> expected = Map.ofEntries(
				Map.entry("clearsky_day", 0), Map.entry("clearsky_night", 0), Map.entry("fair_day", 1), Map.entry("partlycloudy_night", 2),
				Map.entry("cloudy", 3), Map.entry("fog", 45),
				Map.entry("lightrain", 61), Map.entry("rain", 63), Map.entry("heavyrain", 65),
				Map.entry("lightrainshowers_day", 80), Map.entry("rainshowers_day", 81), Map.entry("heavyrainshowers_night", 82),
				Map.entry("lightsleet", 67), Map.entry("heavysleet", 67), Map.entry("sleetshowers_day", 80),
				Map.entry("lightsnow", 71), Map.entry("snow", 73), Map.entry("heavysnow", 75), Map.entry("snowshowers_day", 85),
				Map.entry("rainandthunder", 95), Map.entry("heavyrainshowersandthunder_day", 95), Map.entry("lightssleetshowersandthunder_day", 95),
				Map.entry("simbolo_que_nao_existe", 3));
		expected.forEach((symbol, code) -> assertEquals(code, MetForecast.wmoCode(symbol), symbol));
	}

	@Test
	void theNightSymbolMakesTheCurrentConditionNight() throws Exception {
		JsonNode night = series(entry("2026-11-05T03:00:00Z", 16.0, "clearsky_night", "clearsky_night", 5));

		Map<String, Object> data = MetForecast.parse(night, SAO_PAULO, Instant.parse("2026-11-05T03:10:00Z"));

		@SuppressWarnings("unchecked")
		Map<String, Object> current = (Map<String, Object>) data.get("current");
		assertEquals(0, current.get("code"));
		assertFalse((Boolean) current.get("isDay"));
		assertEquals(18.0, current.get("windKmh")); // 5 m/s
	}

	@Test
	void withoutASymbolTheCloudsDecideTheSky() throws Exception {
		for (Object[] row : new Object[][] { { 5.0, 0 }, { 30.0, 1 }, { 60.0, 2 }, { 95.0, 3 } }) {
			JsonNode data = series(entry("2026-11-05T15:00:00Z", 20.0, null, null, (Double) row[0]));

			@SuppressWarnings("unchecked")
			Map<String, Object> current = (Map<String, Object>) MetForecast.parse(data, SAO_PAULO, Instant.parse("2026-11-05T15:00:00Z")).get("current");

			assertEquals(row[1], current.get("code"), "nuvens " + row[0]);
		}
	}

	@Test
	void theCurrentConditionIsTheEntryNearestToNowAndTheDaysAreTheLocalDaysOfTheCity() throws Exception {
		JsonNode data = series(
				entry("2026-11-05T01:00:00Z", 10.0, "cloudy", "cloudy", 90),
				entry("2026-11-05T15:00:00Z", 22.0, "fair_day", "fair_day", 20),
				entry("2026-11-06T02:00:00Z", 15.0, "cloudy", "cloudy", 90),
				entry("2026-11-06T04:00:00Z", 14.0, "cloudy", "cloudy", 90));

		Map<String, Object> saoPaulo = MetForecast.parse(data, SAO_PAULO, Instant.parse("2026-11-05T14:40:00Z"));
		Map<String, Object> tokyo = MetForecast.parse(data, ZoneId.of("Asia/Tokyo"), Instant.parse("2026-11-05T14:40:00Z"));

		assertEquals(22.0, ((Map<?, ?>) saoPaulo.get("current")).get("temperature"));
		// 02:00Z de 6/11 ainda é 5/11 às 23:00 em São Paulo, e já é 6/11 em Tóquio
		List<?> saoPauloDays = (List<?>) saoPaulo.get("daily");
		assertEquals("2026-11-05", ((Map<?, ?>) saoPauloDays.get(0)).get("date"));
		assertEquals(15.0, ((Map<?, ?>) saoPauloDays.get(0)).get("min")); // 02:00Z vira 23:00 do dia 5 (a de 01:00Z, antes do "agora", só conta no dia 4)
		assertEquals("Asia/Tokyo", tokyo.get("timezone"));
	}

	@Test
	void aResponseWithoutForecastOrTemperatureIsAnError() throws Exception {
		assertThrows(IOException.class, () -> MetForecast.parse(JSON.readTree("{\"properties\":{\"timeseries\":[]}}"), SAO_PAULO, Instant.now()));
		assertThrows(IOException.class, () -> MetForecast.parse(JSON.readTree("{\"html\":true}"), SAO_PAULO, Instant.now()));
		assertThrows(IOException.class, () -> MetForecast.parse(
				JSON.readTree("{\"properties\":{\"timeseries\":[{\"time\":\"2026-11-05T15:00:00Z\",\"data\":{\"instant\":{\"details\":{}}}}]}}"),
				SAO_PAULO, Instant.parse("2026-11-05T15:00:00Z")));
		assertThrows(IOException.class, () -> MetForecast.parse(
				JSON.readTree("{\"properties\":{\"timeseries\":[{\"time\":\"ontem\",\"data\":{}}]}}"), SAO_PAULO, Instant.now()));
	}

	@Test
	void theCityZoneComesFromTheStateInBrazilAndFromTheLongitudeElsewhere() {
		assertEquals("America/Sao_Paulo", WeatherZones.forPlace("br", "BR-SP", -22.9, -47.06));
		assertEquals("America/Sao_Paulo", WeatherZones.forPlace("br", "BR-SC", -27.6, -48.5));
		assertEquals("America/Cuiaba", WeatherZones.forPlace("br", "BR-MT", -15.6, -56.1));
		assertEquals("America/Campo_Grande", WeatherZones.forPlace("br", "BR-MS", -20.4, -54.6));
		assertEquals("America/Manaus", WeatherZones.forPlace("br", "BR-AM", -3.1, -60.0));
		assertEquals("America/Eirunepe", WeatherZones.forPlace("br", "BR-AM", -6.6, -69.9));
		assertEquals("America/Rio_Branco", WeatherZones.forPlace("br", "BR-AC", -9.97, -67.8));
		assertEquals("America/Porto_Velho", WeatherZones.forPlace("br", "BR-RO", -8.76, -63.9));
		assertEquals("America/Boa_Vista", WeatherZones.forPlace("br", "BR-RR", 2.8, -60.7));
		assertEquals("Etc/GMT+4", WeatherZones.forPlace("ve", null, 10.5, -66.9)); // 66,9° O -> UTC-4 (sem horário de verão)
		assertEquals("Etc/GMT-1", WeatherZones.forPlace("pt", null, 38.7, 15.0)); // 15° L -> UTC+1
		assertEquals("UTC", WeatherZones.forPlace("gb", null, 51.5, -0.1));
	}

	@Test
	void aWidgetWithoutZoneGetsAnEstimateFromThePositionAndABadStoredZoneIsIgnored() {
		assertEquals(SAO_PAULO, WeatherZones.of(null, -27.6, -48.5)); // Palhoça (SC)
		assertEquals(ZoneId.of("America/Manaus"), WeatherZones.of("", -3.1, -60.0));
		assertEquals(ZoneId.of("America/Rio_Branco"), WeatherZones.of(null, -9.97, -67.8));
		assertEquals(SAO_PAULO, WeatherZones.of("Marte/Olympus", -23.5, -46.6));
		assertEquals(ZoneId.of("Europe/Lisbon"), WeatherZones.of("Europe/Lisbon", -23.5, -46.6)); // o guardado vale
		assertTrue(WeatherZones.approximate(-25.5, -54.58).contains("Sao_Paulo")); // Foz do Iguaçu é UTC-3, ao contrário de Dourados
	}

}
