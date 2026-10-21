package com.digitalsignage.backend.price;

import java.time.Instant;
import java.util.List;

public record PriceListResponse(
		Long id,
		String name,
		String currency,
		String timezone,
		List<PriceItem> items,
		List<PriceRule> rules,
		/** Quantos widgets da biblioteca mostram esta tabela (não dá para excluir enquanto houver). */
		int widgetCount,
		Instant updatedAt) {

	public static PriceListResponse from(PriceList list, int widgetCount) {
		return new PriceListResponse(list.getId(), list.getName(), list.getCurrency(), list.getTimezone(), list.getItems(), list.getRules(),
				widgetCount, list.getUpdatedAt());
	}

}
