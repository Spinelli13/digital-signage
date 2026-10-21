package com.digitalsignage.backend.price;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.media.MediaRepository;
import com.digitalsignage.backend.monitoring.CsvWriter;
import com.digitalsignage.backend.playlist.PlaylistRepository;

/** Criar, editar, importar e exportar tabelas de preços, e avisar as TVs que mostram a tabela quando um preço muda. */
@Service
public class PriceListService {

	public static final int MAX_ITEMS = 300;
	public static final int MAX_RULES = 20;
	static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");
	static final Set<String> CURRENCIES = Set.of("BRL", "USD", "EUR");

	/** O resultado de uma importação: a tabela já com os itens e o que foi ignorado (uma frase por linha problemática). */
	public record Imported(PriceList list, int imported, List<String> skipped) {
	}

	private final PriceListRepository repository;
	private final MediaRepository mediaRepository;
	private final PlaylistRepository playlistRepository;

	public PriceListService(PriceListRepository repository, MediaRepository mediaRepository, PlaylistRepository playlistRepository) {
		this.repository = repository;
		this.mediaRepository = mediaRepository;
		this.playlistRepository = playlistRepository;
	}

	@Transactional
	public PriceList create(PriceListRequest request) {
		requireUniqueName(request.name().strip(), null);
		PriceList list = new PriceList(request.name().strip());
		fill(list, request);
		return repository.save(list);
	}

	@Transactional
	public PriceList update(Long id, PriceListRequest request) {
		PriceList list = find(id);
		requireUniqueName(request.name().strip(), id);
		list.setName(request.name().strip());
		fill(list, request);
		PriceList saved = repository.save(list);
		notifyTvs(id);
		return saved;
	}

	@Transactional
	public void delete(Long id) {
		PriceList list = find(id);
		int using = widgetsUsing(id).size();
		if (using > 0) {
			throw new IllegalArgumentException("A tabela \"" + list.getName() + "\" está em " + using + " widget(s) da biblioteca. Exclua ou troque os widgets antes.");
		}
		repository.delete(list);
	}

	@Transactional(readOnly = true)
	public PriceList find(Long id) {
		return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Tabela de preços não encontrada: " + id));
	}

	/** Conferido, normalizado e SEM gravar: é o que alimenta a pré-visualização do painel enquanto a pessoa edita. */
	public PriceList draft(PriceListRequest request) {
		PriceList list = new PriceList(request.name().strip());
		fill(list, request);
		return list;
	}

	/** Os widgets (da biblioteca) que mostram esta tabela. */
	@Transactional(readOnly = true)
	public List<Media> widgetsUsing(Long priceListId) {
		return mediaRepository.findByWidgetKind("PRICES").stream()
				.filter(media -> media.getWidgetConfig() != null && String.valueOf(priceListId).equals(String.valueOf(longOf(media.getWidgetConfig().get("priceListId")))))
				.toList();
	}

	/** O preço mudou: as playlists que exibem um widget desta tabela ganham versão nova, e as TVs buscam os dados já. */
	private void notifyTvs(Long priceListId) {
		for (Media media : widgetsUsing(priceListId)) {
			playlistRepository.findUsing(media.getId()).forEach(playlist -> playlist.touch());
		}
	}

