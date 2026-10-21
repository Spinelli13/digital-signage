package com.digitalsignage.backend.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Quanto custa cada item de uma tabela em cada instante: o preço de base, ou o da primeira variação por horário que está valendo
 * e alcança o item. Funções puras (sem banco), testadas com datas fixas.
 *
 * <p>Janela que atravessa a meia-noite (ex.: 22:00 às 02:00): o dia da semana conta pelo dia em que ela COMEÇA, como nas campanhas.
 */
public final class PriceBoard {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/** Um item já com o preço de agora; {@code basePrice} só vem quando uma variação mudou o preço, e {@code rule} diz qual. */
	public record Line(String id, String category, String name, String description, BigDecimal price, BigDecimal basePrice, String unit,
			boolean available, boolean highlight, String rule) {
	}

	/** De {@code fromMs} (inclusive) até {@code toMs} (exclusive) os preços são estes; {@code rule} é a variação em vigor (ou nulo). */
	public record Segment(long fromMs, long toMs, String rule, List<Line> items) {
	}

	private PriceBoard() {
	}

	public static List<Line> resolve(PriceList list, Instant at) {
		return resolve(list.getItems(), list.getRules(), list.zone(), at);
	}

	public static List<Line> resolve(List<PriceItem> items, List<PriceRule> rules, ZoneId zone, Instant at) {
		List<PriceRule> active = rules.stream().filter(rule -> isActive(rule, zone, at)).toList();
		List<Line> lines = new ArrayList<>();
		for (PriceItem item : items) {
			BigDecimal base = scale(item.price());
			String appliedRule = null;
			BigDecimal price = base;
			for (PriceRule rule : active) {
				BigDecimal changed = apply(rule, item, base);
				if (changed != null) {
					price = changed;
					appliedRule = rule.name();
					break;
				}
			}
			lines.add(new Line(item.id(), item.category(), item.name(), item.description(), price,
					appliedRule != null && price.compareTo(base) != 0 ? base : null,
					item.unit(), item.available(), item.highlight(), appliedRule));
		}
		return lines;
	}

	/** O preço depois da variação, ou nulo se ela não alcança este item. */
	private static BigDecimal apply(PriceRule rule, PriceItem item, BigDecimal base) {
		boolean inScope = rule.itemIds() == null || rule.itemIds().isEmpty() || rule.itemIds().contains(item.id());
		if (!inScope) {
			return null;
		}
		if (rule.itemPrices() != null && rule.itemPrices().containsKey(item.id())) {
			return scale(rule.itemPrices().get(item.id()));
		}
		if (rule.percent() == null || rule.percent().signum() == 0) {
			return null;
		}
		BigDecimal changed = base.multiply(BigDecimal.ONE.add(rule.percent().divide(HUNDRED, 6, RoundingMode.HALF_UP)));
		return scale(changed.max(BigDecimal.ZERO));
	}

	/** Os preços dos próximos instantes, cortados onde alguma variação começa ou termina (e à meia-noite). */
	public static List<Segment> segments(PriceList list, Instant from, Instant to, int max) {
		ZoneId zone = list.zone();
		TreeSet<Instant> cuts = new TreeSet<>();
		cuts.add(from);
		LocalDate first = from.atZone(zone).toLocalDate().minusDays(1);
		LocalDate last = to.atZone(zone).toLocalDate().plusDays(1);
		for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
			addCut(cuts, day, LocalTime.MIN, zone, from, to);
			for (PriceRule rule : list.getRules()) {
				addCut(cuts, day, parse(rule.start(), LocalTime.MIN), zone, from, to);
				if (rule.end() != null && !rule.end().isBlank()) {
					addCut(cuts, day, parse(rule.end(), LocalTime.MAX), zone, from, to);
				}
			}
		}
		List<Instant> points = new ArrayList<>(cuts);
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < points.size() && segments.size() <= max; i++) {
			Instant start = points.get(i);
			Instant end = i + 1 < points.size() ? points.get(i + 1) : to;
			List<Line> lines = resolve(list, start);
			String rule = activeRuleName(list, start);
			Segment previous = segments.isEmpty() ? null : segments.get(segments.size() - 1);
			if (previous != null && Objects.equals(previous.items(), lines) && Objects.equals(previous.rule(), rule)) {
				segments.set(segments.size() - 1, new Segment(previous.fromMs(), end.toEpochMilli(), rule, lines));
			} else {
				segments.add(new Segment(start.toEpochMilli(), end.toEpochMilli(), rule, lines));
			}
		}
		return segments.size() > max ? new ArrayList<>(segments.subList(0, max)) : segments;
	}

	/** A primeira variação em vigor no instante (a que o painel mostra como "Happy hour agora"). */
	static String activeRuleName(PriceList list, Instant at) {
		return list.getRules().stream().filter(rule -> isActive(rule, list.zone(), at)).map(PriceRule::name).findFirst().orElse(null);
	}

	public static boolean isActive(PriceRule rule, ZoneId zone, Instant at) {
		ZonedDateTime local = at.atZone(zone);
		LocalTime time = local.toLocalTime();
		LocalTime start = parse(rule.start(), LocalTime.MIN);
		LocalTime end = rule.end() == null || rule.end().isBlank() ? null : parse(rule.end(), null);
		boolean crosses = end != null && end.isBefore(start);
		if (!crosses) {
			return runsOn(rule, local.getDayOfWeek()) && !time.isBefore(start) && (end == null || time.isBefore(end));
		}
		return (!time.isBefore(start) && runsOn(rule, local.getDayOfWeek()))
				|| (time.isBefore(end) && runsOn(rule, local.getDayOfWeek().minus(1)));
	}

	private static boolean runsOn(PriceRule rule, DayOfWeek day) {
		return rule.days() == null || rule.days().isEmpty() || rule.days().contains(day.getValue());
	}

	private static void addCut(TreeSet<Instant> cuts, LocalDate day, LocalTime time, ZoneId zone, Instant from, Instant to) {
		Instant at = ZonedDateTime.of(day, time.equals(LocalTime.MAX) ? LocalTime.MIN : time, zone).toInstant();
		if (at.isAfter(from) && at.isBefore(to)) {
			cuts.add(at);
		}
	}

	static LocalTime parse(String text, LocalTime fallback) {
		if (text == null || text.isBlank()) {
			return fallback;
		}
		return LocalTime.parse(text.length() == 4 ? "0" + text : text);
	}

	static BigDecimal scale(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
