package com.digitalsignage.backend.widget;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Os dados que os widgets mostram (clima, notícias, cotações). O servidor busca, guarda por um tempo e entrega: as TVs nunca
 * falam com os provedores (chaves e limites ficam num lugar só, e centenas de TVs viram uma consulta por cidade).
 *
 * <p>Se o provedor falhar, vale o último resultado por até {@link #STALE_LIMIT} (a TV mostra "atualizado às…"); sem nada
 * guardado, o resultado vem sem dados e com o motivo, e o Player mostra um aviso discreto em vez de quebrar.
 */
@Service
public class WidgetDataService {

	private static final Logger log = LoggerFactory.getLogger(WidgetDataService.class);

	static final Duration WEATHER_TTL = Duration.ofMinutes(30);
	static final Duration NEWS_TTL = Duration.ofMinutes(15);
	static final Duration RATES_TTL = Duration.ofMinutes(60);
	static final Duration GEO_TTL = Duration.ofHours(24);
	static final Duration STALE_LIMIT = Duration.ofHours(24);
	private static final int MAX_CACHE_ENTRIES = 2000;

	/** {@code data} vem vazio quando não foi possível buscar e não há nada guardado; {@code error} diz por quê. */
	public record Result(Map<String, Object> data, String error) {
	}

	public record Place(String name, String region, String country, double latitude, double longitude, String timezone) {
	}

	private record Cached(long fetchedAtMs, Object value) {
	}

	private final WidgetHttp http;
	private final ObjectMapper json;
	private final Clock clock;
	private final Map<String, Cached> cache = new ConcurrentHashMap<>();

	public WidgetDataService(WidgetHttp http, ObjectMapper json, Clock clock) {
		this.http = http;
		this.json = json;
		this.clock = clock;
	}

	/** Os dados do widget já com o instante em que foram buscados (o Player mostra "atualizado há…"). */
	public Result dataFor(WidgetKind kind, Map<String, Object> settings) {
		if (!kind.needsData()) {
			return new Result(Map.of(), null);
		}
		try {
			return switch (kind) {
				case WEATHER -> new Result(weather(settings), null);
				case NEWS -> new Result(news(settings), null);
				case RATES -> new Result(rates(settings), null);
				default -> new Result(Map.of(), null);
			};
		} catch (UnavailableException e) {
			return new Result(null, e.getMessage());
		}
	}

	// ---------- clima (Open-Meteo) ----------

	private Map<String, Object> weather(Map<String, Object> settings) {
		double latitude = ((Number) settings.get("latitude")).doubleValue();
		double longitude = ((Number) settings.get("longitude")).doubleValue();
		// duas casas decimais (~1 km): TVs da mesma cidade dividem a mesma consulta
		String lat = String.format(Locale.ROOT, "%.2f", latitude);
		String lon = String.format(Locale.ROOT, "%.2f", longitude);
		return cached("weather:" + lat + "," + lon, WEATHER_TTL, () -> {
			URI uri = URI.create("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
					+ "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
					+ "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
					+ "&timezone=auto&forecast_days=4");
			JsonNode root = fetchJson(uri, false);
			JsonNode current = root.path("current");
			if (current.isMissingNode() || !current.has("temperature_2m")) {
				throw new IOException("Resposta do clima sem temperatura");
			}
			Map<String, Object> now = new LinkedHashMap<>();
			now.put("temperature", current.path("temperature_2m").asDouble());
			now.put("code", current.path("weather_code").asInt());
			now.put("isDay", current.path("is_day").asInt(1) == 1);
			now.put("windKmh", current.path("wind_speed_10m").asDouble());
			now.put("humidity", current.path("relative_humidity_2m").asInt());
			JsonNode daily = root.path("daily");
			List<Map<String, Object>> days = new ArrayList<>();
			for (int i = 0; i < daily.path("time").size(); i++) {
				Map<String, Object> day = new LinkedHashMap<>();
				day.put("date", daily.path("time").path(i).asText());
				day.put("code", daily.path("weather_code").path(i).asInt());
				day.put("max", daily.path("temperature_2m_max").path(i).asDouble());
				day.put("min", daily.path("temperature_2m_min").path(i).asDouble());
				day.put("rainChance", daily.path("precipitation_probability_max").path(i).asInt());
				days.add(day);
			}
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("current", now);
			data.put("daily", days);
			data.put("timezone", root.path("timezone").asText("auto"));
			return data;
		}, "o clima");
	}

	/** Busca de cidade para o painel (Open-Meteo geocoding), guardada por um dia. */
	@SuppressWarnings("unchecked")
	public List<Place> searchPlaces(String query, String language) {
		String text = query == null ? "" : query.strip();
		if (text.length() < 2) {
			return List.of();
		}
		String lang = "en".equalsIgnoreCase(language) ? "en" : "pt";
		Object found = cachedValue("geo:" + lang + ":" + text.toLowerCase(Locale.ROOT), GEO_TTL, () -> {
			URI uri = URI.create("https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&language=" + lang + "&name="
					+ URLEncoder.encode(text, StandardCharsets.UTF_8));
			JsonNode results = fetchJson(uri, false).path("results");
			List<Place> places = new ArrayList<>();
			for (JsonNode place : results) {
				places.add(new Place(place.path("name").asText(), place.path("admin1").asText(""), place.path("country").asText(""),
						place.path("latitude").asDouble(), place.path("longitude").asDouble(), place.path("timezone").asText("")));
			}
			return places;
		}, "a busca de cidades");
		return (List<Place>) found;
	}

	// ---------- notícias (RSS) ----------

	private Map<String, Object> news(Map<String, Object> settings) {
		String source = String.valueOf(settings.get("source"));
		int max = ((Number) settings.get("maxItems")).intValue();
		boolean custom = "custom".equals(source);
		NewsFeeds.Preset preset = custom ? null : NewsFeeds.preset(source);
		String url = custom ? String.valueOf(settings.get("customUrl")) : preset.url();
		String name = custom ? NewsFeeds.hostOf(url) : preset.name();
		Map<String, Object> data = cached("news:" + url, NEWS_TTL, () -> {
			WidgetHttp.Response response = http.get(URI.create(url), custom);
			if (response.status() / 100 != 2) {
				throw new IOException("O feed respondeu HTTP " + response.status());
			}
			List<NewsFeeds.Headline> headlines;
			try {
				headlines = NewsFeeds.parse(response.body(), response.contentType(), 12);
			} catch (Exception e) {
				throw new IOException("O feed não pôde ser lido: " + e.getClass().getSimpleName());
			}
			if (headlines.isEmpty()) {
				throw new IOException("O feed está vazio");
			}
			List<Map<String, Object>> items = new ArrayList<>();
			for (NewsFeeds.Headline headline : headlines) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("title", headline.title());
				item.put("publishedAtMs", headline.publishedAtMs());
				items.add(item);
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("source", name);
			result.put("items", items);
			return result;
		}, "as notícias");
		// cada widget mostra só quantas manchetes escolheu, mesmo que o feed guardado tenha mais
		Map<String, Object> trimmed = new LinkedHashMap<>(data);
		Object items = data.get("items");
		if (items instanceof List<?> list && list.size() > max) {
			trimmed.put("items", new ArrayList<>(list.subList(0, max)));
		}
		return trimmed;
	}

	// ---------- cotações (Frankfurter, Banco Central Europeu) ----------

	@SuppressWarnings("unchecked")
	private Map<String, Object> rates(Map<String, Object> settings) {
		List<String> currencies = ((List<Object>) settings.get("currencies")).stream().map(Object::toString).toList();
		return cached("rates:" + String.join(",", currencies), RATES_TTL, () -> {
			LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
			URI uri = URI.create("https://api.frankfurter.dev/v1/" + today.minusDays(10) + ".." + today
					+ "?base=BRL&symbols=" + String.join(",", currencies));
			JsonNode rates = fetchJson(uri, false).path("rates");
			List<String> dates = new ArrayList<>();
			rates.fieldNames().forEachRemaining(dates::add);
			dates.sort(String::compareTo);
			if (dates.isEmpty()) {
				throw new IOException("Resposta de cotações vazia");
			}
			String last = dates.get(dates.size() - 1);
			String previous = dates.size() > 1 ? dates.get(dates.size() - 2) : null;
			List<Map<String, Object>> rows = new ArrayList<>();
			for (String code : currencies) {
				double perBrl = rates.path(last).path(code).asDouble(0);
				if (perBrl <= 0) {
					continue;
				}
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("code", code);
				double value = 1.0 / perBrl; // quantos reais vale 1 unidade da moeda
				row.put("value", Math.round(value * 10_000.0) / 10_000.0);
				double before = previous == null ? 0 : rates.path(previous).path(code).asDouble(0);
				if (before > 0) {
					double previousValue = 1.0 / before;
					row.put("changePercent", Math.round((value / previousValue - 1.0) * 10_000.0) / 100.0);
				}
				rows.add(row);
			}
			if (rows.isEmpty()) {
				throw new IOException("Nenhuma das moedas veio na resposta");
			}
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("base", "BRL");
			data.put("date", last);
			data.put("rates", rows);
			return data;
		}, "as cotações");
	}

	// ---------- guarda e busca ----------

	@SuppressWarnings("unchecked")
	private Map<String, Object> cached(String key, Duration ttl, ThrowingSupplier<Map<String, Object>> loader, String what) {
		Object value = cachedValue(key, ttl, () -> loader.get(), what);
		long fetchedAt = cache.get(key).fetchedAtMs();
		Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) value);
		copy.put("fetchedAtMs", fetchedAt);
		return copy;
	}

	private Object cachedValue(String key, Duration ttl, ThrowingSupplier<Object> loader, String what) {
		long now = clock.millis();
		Cached hit = cache.get(key);
		if (hit != null && now - hit.fetchedAtMs() < ttl.toMillis()) {
			return hit.value();
		}
		try {
			Object fresh = loader.get();
			if (cache.size() >= MAX_CACHE_ENTRIES) {
				evictOld(now);
			}
			cache.put(key, new Cached(now, fresh));
			return fresh;
		} catch (IOException | RuntimeException e) {
			log.warn("Widget: falha ao buscar {} ({}): {}", what, key, e.toString());
			if (hit != null && now - hit.fetchedAtMs() < STALE_LIMIT.toMillis()) {
				return hit.value(); // melhor o dado de ontem do que nenhum
			}
			throw new UnavailableException("Não foi possível buscar " + what + " agora");
		}
	}

	private void evictOld(long now) {
		for (Iterator<Map.Entry<String, Cached>> it = cache.entrySet().iterator(); it.hasNext();) {
			if (now - it.next().getValue().fetchedAtMs() > STALE_LIMIT.toMillis()) {
				it.remove();
			}
		}
		if (cache.size() >= MAX_CACHE_ENTRIES) {
			cache.clear();
		}
	}

	private JsonNode fetchJson(URI uri, boolean publicOnly) throws IOException {
		WidgetHttp.Response response = http.get(uri, publicOnly);
		if (response.status() / 100 != 2) {
			throw new IOException("HTTP " + response.status());
		}
		return json.readTree(response.body());
	}

	/** Esquece tudo o que foi guardado (os testes começam sempre do zero). */
	public void clearCache() {
		cache.clear();
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T> {
		T get() throws IOException;
	}

	/** Não deu para buscar e não há nada guardado. */
	private static final class UnavailableException extends RuntimeException {
		UnavailableException(String message) {
			super(message);
		}
	}

}
