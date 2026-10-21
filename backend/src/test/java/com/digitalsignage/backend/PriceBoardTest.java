package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.digitalsignage.backend.price.PriceBoard;
import com.digitalsignage.backend.price.PriceItem;
import com.digitalsignage.backend.price.PriceList;
import com.digitalsignage.backend.price.PriceRule;

/**
 * O preço de cada item em cada instante: preço de base ou o da primeira variação por horário em vigor. Quinta-feira 05/11/2026;
 * 12:00 em São Paulo (UTC-3) = 15:00Z.
 */
class PriceBoardTest {

	private static final Instant NOON = Instant.parse("2026-11-05T15:00:00Z");
	private static final Instant SIX_PM = Instant.parse("2026-11-05T21:00:00Z");

	private static BigDecimal money(String value) {
		return new BigDecimal(value);
	}

	private static PriceItem item(String id, String name, String price) {
		return new PriceItem(id, "Bebidas", name, null, money(price), "un", true, false);
	}

	private static PriceRule rule(String name, List<Integer> days, String start, String end, String percent, List<String> itemIds, Map<String, BigDecimal> fixed) {
		return new PriceRule(name, days, start, end, money(percent), itemIds, fixed);
	}

	private static PriceList list(List<PriceItem> items, PriceRule... rules) {
		PriceList list = new PriceList("Cardápio");
		list.setItems(items);
		list.setRules(List.of(rules));
		return list;
	}

