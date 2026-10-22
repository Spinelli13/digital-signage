package com.digitalsignage.backend.widget;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lê a previsão do MET Norway (Locationforecast 2.0, formato compact, licença CC BY 4.0) e a entrega no formato que o widget de
 * clima do Player já entende: o código do tempo é o da OMM/WMO (o que o Open-Meteo entregava), então um Player antigo continua
 * mostrando a previsão sem atualização.
 *
 * <p>O MET dá hora em hora nos primeiros dias e de 6 em 6 horas depois, sempre em UTC; os dias aqui são os do fuso da TV.
 * O MET não informa probabilidade de chuva fora da Escandinávia, só o volume: {@code rainMm} é o volume do dia e
 * {@code rainChance} (que o Player antigo ainda mostra) é a parte do dia com chuva prevista, não uma probabilidade.
 */
final class MetForecast {

	/** Citação exigida pela licença; o widget mostra no rodapé. */
	static final String CREDIT = "MET Norway";

	private static final int DAYS = 4;
	private static final double MS_TO_KMH = 3.6;

	private MetForecast() {
	}

	static Map<String, Object> parse(JsonNode root, ZoneId zone, Instant now) throws IOException {
		JsonNode series = root.path("properties").path("timeseries");
		if (!series.isArray() || series.isEmpty()) {
			throw new IOException("Resposta do clima sem previsão");
		}
		JsonNode nearest = null;
		long nearestDistance = Long.MAX_VALUE;
		for (JsonNode entry : series) {
			long distance = Math.abs(Duration.between(now, time(entry)).toMillis());
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = entry;
			}
		}
		JsonNode details = nearest.path("data").path("instant").path("details");
		if (!details.has("air_temperature")) {
			throw new IOException("Resposta do clima sem temperatura");
		}
		String symbol = symbolOf(nearest, "next_1_hours", "next_6_hours", "next_12_hours");
		if (symbol == null) {
			symbol = symbolFromClouds(details.path("cloud_area_fraction").asDouble(100));
		}
		Map<String, Object> current = new LinkedHashMap<>();
		current.put("temperature", details.path("air_temperature").asDouble());
		current.put("code", wmoCode(symbol));
		current.put("isDay", !symbol.endsWith("_night"));
		current.put("windKmh", Math.round(details.path("wind_speed").asDouble() * MS_TO_KMH * 10.0) / 10.0);
		current.put("humidity", (int) Math.round(details.path("relative_humidity").asDouble()));

		LocalDate today = now.atZone(zone).toLocalDate();
		Map<LocalDate, Day> days = new TreeMap<>();
		for (JsonNode entry : series) {
			ZonedDateTime at = time(entry).atZone(zone);
			LocalDate date = at.toLocalDate();
			if (date.isBefore(today) || !date.isBefore(today.plusDays(DAYS))) {
				continue;
			}
			days.computeIfAbsent(date, key -> new Day()).add(entry, at.getHour());
		}
		List<Map<String, Object>> daily = new ArrayList<>();
		for (Map.Entry<LocalDate, Day> day : days.entrySet()) {
			Map<String, Object> row = day.getValue().toRow(day.getKey());
			if (row != null) {
				daily.add(row);
			}
		}
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("current", current);
		data.put("daily", daily);
		data.put("timezone", zone.getId());
		data.put("credit", CREDIT);
		return data;
	}

	/** Um dia da previsão: extremos de temperatura, chuva e o símbolo que melhor resume o dia. */
	private static final class Day {
		private Double max;
		private Double min;
		private double rainMm;
		private int wetHours;
		private int totalHours;
		private String symbol;
		private int symbolScore = Integer.MAX_VALUE;

		void add(JsonNode entry, int hour) {
			JsonNode data = entry.path("data");
			JsonNode instant = data.path("instant").path("details");
			if (instant.has("air_temperature")) {
				extremes(instant.path("air_temperature").asDouble(), instant.path("air_temperature").asDouble());
			}
			JsonNode oneHour = data.path("next_1_hours");
			JsonNode sixHours = data.path("next_6_hours");
			JsonNode block = oneHour.isMissingNode() ? sixHours : oneHour;
			if (!block.isMissingNode()) {
				int hours = oneHour.isMissingNode() ? 6 : 1;
				double amount = block.path("details").path("precipitation_amount").asDouble(0);
				rainMm += amount;
				totalHours += hours;
				if (amount >= 0.1 * hours) {
					wetHours += hours;
				}
				JsonNode details = sixHours.path("details");
				if (details.has("air_temperature_max") && details.has("air_temperature_min")) {
					extremes(details.path("air_temperature_max").asDouble(), details.path("air_temperature_min").asDouble());
				}
			}
			// o símbolo do dia: o de 12 h que começa de manhã; sem ele, o de 6 h do meio-dia; sem ele, o de 1 h
			consider(data.path("next_12_hours"), Math.abs(hour - 9));
			consider(sixHours, 100 + Math.abs(hour - 12));
			consider(oneHour, 200 + Math.abs(hour - 12));
		}

		private void extremes(double high, double low) {
			max = max == null ? high : Math.max(max, high);
			min = min == null ? low : Math.min(min, low);
		}

		private void consider(JsonNode block, int score) {
			String code = block.path("summary").path("symbol_code").asText(null);
			if (code != null && score < symbolScore) {
				symbol = code;
				symbolScore = score;
			}
		}

		Map<String, Object> toRow(LocalDate date) {
			if (max == null || min == null) {
				return null;
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("date", date.toString());
			row.put("code", wmoCode(symbol == null ? "cloudy" : symbol));
			row.put("max", max);
			row.put("min", min);
			row.put("rainMm", Math.round(rainMm * 10.0) / 10.0);
			row.put("rainChance", totalHours == 0 ? 0 : (int) Math.round(100.0 * wetHours / totalHours));
			return row;
		}
	}

	private static Instant time(JsonNode entry) throws IOException {
		try {
			return Instant.parse(entry.path("time").asText());
		} catch (RuntimeException e) {
			throw new IOException("Resposta do clima com hora inválida");
		}
	}

	private static String symbolOf(JsonNode entry, String... blocks) {
		for (String block : blocks) {
			String code = entry.path("data").path(block).path("summary").path("symbol_code").asText(null);
			if (code != null) {
				return code;
			}
		}
		return null;
	}

	private static String symbolFromClouds(double cloudPercent) {
		if (cloudPercent < 15) {
			return "clearsky_day";
		}
		if (cloudPercent < 40) {
			return "fair_day";
		}
		return cloudPercent < 75 ? "partlycloudy_day" : "cloudy";
	}

	/** O símbolo do MET ("lightrainshowers_day") no código da OMM que o widget conhece. */
	static int wmoCode(String symbol) {
		String base = symbol.replaceAll("_(day|night|polartwilight)$", "");
		boolean heavy = base.startsWith("heavy");
		boolean light = base.startsWith("light");
		boolean showers = base.contains("showers");
		if (base.contains("thunder")) {
			return 95;
		}
		if (base.contains("snow")) {
			return showers ? 85 : light ? 71 : heavy ? 75 : 73;
		}
		if (base.contains("sleet")) {
			return showers ? 80 : 67;
		}
		if (base.contains("rain")) {
			if (showers) {
				return light ? 80 : heavy ? 82 : 81;
			}
			return light ? 61 : heavy ? 65 : 63;
		}
		return switch (base) {
			case "clearsky" -> 0;
			case "fair" -> 1;
			case "partlycloudy" -> 2;
			case "fog" -> 45;
			default -> 3;
		};
	}

}
