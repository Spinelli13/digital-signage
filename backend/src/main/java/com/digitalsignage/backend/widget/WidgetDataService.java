package com.digitalsignage.backend.widget;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

import com.digitalsignage.backend.price.PriceBoardService;
import com.digitalsignage.backend.queue.QueueBoardService;

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
	/** Depois de uma falha o MET não é consultado de novo por este tempo (o provedor pede que ninguém insista). */
	static final Duration WEATHER_RETRY = Duration.ofMinutes(5);
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

	/**
	 * Uma resposta guardada. {@code lastModified} e {@code refreshAtMs} só existem para o clima, que pergunta ao provedor se
	 * mudou (GET condicional) e não consulta antes do {@code Expires} dele.
	 */
	private record Cached(long fetchedAtMs, Object value, String lastModified, long refreshAtMs) {

		Cached(long fetchedAtMs, Object value) {
			this(fetchedAtMs, value, null, 0);
		}
	}

	private final WidgetHttp http;
	private final ObjectMapper json;
	private final Clock clock;
	private final PriceBoardService prices;
	private final QueueBoardService queueBoard;
	private final Map<String, Cached> cache = new ConcurrentHashMap<>();
	private final Object placeSearchLock = new Object();
	private long lastPlaceSearchNanos;

	public WidgetDataService(WidgetHttp http, ObjectMapper json, Clock clock, PriceBoardService prices, QueueBoardService queueBoard) {
		this.http = http;
		this.json = json;
		this.clock = clock;
		this.prices = prices;
		this.queueBoard = queueBoard;
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
				case PRICES -> new Result(prices(settings), null);
				case QUEUE -> new Result(queueBoard.dataFor(settings), null);
				default -> new Result(Map.of(), null);
			};
		} catch (UnavailableException e) {
			return new Result(null, e.getMessage());
		}
	}

	// ---------- preços (do próprio servidor: vêm do banco, sem cache) ----------

	private Map<String, Object> prices(Map<String, Object> settings) {
		try {
			return prices.dataFor(settings);
		} catch (java.util.NoSuchElementException e) {
			throw new UnavailableException("A tabela de preços deste widget não existe mais");
		}
	}

	// ---------- clima (MET Norway) e busca de cidades (Nominatim) ----------

	private Map<String, Object> weather(Map<String, Object> settings) {
		double latitude = ((Number) settings.get("latitude")).doubleValue();
		double longitude = ((Number) settings.get("longitude")).doubleValue();
		// duas casas decimais (~1 km): TVs da mesma cidade dividem a mesma consulta (o MET aceita até quatro)
		String lat = String.format(Locale.ROOT, "%.2f", latitude);
		String lon = String.format(Locale.ROOT, "%.2f", longitude);
		ZoneId zone = WeatherZones.of(settings.get("timezone"), latitude, longitude);
		String key = "weather:" + lat + "," + lon;
		URI uri = URI.create("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" + lat + "&lon=" + lon);
		Cached forecast = forecast(key, uri, zone);
		// a previsão guardada é a resposta do MET como veio; "agora" e os dias são calculados a cada pedido
		try {
			Map<String, Object> data = new LinkedHashMap<>(MetForecast.parse((JsonNode) forecast.value(), zone, clock.instant()));
			data.put("fetchedAtMs", forecast.fetchedAtMs());
			return data;
		} catch (IOException e) {
			throw new UnavailableException("Não foi possível buscar o clima agora");
		}
	}

	/**
	 * A previsão do MET, no ritmo que os termos de uso pedem: nada de consultar antes do {@code Expires} (e nunca em menos de
	 * {@link #WEATHER_TTL}), e perguntar com {@code If-Modified-Since} para o MET responder 304 quando nada mudou.
	 */
	private Cached forecast(String key, URI uri, ZoneId zone) {
		long now = clock.millis();
		Cached hit = cache.get(key);
		if (hit != null && now < hit.refreshAtMs()) {
			if (hit.value() == null) {
				throw new UnavailableException("Não foi possível buscar o clima agora"); // falhou há pouco: não insiste
			}
			return hit;
		}
		String lastModified = hit == null ? null : hit.lastModified();
		try {
			WidgetHttp.Response response = http.get(uri, false, lastModified);
			Cached fresh;
			if (response.status() == 304 && hit != null && hit.value() != null) {
				fresh = new Cached(now, hit.value(), hit.lastModified(), refreshAt(now, response.expires()));
			} else if (response.status() / 100 == 2) {
				JsonNode root = json.readTree(response.body());
				MetForecast.parse(root, zone, clock.instant()); // confere antes de guardar
				fresh = new Cached(now, root, response.lastModified(), refreshAt(now, response.expires()));
			} else {
				throw new IOException("HTTP " + response.status());
			}
			if (cache.size() >= MAX_CACHE_ENTRIES) {
				evictOld(now);
			}
			cache.put(key, fresh);
			return fresh;
		} catch (IOException | RuntimeException e) {
			log.warn("Widget: falha ao buscar o clima ({}): {}", key, e.toString());
			long retryAt = now + WEATHER_RETRY.toMillis();
			if (hit != null && hit.value() != null && now - hit.fetchedAtMs() < STALE_LIMIT.toMillis()) {
				Cached stale = new Cached(hit.fetchedAtMs(), hit.value(), hit.lastModified(), retryAt);
				cache.put(key, stale);
				return stale; // melhor o dado de ontem do que nenhum
			}
			cache.put(key, new Cached(now, null, null, retryAt));
			throw new UnavailableException("Não foi possível buscar o clima agora");
		}
	}

	/** O instante da próxima consulta: o maior entre o tempo mínimo nosso e o {@code Expires} do provedor (no máximo 2 h). */
	private static long refreshAt(long now, String expires) {
		long minimum = now + WEATHER_TTL.toMillis();
		if (expires == null) {
			return minimum;
		}
		try {
			long expiresAt = ZonedDateTime.parse(expires, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
			return Math.min(Math.max(minimum, expiresAt), now + Duration.ofHours(2).toMillis());
		} catch (DateTimeParseException e) {
			return minimum;
		}
	}

	/**
	 * Busca de cidade para o painel (Nominatim, do OpenStreetMap), guardada por um dia. A política do serviço público pede no
	 * máximo uma consulta por segundo e identificação do aplicativo; aqui só o botão "Buscar" do painel chega até ele.
	 */
	@SuppressWarnings("unchecked")
	public List<Place> searchPlaces(String query, String language) {
		String text = query == null ? "" : query.strip();
		if (text.length() < 2) {
			return List.of();
		}
		String lang = "en".equalsIgnoreCase(language) ? "en" : "pt";
		Object found = cachedValue("geo:" + lang + ":" + text.toLowerCase(Locale.ROOT), GEO_TTL, () -> {
			pauseBetweenPlaceSearches();
			URI uri = URI.create("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=6&featureType=settlement"
					+ "&accept-language=" + lang + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
			List<Place> places = new ArrayList<>();
			for (JsonNode place : fetchJson(uri, false)) {
				JsonNode address = place.path("address");
				double latitude = place.path("lat").asDouble(Double.NaN);
				double longitude = place.path("lon").asDouble(Double.NaN);
				String name = place.path("name").asText("");
				if (name.isBlank() || Double.isNaN(latitude) || Double.isNaN(longitude)) {
					continue;
				}
				places.add(new Place(name, address.path("state").asText(""), address.path("country").asText(""), latitude, longitude,
						WeatherZones.forPlace(address.path("country_code").asText(""), address.path("ISO3166-2-lvl4").asText(null), latitude, longitude)));
			}
			return places;
		}, "a busca de cidades");
		return (List<Place>) found;
	}

	private void pauseBetweenPlaceSearches() {
		synchronized (placeSearchLock) {
			long wait = 1_100_000_000L - (System.nanoTime() - lastPlaceSearchNanos);
			if (lastPlaceSearchNanos != 0 && wait > 0) {
				try {
					Thread.sleep(wait / 1_000_000L + 1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			lastPlaceSearchNanos = System.nanoTime();
		}
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
