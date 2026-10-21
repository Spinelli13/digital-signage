package com.digitalsignage.backend.price;

import java.math.BigDecimal;

/**
 * Um produto ou serviço da tabela. O {@code id} é só um identificador estável dentro da tabela (as regras de preço citam
 * itens por ele); quem edita a tabela nunca o vê.
 */
public record PriceItem(
		String id,
		String category,
		String name,
		String description,
		BigDecimal price,
		String unit,
		boolean available,
		boolean highlight) {
}
