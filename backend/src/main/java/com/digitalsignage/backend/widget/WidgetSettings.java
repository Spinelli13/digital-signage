package com.digitalsignage.backend.widget;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.digitalsignage.backend.monitoring.WebhookUrlPolicy;

/**
 * As escolhas feitas no painel para cada tipo de widget. Aceita só o que cada tipo conhece (o resto é descartado), confere
 * tipos e limites e devolve um mapa limpo e estável: é isso que fica no banco e vai para a TV. Texto de pessoa nunca é HTML:
 * o Player o coloca na tela como texto puro.
 */
public final class WidgetSettings {

	/** Os temas prontos (escuro, claro, violeta da marca e o verde de antes); as cores da pessoa vão por cima deles. */
	static final Set<String> THEMES = Set.of("dark", "light", "brand", "green");
	/** As cores que a pessoa pode escolher no lugar das do tema: fundo, texto e destaque, no formato #rrggbb. */
	static final List<String> COLOR_KEYS = List.of("bgColor", "textColor", "accentColor");
	private static final java.util.regex.Pattern HEX_COLOR = java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");
	static final Set<String> ACCENTS = Set.of("brand", "blue", "amber", "red", "green");
	static final Set<String> ALIGNS = Set.of("center", "left");
	static final List<String> LANGS = List.of("pt-BR", "en");

