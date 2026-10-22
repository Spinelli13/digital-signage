package com.digitalsignage.backend.privacy;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * O registro de um pedido de titular atendido pelo gestor (cópia ou exclusão dos dados de uma pessoa): quando, quem fez, o que
 * foi encontrado e uma observação (por exemplo, o protocolo). Nunca guarda o nome buscado, que é dado pessoal.
 */
@Entity
@Table(name = "privacy_requests")
public class PrivacyRequestLog {

	public enum Kind {
		EXPORT, ERASE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(nullable = false, updatable = false)
	private String kind;

	@Column(nullable = false, updatable = false)
	private int tickets;

	@Column(nullable = false, updatable = false)
	private int coupons;

	@Column(nullable = false, updatable = false)
	private int users;

	@Column(updatable = false)
	private String note;

	@Column(name = "performed_by", nullable = false, updatable = false)
	private String performedBy;

	@Column(name = "performed_at", nullable = false, updatable = false)
	private Instant performedAt;

	protected PrivacyRequestLog() {
	}

	public PrivacyRequestLog(Kind kind, int tickets, int coupons, int users, String note, String performedBy, Instant performedAt) {
		this.kind = kind.name();
		this.tickets = tickets;
		this.coupons = coupons;
		this.users = users;
		this.note = note;
		this.performedBy = performedBy;
		this.performedAt = performedAt;
	}

	public Long getId() {
		return id;
	}

	public Kind getKind() {
		return Kind.valueOf(kind);
	}

	public int getTickets() {
		return tickets;
	}

	public int getCoupons() {
		return coupons;
	}

	public int getUsers() {
		return users;
	}

	public String getNote() {
		return note;
	}

	public String getPerformedBy() {
		return performedBy;
	}

	public Instant getPerformedAt() {
		return performedAt;
	}

}
