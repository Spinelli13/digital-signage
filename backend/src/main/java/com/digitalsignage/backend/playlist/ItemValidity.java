package com.digitalsignage.backend.playlist;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.digitalsignage.backend.campaign.Campaign;

/**
 * A validade de um item da playlist (datas e dias da semana), sem campanha. Vale o dia inteiro no fuso das campanhas (São Paulo):
 * não há hora, então a TV só precisa conferir de novo na virada do dia. "Hoje" é sempre a data de lá, nunca a do servidor.
 */
public final class ItemValidity {

	/** O fuso em que "hoje" é contado (o mesmo padrão das campanhas e dos relatórios). */
	public static final ZoneId ZONE = ZoneId.of(Campaign.DEFAULT_TIMEZONE);

	private ItemValidity() {
	}

	/** A data de hoje no fuso da validade. */
	public static LocalDate today(Clock clock) {
		// pelo instante, não pelo fuso do relógio: vale para qualquer relógio injetado (o dos testes ignora withZone)
		return LocalDate.ofInstant(clock.instant(), ZONE);
	}

	/** "2,4,6" vira {terça, quinta, sábado}; nulo ou vazio = todos os dias. Valores inválidos são ignorados. */
	public static Set<DayOfWeek> parseDays(String text) {
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		if (text == null || text.isBlank()) {
			return EnumSet.allOf(DayOfWeek.class);
		}
		for (String part : text.split(",")) {
			try {
				days.add(DayOfWeek.of(Integer.parseInt(part.trim())));
			} catch (RuntimeException e) {
				// número fora de 1..7 ou texto: ignora (a API já validou na entrada)
			}
		}
		return days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : days;
	}

	/** Os dias como a API devolve (1 a 7, em ordem); nulo quando o item vale todos os dias. */
	public static List<Integer> daysOf(String text) {
		Set<DayOfWeek> days = parseDays(text);
		if (days.size() == 7) {
			return null;
		}
		List<Integer> numbers = new ArrayList<>();
		new TreeSet<>(days).forEach(day -> numbers.add(day.getValue()));
		return numbers;
	}

	/**
	 * Valida e guarda os dias: de 1 a 7, sem repetir. Todos os sete (ou nenhum) viram nulo, "vale todos os dias". Um número fora do
	 * intervalo é erro de quem mandou, não algo a ignorar em silêncio.
	 */
	public static String formatDays(Collection<Integer> days) {
		if (days == null || days.isEmpty()) {
			return null;
		}
		TreeSet<Integer> sorted = new TreeSet<>();
		for (Integer day : days) {
			if (day == null || day < 1 || day > 7) {
				throw new IllegalArgumentException("Os dias da semana vão de 1 (segunda) a 7 (domingo)");
			}
			sorted.add(day);
		}
		if (sorted.size() == 7) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (Integer day : sorted) {
			text.append(text.isEmpty() ? "" : ",").append(day);
		}
		return text.toString();
	}
}