	private static Long longOf(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		try {
			return value == null ? null : Long.valueOf(value.toString());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// ---------- validação ----------

	private void requireUniqueName(String name, Long id) {
		boolean taken = id == null ? repository.existsByNameIgnoreCase(name) : repository.existsByNameIgnoreCaseAndIdNot(name, id);
		if (taken) {
			throw new com.digitalsignage.backend.common.ConflictException("Já existe uma tabela de preços chamada \"" + name + "\"");
		}
	}

	private void fill(PriceList list, PriceListRequest request) {
		String currency = request.currency() == null || request.currency().isBlank() ? "BRL" : request.currency().strip().toUpperCase(Locale.ROOT);
		if (!CURRENCIES.contains(currency)) {
			throw new IllegalArgumentException("Moeda não disponível: " + request.currency() + ". Use BRL, USD ou EUR.");
		}
		String timezone = request.timezone() == null || request.timezone().isBlank() ? "America/Sao_Paulo" : request.timezone().strip();
		try {
			timezone = ZoneId.of(timezone).getId();
		} catch (DateTimeException e) {
			throw new IllegalArgumentException("Fuso horário desconhecido: " + timezone);
		}
		list.setCurrency(currency);
		list.setTimezone(timezone);

		List<PriceItem> items = normalizeItems(request.items() == null ? List.of() : request.items());
		list.setItems(items);
		list.setRules(normalizeRules(request.rules() == null ? List.of() : request.rules(), items));
	}

	private static List<PriceItem> normalizeItems(List<PriceListRequest.ItemRequest> requested) {
		if (requested.size() > MAX_ITEMS) {
			throw new IllegalArgumentException("Uma tabela aceita no máximo " + MAX_ITEMS + " itens");
		}
		Set<String> ids = new LinkedHashSet<>();
		List<PriceItem> items = new ArrayList<>();
		for (PriceListRequest.ItemRequest item : requested) {
			String name = item.name().strip();
			if (name.isEmpty()) {
				throw new IllegalArgumentException("Todo item precisa de um nome");
			}
			String id = item.id() == null || item.id().isBlank() ? UUID.randomUUID().toString().substring(0, 8) : item.id().strip();
			if (!ids.add(id)) {
				throw new IllegalArgumentException("Dois itens têm o mesmo identificador (\"" + id + "\")");
			}
			items.add(new PriceItem(id, blankToNull(item.category()), name, blankToNull(item.description()), price(item.price(), name),
					blankToNull(item.unit()), item.available() == null || item.available(), item.highlight() != null && item.highlight()));
		}
		return items;
	}

	private static List<PriceRule> normalizeRules(List<PriceListRequest.RuleRequest> requested, List<PriceItem> items) {
		if (requested.size() > MAX_RULES) {
			throw new IllegalArgumentException("Uma tabela aceita no máximo " + MAX_RULES + " variações por horário");
		}
		Set<String> known = new LinkedHashSet<>();
		items.forEach(item -> known.add(item.id()));
		List<PriceRule> rules = new ArrayList<>();
		for (PriceListRequest.RuleRequest rule : requested) {
			String name = rule.name().strip();
			List<Integer> days = rule.days() == null ? List.of() : rule.days().stream().distinct().sorted().toList();
			if (days.stream().anyMatch(day -> day < 1 || day > 7)) {
				throw new IllegalArgumentException("Dia da semana inválido na variação \"" + name + "\" (use de 1 = segunda a 7 = domingo)");
			}
			String start = time(rule.start(), name);
			String end = time(rule.end(), name);
			if (start != null && start.equals(end)) {
				throw new IllegalArgumentException("A variação \"" + name + "\" começa e termina na mesma hora");
			}
			BigDecimal percent = rule.percent() == null ? BigDecimal.ZERO : rule.percent();
			if (percent.compareTo(new BigDecimal("-100")) < 0 || percent.compareTo(new BigDecimal("1000")) > 0) {
				throw new IllegalArgumentException("O percentual da variação \"" + name + "\" precisa estar entre -100 e 1000");
			}
			List<String> itemIds = rule.itemIds() == null ? List.of() : rule.itemIds().stream().distinct().toList();
			for (String itemId : itemIds) {
				if (!known.contains(itemId)) {
					throw new IllegalArgumentException("A variação \"" + name + "\" cita um item que não está na tabela");
				}
			}
			Map<String, BigDecimal> fixed = new LinkedHashMap<>();
			if (rule.itemPrices() != null) {
				for (Map.Entry<String, BigDecimal> entry : rule.itemPrices().entrySet()) {
					if (!known.contains(entry.getKey())) {
						throw new IllegalArgumentException("A variação \"" + name + "\" cita um item que não está na tabela");
					}
					fixed.put(entry.getKey(), price(entry.getValue(), name));
				}
			}
			if (percent.signum() == 0 && fixed.isEmpty()) {
				throw new IllegalArgumentException("A variação \"" + name + "\" não muda nenhum preço: informe um percentual ou preços fixos");
			}
			rules.add(new PriceRule(name, days, start, end, percent.setScale(2, java.math.RoundingMode.HALF_UP), itemIds, fixed));
		}
		return rules;
	}

	private static String time(String text, String rule) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(text.strip().length() == 4 ? "0" + text.strip() : text.strip()).toString();
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("Horário inválido na variação \"" + rule + "\" (use HH:mm, ex.: 17:30)");
		}
	}

