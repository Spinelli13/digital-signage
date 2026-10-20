package com.digitalsignage.backend.billing;

import java.time.Instant;
import java.time.LocalDate;

public record InvoiceResponse(
		Long id,
		Long tenantId,
		String tenantName,
		String number,
		String description,
		Instant periodStart,
		Instant periodEnd,
		int amountCents,
		String currency,
		String status,
		LocalDate dueDate,
		/** Aberta e já vencida. */
		boolean overdue,
		Instant paidAt,
		String paymentMethod,
		String paymentNote,
		Instant createdAt) {

	public static InvoiceResponse of(Invoice invoice, String tenantName, LocalDate today) {
		return new InvoiceResponse(invoice.getId(), invoice.getTenantId(), tenantName, invoice.getNumber(), invoice.getDescription(),
				invoice.getPeriodStart(), invoice.getPeriodEnd(), invoice.getAmountCents(), invoice.getCurrency(), invoice.getStatus().name(),
				invoice.getDueDate(), invoice.getStatus() == InvoiceStatus.OPEN && invoice.getDueDate().isBefore(today),
				invoice.getPaidAt(), invoice.getPaymentMethod(), invoice.getPaymentNote(), invoice.getCreatedAt());
	}

}
