package com.digitalsignage.backend.price;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** O que o painel manda para criar ou trocar uma tabela de preços (os limites finos são conferidos em {@link PriceListService}). */
public record PriceListRequest(
		@NotBlank @Size(max = 80) String name,
		@Size(max = 3) String currency,
		@Size(max = 60) String timezone,
		@Valid @Size(max = 300) List<ItemRequest> items,
		@Valid @Size(max = 20) List<RuleRequest> rules) {

	public record ItemRequest(
			@Size(max = 40) String id,
			@Size(max = 40) String category,
			@NotBlank @Size(max = 80) String name,
			@Size(max = 160) String description,
			@NotNull BigDecimal price,
			@Size(max = 20) String unit,
			Boolean available,
			Boolean highlight) {
	}

	public record RuleRequest(
			@NotBlank @Size(max = 60) String name,
			List<Integer> days,
			@Size(max = 5) String start,
			@Size(max = 5) String end,
			BigDecimal percent,
			List<String> itemIds,
			Map<String, BigDecimal> itemPrices) {
	}

}
