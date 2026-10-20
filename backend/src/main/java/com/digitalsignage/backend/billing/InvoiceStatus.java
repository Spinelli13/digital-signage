package com.digitalsignage.backend.billing;

public enum InvoiceStatus {
	/** A pagar. */
	OPEN,
	/** Paga. */
	PAID,
	/** Cancelada (não é mais devida). */
	VOID
}
