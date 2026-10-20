package com.digitalsignage.backend.billing;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Uma cobrança de um cliente. Não usa o isolamento automático do Hibernate porque a operação da plataforma
 * consulta as faturas de todos os clientes; quem lê as do próprio cliente passa o cliente na consulta.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(nullable = false, unique = true)
	private String number;

	@Column(nullable = false)
	private String description;

	@Column(name = "period_start")
	private Instant periodStart;

	@Column(name = "period_end")
	private Instant periodEnd;

	@Column(name = "amount_cents", nullable = false)
	private int amountCents;

	@Column(nullable = false)
	private String currency = "BRL";

	@Column(nullable = false)
	private String status = InvoiceStatus.OPEN.name();

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "payment_method")
	private String paymentMethod;

	@Column(name = "payment_note")
	private String paymentNote;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Invoice() {
	}

	public Invoice(Long tenantId, String number, String description, Instant periodStart, Instant periodEnd, int amountCents, String currency, LocalDate dueDate) {
		this.tenantId = tenantId;
		this.number = number;
		this.description = description;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.amountCents = amountCents;
		this.currency = currency;
		this.dueDate = dueDate;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public void markPaid(Instant at, String method, String note) {
		this.status = InvoiceStatus.PAID.name();
		this.paidAt = at;
		this.paymentMethod = method;
		this.paymentNote = note;
	}

	public void voidIt() {
		this.status = InvoiceStatus.VOID.name();
	}

	public Long getId() {
		return id;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public String getNumber() {
		return number;
	}

	public String getDescription() {
		return description;
	}

	public Instant getPeriodStart() {
		return periodStart;
	}

	public Instant getPeriodEnd() {
		return periodEnd;
	}

	public int getAmountCents() {
		return amountCents;
	}

	public String getCurrency() {
		return currency;
	}

	public InvoiceStatus getStatus() {
		return InvoiceStatus.valueOf(status);
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public String getPaymentNote() {
		return paymentNote;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
