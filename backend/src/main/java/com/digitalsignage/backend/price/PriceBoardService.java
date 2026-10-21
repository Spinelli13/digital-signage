package com.digitalsignage.backend.price;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os dados de um painel de preços que a TV (e a pré-visualização do painel) desenham: os preços das próximas 24 horas, em
 * intervalos. Assim, mesmo sem internet, a TV troca para o preço do "happy hour" na hora certa: ela só olha o relógio.
 */
@Service
public class PriceBoardService {

	static final Duration WINDOW = Duration.ofHours(24);
	static final int MAX_SEGMENTS = 60;

	private final PriceListRepository repository;
	private final Clock clock;

	public PriceBoardService(PriceListRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** Lança {@link java.util.NoSuchElementException} se a tabela escolhida no widget não existe mais. */
	@Transactional(readOnly = true)
	public Map<String, Object> dataFor(Map<String, Object> widgetSettings) {
		Object id = widgetSettings.get("priceListId");
		long listId = id instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(id));
		PriceList list = repository.findById(listId).orElseThrow(() -> new java.util.NoSuchElementException("Tabela de preços não encontrada: " + listId));
		return dataFor(list);
	}

	public Map<String, Object> dataFor(PriceList list) {
		Instant now = clock.instant();
		List<PriceBoard.Segment> segments = PriceBoard.segments(list, now, now.plus(WINDOW), MAX_SEGMENTS);
		Map<String, Object> listInfo = new LinkedHashMap<>();
		listInfo.put("id", list.getId());
		listInfo.put("name", list.getName());
		listInfo.put("currency", list.getCurrency());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("list", listInfo);
		data.put("timezone", list.getTimezone());
		data.put("segments", segments);
		data.put("fetchedAtMs", now.toEpochMilli());
		return data;
	}

}