	/** Moedas que a fonte de cotações (Banco Central Europeu, via Frankfurter) publica em relação ao real. */
	public static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "MXN");
	static final int MAX_CURRENCIES = 4;

	private WidgetSettings() {
	}

	public static Map<String, Object> sanitize(WidgetKind kind, Map<String, Object> raw, WebhookUrlPolicy urls) {
		Map<String, Object> in = raw == null ? Map.of() : raw;
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("theme", oneOf(in, "theme", THEMES, "dark"));
		out.put("lang", oneOfList(in, "lang", LANGS, "pt-BR"));
		for (String key : COLOR_KEYS) {
			String color = color(in, key);
			if (color != null) {
				out.put(key, color);
			}
		}
		switch (kind) {
			case CLOCK -> {
				out.put("timezone", zone(in, "timezone", "America/Sao_Paulo"));
				out.put("label", text(in, "label", 60, ""));
				out.put("hour12", bool(in, "hour12", false));
				out.put("showSeconds", bool(in, "showSeconds", false));
				out.put("showDate", bool(in, "showDate", true));
			}
			case WEATHER -> {
				out.put("city", text(in, "city", 80, ""));
				out.put("latitude", number(in, "latitude", -90, 90, null, "Escolha a cidade da previsão do tempo"));
				out.put("longitude", number(in, "longitude", -180, 180, null, "Escolha a cidade da previsão do tempo"));
				if (((String) out.get("city")).isBlank()) {
					throw new IllegalArgumentException("Escolha a cidade da previsão do tempo");
				}
				out.put("days", (int) Math.round(number(in, "days", 0, 3, 3.0, null)));
				if (!text(in, "timezone", 60, "").isBlank()) { // vem da busca de cidades; sem ele o servidor estima pela posição
					out.put("timezone", zone(in, "timezone", "UTC"));
				}
			}
			case NEWS -> {
				String source = text(in, "source", 20, "g1").toLowerCase(java.util.Locale.ROOT);
				if (!NewsFeeds.isKnown(source) && !"custom".equals(source)) {
					throw new IllegalArgumentException("Fonte de notícias desconhecida: " + source);
				}
				out.put("source", source);
				if ("custom".equals(source)) {
					String url = text(in, "customUrl", 500, "");
					out.put("customUrl", urls.validate(url, "feed de notícias").toString());
				}
				out.put("maxItems", (int) Math.round(number(in, "maxItems", 3, 12, 8.0, null)));
				out.put("secondsPerItem", (int) Math.round(number(in, "secondsPerItem", 4, 30, 8.0, null)));
			}
			case RATES -> out.put("currencies", currencies(in));
			case NOTICE -> {
				String title = text(in, "title", 80, "");
				String message = text(in, "message", 400, "");
				if (title.isBlank() && message.isBlank()) {
					throw new IllegalArgumentException("Escreva o título ou a mensagem do aviso");
				}
				out.put("title", title);
				out.put("message", message);
				out.put("accent", oneOf(in, "accent", ACCENTS, "brand"));
				out.put("align", oneOf(in, "align", ALIGNS, "center"));
			}
			case PRICES -> {
				out.put("priceListId", (long) Math.round(number(in, "priceListId", 1, 9_000_000_000_000.0, null, "Escolha a tabela de preços")));
				out.put("title", text(in, "title", 60, ""));
				out.put("columns", (int) Math.round(number(in, "columns", 1, 3, 1.0, null)));
				out.put("rowsPerColumn", (int) Math.round(number(in, "rowsPerColumn", 4, 14, 8.0, null)));
				out.put("secondsPerPage", (int) Math.round(number(in, "secondsPerPage", 4, 30, 8.0, null)));
				out.put("showCategories", bool(in, "showCategories", true));
			}
			case QUEUE -> {
				out.put("title", text(in, "title", 60, ""));
				out.put("queueIds", queueIds(in));
				out.put("calls", (int) Math.round(number(in, "calls", 3, 8, 5.0, null)));
				out.put("sound", bool(in, "sound", true));
			}
			case COUNTDOWN -> {
				String label = text(in, "label", 80, "");
				if (label.isBlank()) {
					throw new IllegalArgumentException("Escreva o nome do que está sendo esperado (ex.: Natal)");
				}
				out.put("label", label);
				out.put("target", target(in));
				out.put("timezone", zone(in, "timezone", "America/Sao_Paulo"));
				out.put("finishedText", text(in, "finishedText", 80, "Chegou o dia!"));
			}
		}
		return out;
	}

	/** Uma cor no formato #rrggbb (guardada em minúsculas); nula se a pessoa deixou em branco (vale a cor do tema). */
	private static String color(Map<String, Object> in, String key) {
		Object value = in.get(key);
		if (value == null || value.toString().isBlank()) {
			return null;
		}
		String color = value.toString().strip();
		if (!HEX_COLOR.matcher(color).matches()) {
			throw new IllegalArgumentException("Cor inválida: use o formato #RRGGBB (por exemplo, #8B5CF6)");
		}
		return color.toLowerCase(java.util.Locale.ROOT);
	}

	private static String text(Map<String, Object> in, String key, int max, String fallback) {
		Object value = in.get(key);
		if (value == null) {
			return fallback;
		}
		String text = value.toString().replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").strip();
		if (text.length() > max) {
			throw new IllegalArgumentException("O campo \"" + key + "\" aceita no máximo " + max + " caracteres");
		}
		return text.isEmpty() ? fallback : text;
	}

	private static boolean bool(Map<String, Object> in, String key, boolean fallback) {
		Object value = in.get(key);
		if (value == null) {
			return fallback;
		}
		if (value instanceof Boolean b) {
			return b;
		}
		return "true".equalsIgnoreCase(value.toString());
	}

	private static Double number(Map<String, Object> in, String key, double min, double max, Double fallback, String missing) {
		Object value = in.get(key);
		if (value == null || value.toString().isBlank()) {
			if (fallback != null) {
				return fallback;
			}
			throw new IllegalArgumentException(missing);
		}
		double number;
		try {
			number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString().trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("O campo \"" + key + "\" precisa ser um número");
		}
		if (Double.isNaN(number) || number < min || number > max) {
			throw new IllegalArgumentException("O campo \"" + key + "\" precisa estar entre " + trimZero(min) + " e " + trimZero(max));
		}
		return Math.round(number * 10_000.0) / 10_000.0;
	}

	private static String trimZero(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
	}

	private static String oneOf(Map<String, Object> in, String key, Set<String> allowed, String fallback) {
		Object value = in.get(key);
		if (value == null || value.toString().isBlank()) {
			return fallback;
		}
		String text = value.toString().trim().toLowerCase(java.util.Locale.ROOT);
		if (!allowed.contains(text)) {
			throw new IllegalArgumentException("Valor inválido em \"" + key + "\": " + value);
		}
		return text;
	}

	private static String oneOfList(Map<String, Object> in, String key, List<String> allowed, String fallback) {
		Object value = in.get(key);
		if (value == null || value.toString().isBlank()) {
			return fallback;
		}
		return allowed.stream().filter(candidate -> candidate.equalsIgnoreCase(value.toString().trim())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Valor inválido em \"" + key + "\": " + value));
	}

	private static String zone(Map<String, Object> in, String key, String fallback) {
		String id = text(in, key, 60, fallback);
		try {
			return ZoneId.of(id).getId();
		} catch (DateTimeException e) {
			throw new IllegalArgumentException("Fuso horário desconhecido: " + id);
		}
	}

	/** As filas que o painel de chamadas mostra (vazio = todas). */
	private static List<Long> queueIds(Map<String, Object> in) {
		List<Long> ids = new ArrayList<>();
		if (in.get("queueIds") instanceof List<?> list) {
			for (Object item : list) {
				long id;
				try {
					id = item instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(item).trim());
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Fila inválida no painel de chamadas: " + item);
				}
				if (id < 1) {
					throw new IllegalArgumentException("Fila inválida no painel de chamadas: " + item);
				}
				if (!ids.contains(id)) {
					ids.add(id);
				}
			}
		}
		if (ids.size() > 20) {
			throw new IllegalArgumentException("Escolha no máximo 20 filas");
		}
		return ids;
	}

	private static List<String> currencies(Map<String, Object> in) {
		Object value = in.get("currencies");
		List<String> chosen = new ArrayList<>();
		if (value instanceof List<?> list) {
			for (Object item : list) {
				String code = item == null ? "" : item.toString().trim().toUpperCase(java.util.Locale.ROOT);
				if (!CURRENCIES.contains(code)) {
					throw new IllegalArgumentException("Moeda não disponível: " + item);
				}
				if (!chosen.contains(code)) {
					chosen.add(code);
				}
			}
		}
		if (chosen.isEmpty()) {
			return List.of("USD", "EUR");
		}
		if (chosen.size() > MAX_CURRENCIES) {
			throw new IllegalArgumentException("Escolha no máximo " + MAX_CURRENCIES + " moedas");
		}
		return chosen;
	}

	/** Data e hora (sem fuso: vale o fuso escolhido), no formato 2026-12-25T00:00. */
	private static String target(Map<String, Object> in) {
		String value = text(in, "target", 25, "");
		if (value.isBlank()) {
			throw new IllegalArgumentException("Escolha a data e a hora da contagem regressiva");
		}
		try {
			return LocalDateTime.parse(value.length() == 10 ? value + "T00:00" : value).withSecond(0).withNano(0).toString();
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("A data da contagem regressiva não é válida");
		}
	}

}
