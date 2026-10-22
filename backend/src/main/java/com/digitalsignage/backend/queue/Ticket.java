package com.digitalsignage.backend.queue;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma senha: de uma fila, numerada por dia, com o caminho que percorreu (espera, chamada, atendimento). */
@Entity
@Table(name = "tickets")
public class Ticket {

	/** WAITING: na fila; CALLING: chamada no guichê; SERVING: em atendimento; o resto são finais. */
	public enum Status {
		WAITING, CALLING, SERVING, DONE, NO_SHOW, CANCELED;

		public boolean isOpen() {
			return this == WAITING || this == CALLING || this == SERVING;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(name = "queue_id", nullable = false, updatable = false)
	private Long queueId;

	@Column(name = "ticket_day", nullable = false, updatable = false)
	private LocalDate day;

	@Column(nullable = false, updatable = false)
	private int number;

	@Column(nullable = false, updatable = false)
	private String label;

	@Column(nullable = false)
	private String status = Status.WAITING.name();

	@Column(name = "counter_id")
	private Long counterId;

	@Column(name = "customer_name")
	private String customerName;

	@Column(nullable = false, updatable = false)
	private String source;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "first_called_at")
	private Instant firstCalledAt;

	@Column(name = "called_at")
	private Instant calledAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "call_count", nullable = false)
	private int callCount;

	/** Quando a pessoa concordou com o aviso de privacidade ao informar o nome pelo celular, e qual versão do aviso viu. */
	@Column(name = "consent_at")
	private Instant consentAt;

	@Column(name = "consent_notice")
	private String consentNotice;

	/** O segredo de quem pegou a senha pelo celular (acompanhar e sair da fila); nulo nas senhas emitidas no balcão. */
	@Column(name = "public_token", updatable = false)
	private String publicToken;

	protected Ticket() {
	}

	public Ticket(Long queueId, LocalDate day, int number, String label, String customerName, String source, Instant issuedAt) {
		this.queueId = queueId;
		this.day = day;
		this.number = number;
		this.label = label;
		this.customerName = customerName;
		this.source = source;
		this.issuedAt = issuedAt;
	}

	public void assignPublicToken(String token) {
		this.publicToken = token;
	}

	public String getPublicToken() {
		return publicToken;
	}

	public void recordConsent(Instant at, String noticeVersion) {
		this.consentAt = at;
		this.consentNotice = noticeVersion;
	}

	public Instant getConsentAt() {
		return consentAt;
	}

	public String getConsentNotice() {
		return consentNotice;
	}

	public void call(Long counterId, Instant now) {
		this.status = Status.CALLING.name();
		this.counterId = counterId;
		this.callCount++;
		this.calledAt = now;
		if (firstCalledAt == null) {
			firstCalledAt = now;
		}
	}

	public void start(Instant now) {
		this.status = Status.SERVING.name();
		this.startedAt = now;
	}

	public void finish(Status end, Instant now) {
		this.status = end.name();
		this.finishedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getQueueId() {
		return queueId;
	}

	public LocalDate getDay() {
		return day;
	}

	public int getNumber() {
		return number;
	}

	public String getLabel() {
		return label;
	}

	public Status getStatus() {
		return Status.valueOf(status);
	}

	public Long getCounterId() {
		return counterId;
	}

	public String getCustomerName() {
		return customerName;
	}

	public String getSource() {
		return source;
	}

	public Instant getIssuedAt() {
		return issuedAt;
	}

	public Instant getFirstCalledAt() {
		return firstCalledAt;
	}

	public Instant getCalledAt() {
		return calledAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public int getCallCount() {
		return callCount;
	}

}
