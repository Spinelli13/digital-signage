package com.digitalsignage.backend.widget;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Os feeds RSS prontos para escolher no painel e a leitura (RSS 2.0 e Atom) de qualquer feed. Só manchetes, com a fonte citada. */
public final class NewsFeeds {

	public record Preset(String id, String name, String url) {
	}

	public record Headline(String title, Long publishedAtMs) {
	}

	/** Veículos conhecidos, com o endereço público do feed deles. */
	public static final List<Preset> PRESETS = List.of(
			new Preset("g1", "G1", "https://g1.globo.com/rss/g1/"),
			new Preset("agenciabrasil", "Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml"),
			new Preset("bbc", "BBC News Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml"),
			new Preset("cnn", "CNN Brasil", "https://www.cnnbrasil.com.br/feed/"),
			new Preset("uol", "UOL Notícias", "https://rss.uol.com.br/feed/noticias.xml"));

	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern CHARSET = Pattern.compile("charset=([\\w\\-]+)", Pattern.CASE_INSENSITIVE);
	private static final int MAX_TITLE = 220;

	private NewsFeeds() {
	}

	public static boolean isKnown(String id) {
		return PRESETS.stream().anyMatch(preset -> preset.id().equals(id));
	}

	public static Preset preset(String id) {
		return PRESETS.stream().filter(preset -> preset.id().equals(id)).findFirst().orElse(null);
	}

	/** As manchetes do feed, da mais nova para a mais antiga (na ordem do feed), no máximo {@code max}. */
	public static List<Headline> parse(byte[] body, String contentType, int max) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// Feed é texto de terceiros: nada de DTD nem entidades externas (XXE) e nada de expansão exagerada.
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		DocumentBuilder builder = factory.newDocumentBuilder();

		InputSource source = sourceOf(body, contentType);
		Document document = builder.parse(source);

		NodeList items = document.getElementsByTagName("item");
		boolean atom = items.getLength() == 0;
		if (atom) {
			items = document.getElementsByTagName("entry");
		}
		List<Headline> result = new ArrayList<>();
		for (int i = 0; i < items.getLength() && result.size() < max; i++) {
			Element item = (Element) items.item(i);
			String title = clean(firstText(item, "title"));
			if (title.isEmpty()) {
				continue;
			}
			String date = firstText(item, atom ? "updated" : "pubDate");
			if (date.isEmpty() && atom) {
				date = firstText(item, "published");
			}
			result.add(new Headline(title, parseDate(date)));
		}
		return result;
	}

	/** Sem declaração de codificação (alguns feeds antigos, ISO-8859-1), vale a do cabeçalho HTTP. */
	private static InputSource sourceOf(byte[] body, String contentType) {
		String head = new String(body, 0, Math.min(body.length, 100), StandardCharsets.ISO_8859_1).stripLeading();
		InputStream stream = new ByteArrayInputStream(body);
		if (head.startsWith("<?xml")) {
			return new InputSource(stream);
		}
		Charset charset = StandardCharsets.UTF_8;
		if (contentType != null) {
			Matcher matcher = CHARSET.matcher(contentType);
			if (matcher.find()) {
				try {
					charset = Charset.forName(matcher.group(1));
				} catch (Exception e) {
					// codificação desconhecida: fica o UTF-8
				}
			}
		}
		return new InputSource(new InputStreamReader(stream, charset));
	}

	private static String firstText(Element parent, String tag) {
		NodeList nodes = parent.getElementsByTagName(tag);
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			// o <title> do item, não o de algum elemento filho (ex.: <media:title>)
			if (node.getParentNode() == parent) {
				return node.getTextContent() == null ? "" : node.getTextContent();
			}
		}
		return "";
	}

	private static String clean(String raw) {
		String text = TAGS.matcher(raw).replaceAll(" ").replaceAll("\\s+", " ").strip();
		return text.length() > MAX_TITLE ? text.substring(0, MAX_TITLE - 1).stripTrailing() + "…" : text;
	}

	private static Long parseDate(String text) {
		String value = text == null ? "" : text.strip();
		if (value.isEmpty()) {
			return null;
		}
		try {
			return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
		} catch (Exception ignored) {
			// tenta ISO
		}
		try {
			return OffsetDateTime.parse(value).toInstant().toEpochMilli();
		} catch (Exception ignored) {
			// tenta ISO em UTC
		}
		try {
			return Instant.parse(value).toEpochMilli();
		} catch (Exception e) {
			return null;
		}
	}

	/** Para o painel: a lista de fontes prontas. */
	public static List<Map<String, String>> catalog() {
		List<Map<String, String>> list = new ArrayList<>();
		for (Preset preset : PRESETS) {
			Map<String, String> row = new LinkedHashMap<>();
			row.put("id", preset.id());
			row.put("name", preset.name());
			list.add(row);
		}
		return list;
	}

	public static String hostOf(String url) {
		try {
			String host = java.net.URI.create(url).getHost();
			return host == null ? url : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
		} catch (IllegalArgumentException e) {
			return url;
		}
	}

}
