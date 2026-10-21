package com.digitalsignage.backend.price;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Uma variação de preço por horário (ex.: "Happy hour": de segunda a sexta, 17:00 às 20:00, 20% de desconto nas bebidas).
 * Dias da semana: 1 = segunda ... 7 = domingo (vazio = todos). Horas "HH:mm"; um fim menor que o início atravessa a meia-noite.
 *
 * <p>{@code itemIds} vazio = vale para todos os itens. {@code itemPrices} troca o preço de itens específicos por um valor fixo
 * (tem prioridade sobre {@code percent}). {@code percent}: +10 aumenta 10%, -20 dá 20% de desconto.
 */
public record PriceRule(
		String name,
		List<Integer> days,
		String start,
		String end,
		BigDecimal percent,
		List<String> itemIds,
		Map<String, BigDecimal> itemPrices) {
}