	private static PriceBoard.Line line(List<PriceBoard.Line> lines, String id) {
		return lines.stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void withoutRulesEveryItemKeepsItsBasePrice() {
		PriceList list = list(List.of(item("a", "Cerveja", "10"), item("b", "Suco", "8.5")));

		List<PriceBoard.Line> lines = PriceBoard.resolve(list, NOON);

		assertEquals(money("10.00"), line(lines, "a").price());
		assertEquals(money("8.50"), line(lines, "b").price());
		assertNull(line(lines, "a").basePrice());
		assertNull(line(lines, "a").rule());
	}

	@Test
	void aDiscountAppliesOnlyInsideTheWindowAndOnlyToItsItems() {
		PriceList list = list(List.of(item("a", "Cerveja", "10"), item("b", "Suco", "8")),
				rule("Happy hour", List.of(1, 2, 3, 4, 5), "17:00", "20:00", "-20", List.of("a"), Map.of()));

		List<PriceBoard.Line> happy = PriceBoard.resolve(list, SIX_PM);

		assertEquals(money("8.00"), line(happy, "a").price());
		assertEquals(money("10.00"), line(happy, "a").basePrice());
		assertEquals("Happy hour", line(happy, "a").rule());
		assertEquals(money("8.00"), line(happy, "b").price()); // o suco não entra na variação
		assertNull(line(happy, "b").rule());

		assertEquals(money("10.00"), line(PriceBoard.resolve(list, NOON), "a").price());
		assertEquals(money("10.00"), line(PriceBoard.resolve(list, Instant.parse("2026-11-05T23:00:00Z")), "a").price()); // 20:00: já acabou
		// sábado 18:00: o dia da semana não vale
		assertEquals(money("10.00"), line(PriceBoard.resolve(list, Instant.parse("2026-11-07T21:00:00Z")), "a").price());
	}

	@Test
	void aFixedPriceBeatsThePercentAndAnIncreaseWorks() {
		PriceList list = list(List.of(item("a", "Prato", "40"), item("b", "Sobremesa", "12")),
				rule("Executivo", List.of(), "11:00", "15:00", "10", List.of(), Map.of("a", money("35"))));

		List<PriceBoard.Line> lines = PriceBoard.resolve(list, NOON);

		assertEquals(money("35.00"), line(lines, "a").price()); // valor fixo
		assertEquals(money("13.20"), line(lines, "b").price()); // +10%
	}

	@Test
	void theFirstActiveRuleThatReachesTheItemWins() {
		PriceList list = list(List.of(item("a", "Cerveja", "10"), item("b", "Suco", "10")),
				rule("Especial", List.of(), "10:00", "14:00", "-50", List.of("a"), Map.of()),
				rule("Geral", List.of(), "10:00", "14:00", "-10", List.of(), Map.of()));

		List<PriceBoard.Line> lines = PriceBoard.resolve(list, NOON);

		assertEquals(money("5.00"), line(lines, "a").price());
		assertEquals("Especial", line(lines, "a").rule());
		assertEquals(money("9.00"), line(lines, "b").price());
		assertEquals("Geral", line(lines, "b").rule());
	}

	@Test
	void aWindowThatCrossesMidnightCountsFromTheDayItStarts() {
		PriceList list = list(List.of(item("a", "Cerveja", "10")),
				rule("Madrugada", List.of(5), "22:00", "02:00", "-30", List.of(), Map.of())); // sexta

		// sexta 23:00 SP = sábado 02:00Z; sábado 01:00 SP = 04:00Z; sábado 23:00 SP = domingo 02:00Z
		assertEquals(money("7.00"), line(PriceBoard.resolve(list, Instant.parse("2026-11-07T02:00:00Z")), "a").price());
		assertEquals(money("7.00"), line(PriceBoard.resolve(list, Instant.parse("2026-11-07T04:00:00Z")), "a").price());
		assertEquals(money("10.00"), line(PriceBoard.resolve(list, Instant.parse("2026-11-07T06:00:00Z")), "a").price()); // 03:00 SP: já acabou
		assertEquals(money("10.00"), line(PriceBoard.resolve(list, Instant.parse("2026-11-08T02:00:00Z")), "a").price()); // sábado 23:00: dia errado
	}

	@Test
	void roundingGoesToTheCentAndAnUnavailableItemStaysUnavailable() {
		PriceItem sold = new PriceItem("a", null, "Camarão", null, money("33.33"), "kg", false, true);
		PriceList list = list(List.of(sold), rule("Promo", List.of(), "00:00", null, "-15", List.of(), Map.of()));

		PriceBoard.Line line = line(PriceBoard.resolve(list, NOON), "a");

		assertEquals(money("28.33"), line.price()); // 28,3305 -> 28,33
		assertFalse(line.available());
		assertTrue(line.highlight());
	}

	@Test
	void aHundredPercentDiscountIsFreeAndNeverNegative() {
		PriceList list = list(List.of(item("a", "Brinde", "5")), rule("Grátis", List.of(), null, null, "-100", List.of(), Map.of()));

		assertEquals(money("0.00"), line(PriceBoard.resolve(list, NOON), "a").price());
	}

	@Test
	void segmentsCutWhereAPriceChangesAndMergeTheRest() {
		PriceList list = list(List.of(item("a", "Cerveja", "10")),
				rule("Happy hour", List.of(), "17:00", "20:00", "-20", List.of(), Map.of()));

		List<PriceBoard.Segment> segments = PriceBoard.segments(list, NOON, NOON.plusSeconds(24 * 3600), 60);

		assertEquals(3, segments.size());
		assertEquals(NOON.toEpochMilli(), segments.get(0).fromMs());
		assertEquals(Instant.parse("2026-11-05T20:00:00Z").toEpochMilli(), segments.get(0).toMs());
		assertNull(segments.get(0).rule());
		assertEquals("Happy hour", segments.get(1).rule());
		assertEquals(money("8.00"), segments.get(1).items().get(0).price());
		assertEquals(Instant.parse("2026-11-05T23:00:00Z").toEpochMilli(), segments.get(1).toMs());
		assertNull(segments.get(2).rule());
		assertEquals(NOON.plusSeconds(24 * 3600).toEpochMilli(), segments.get(2).toMs());
		for (int i = 1; i < segments.size(); i++) {
			assertEquals(segments.get(i - 1).toMs(), segments.get(i).fromMs());
		}
	}

	@Test
	void aListWithoutRulesIsOneSegmentAndTheLimitIsRespected() {
		PriceList plain = list(List.of(item("a", "Cerveja", "10")));
		assertEquals(1, PriceBoard.segments(plain, NOON, NOON.plusSeconds(24 * 3600), 60).size());

		PriceList busy = list(List.of(item("a", "Cerveja", "10")),
				rule("Hora a hora", List.of(), "00:00", "00:30", "-10", List.of(), Map.of()),
				rule("Meia", List.of(), "00:30", "01:00", "-20", List.of(), Map.of()));
		assertTrue(PriceBoard.segments(busy, NOON, NOON.plusSeconds(24 * 3600), 5).size() <= 5);
	}

	@Test
	void theTimezoneOfTheListDecidesWhenTheWindowStarts() {
		PriceList utc = list(List.of(item("a", "Cerveja", "10")), rule("Tarde", List.of(), "17:00", "20:00", "-20", List.of(), Map.of()));
		utc.setTimezone("UTC");

		assertEquals(money("8.00"), line(PriceBoard.resolve(utc, Instant.parse("2026-11-05T18:00:00Z")), "a").price());
		assertEquals(money("10.00"), line(PriceBoard.resolve(utc, SIX_PM.plusSeconds(3 * 3600)), "a").price());
	}

}