	private static BigDecimal price(BigDecimal value, String what) {
		if (value == null || value.signum() < 0 || value.compareTo(MAX_PRICE) > 0) {
			throw new IllegalArgumentException("Preço inválido em \"" + what + "\": use um valor entre 0 e 999.999,99");
		}
		return PriceBoard.scale(value);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	// ---------- CSV ----------

	/** A tabela como planilha (separador ";", acentos certos no Excel brasileiro): uma linha por item. */
	public byte[] exportCsv(PriceList list) {
		CsvWriter csv = new CsvWriter().row("Categoria", "Nome", "Descrição", "Preço", "Unidade", "Disponível", "Destaque");
		for (PriceItem item : list.getItems()) {
			csv.row(item.category(), item.name(), item.description(), item.price().toPlainString().replace('.', ','), item.unit(),
					item.available() ? "sim" : "não", item.highlight() ? "sim" : "não");
		}
		return csv.bytes();
	}

	/**
	 * Lê uma planilha (CSV com ";" ou ",", com ou sem BOM; UTF-8 ou, se não for UTF-8 válido, ISO-8859-1/Windows-1252) e troca
	 * ({@code replace}) ou acrescenta ({@code append}) os itens da tabela. Linhas sem nome ou com preço ilegível são ignoradas e
	 * explicadas; as variações por horário só continuam valendo para os itens que ainda existem.
	 */
	@Transactional
	public Imported importCsv(Long id, byte[] content, boolean replace) {
		PriceList list = find(id);
		List<List<String>> rows = parseCsv(decode(content));
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("A planilha está vazia");
		}
		Map<String, Integer> columns = columns(rows.get(0));
		if (!columns.containsKey("name") || !columns.containsKey("price")) {
			throw new IllegalArgumentException("A primeira linha da planilha precisa ter os títulos \"Nome\" e \"Preço\" (e, se quiser, Categoria, Descrição, Unidade, Disponível, Destaque)");
		}
		List<PriceListRequest.ItemRequest> items = new ArrayList<>();
		if (!replace) {
			list.getItems().forEach(item -> items.add(new PriceListRequest.ItemRequest(item.id(), item.category(), item.name(), item.description(),
					item.price(), item.unit(), item.available(), item.highlight())));
		}
		List<String> skipped = new ArrayList<>();
		int imported = 0;
		for (int line = 1; line < rows.size(); line++) {
			List<String> row = rows.get(line);
			if (row.stream().allMatch(String::isBlank)) {
				continue;
			}
			String name = cell(row, columns, "name");
			BigDecimal price = parsePrice(cell(row, columns, "price"));
			if (name.isBlank()) {
				skipped.add("Linha " + (line + 1) + ": sem nome");
			} else if (price == null) {
				skipped.add("Linha " + (line + 1) + " (" + name + "): preço ilegível");
			} else if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
				skipped.add("Linha " + (line + 1) + " (" + name + "): preço fora do limite");
			} else {
				items.add(new PriceListRequest.ItemRequest(null, cell(row, columns, "category"), name.length() > 80 ? name.substring(0, 80) : name,
						cell(row, columns, "description"), price, cell(row, columns, "unit"),
						columns.containsKey("available") ? yes(cell(row, columns, "available"), true) : Boolean.TRUE,
						columns.containsKey("highlight") && yes(cell(row, columns, "highlight"), false)));
				imported++;
			}
		}
		if (items.size() > MAX_ITEMS) {
			throw new IllegalArgumentException("A tabela ficaria com " + items.size() + " itens; o limite é " + MAX_ITEMS);
		}
		List<PriceItem> normalized = normalizeItems(items);
		list.setItems(normalized);
		Set<String> kept = new LinkedHashSet<>();
		normalized.forEach(item -> kept.add(item.id()));
		list.setRules(list.getRules().stream().map(rule -> prune(rule, kept)).filter(rule -> rule != null).toList());
		PriceList saved = repository.save(list);
		notifyTvs(id);
		return new Imported(saved, imported, skipped);
	}

	/** A variação sem os itens que saíram da tabela; some se nada sobrou para ela mudar. */
	private static PriceRule prune(PriceRule rule, Set<String> kept) {
		List<String> itemIds = rule.itemIds() == null ? List.of() : rule.itemIds().stream().filter(kept::contains).toList();
		boolean scoped = rule.itemIds() != null && !rule.itemIds().isEmpty();
		if (scoped && itemIds.isEmpty()) {
			return null;
		}
		Map<String, BigDecimal> fixed = new LinkedHashMap<>();
		if (rule.itemPrices() != null) {
			rule.itemPrices().forEach((key, value) -> {
				if (kept.contains(key)) {
					fixed.put(key, value);
				}
			});
		}
		if ((rule.percent() == null || rule.percent().signum() == 0) && fixed.isEmpty()) {
			return null;
		}
		return new PriceRule(rule.name(), rule.days(), rule.start(), rule.end(), rule.percent(), itemIds, fixed);
	}

	private static String decode(byte[] content) {
		byte[] data = content;
		if (data.length >= 3 && (data[0] & 0xff) == 0xEF && (data[1] & 0xff) == 0xBB && (data[2] & 0xff) == 0xBF) {
			data = java.util.Arrays.copyOfRange(data, 3, data.length);
		}
		try {
			return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();
		} catch (java.nio.charset.CharacterCodingException e) {
			// Excel em português salva "CSV" em Windows-1252
			return new String(data, Charset.forName("windows-1252"));
		}
	}

	/** Linhas e células de um CSV com aspas ("" dentro de aspas) e separador ";" ou "," (vale o que mais aparece na primeira linha). */
	static List<List<String>> parseCsv(String text) {
		int firstLineEnd = text.indexOf('\n') < 0 ? text.length() : text.indexOf('\n');
		String head = text.substring(0, firstLineEnd);
		char separator = count(head, ';') >= count(head, ',') && count(head, ';') > 0 ? ';' : count(head, '\t') > 0 ? '\t' : ',';
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
				} else {
					cell.append(c);
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == separator) {
				row.add(cell.toString().strip());
				cell.setLength(0);
			} else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				row.add(cell.toString().strip());
				cell.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
			} else {
				cell.append(c);
			}
		}
		if (cell.length() > 0 || !row.isEmpty()) {
			row.add(cell.toString().strip());
			rows.add(row);
		}
		return rows;
	}

	private static int count(String text, char c) {
		return (int) text.chars().filter(ch -> ch == c).count();
	}

	private static Map<String, Integer> columns(List<String> header) {
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.size(); i++) {
			String key = Normalizer.normalize(header.get(i), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
			String field = switch (key) {
				case "categoria", "category", "grupo" -> "category";
				case "nome", "produto", "item", "name", "servico" -> "name";
				case "descricao", "description" -> "description";
				case "preco", "valor", "price" -> "price";
				case "unidade", "unit", "un" -> "unit";
				case "disponivel", "available", "estoque" -> "available";
				case "destaque", "highlight" -> "highlight";
				default -> null;
			};
			if (field != null) {
				columns.putIfAbsent(field, i);
			}
		}
		return columns;
	}

	private static String cell(List<String> row, Map<String, Integer> columns, String field) {
		Integer index = columns.get(field);
		return index == null || index >= row.size() ? "" : row.get(index);
	}

	/** "12,50", "12.50", "R$ 1.234,56" e "1,234.56" viram número; o resto, nulo. */
	static BigDecimal parsePrice(String raw) {
		String text = raw == null ? "" : raw.replaceAll("[^0-9,.\\-]", "");
		if (text.isEmpty() || text.equals("-")) {
			return null;
		}
		int comma = text.lastIndexOf(',');
		int dot = text.lastIndexOf('.');
		if (comma >= 0 && dot >= 0) {
			// o último separador é o decimal; o outro é de milhar
			text = comma > dot ? text.replace(".", "").replace(',', '.') : text.replace(",", "");
		} else if (comma >= 0) {
			text = text.replace(',', '.');
		}
		try {
			return PriceBoard.scale(new BigDecimal(text));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean yes(String text, boolean fallback) {
		String value = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
		if (value.isEmpty()) {
			return fallback;
		}
		return switch (value) {
			case "sim", "s", "yes", "y", "true", "1", "x", "disponivel" -> true;
			case "nao", "n", "no", "false", "0", "esgotado", "indisponivel" -> false;
			default -> fallback;
		};
	}

}
