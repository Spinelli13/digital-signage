package com.digitalsignage.backend.privacy;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * As escolhas de privacidade de um cliente (a loja, que é a controladora dos dados dos clientes dela): o canal de contato que
 * aparece no aviso das páginas públicas e por quanto tempo os nomes ficam guardados. Uma linha por cliente, criada no primeiro
 * "salvar"; enquanto não existe valem os padrões abaixo.
 */
@Entity
@Table(name = "privacy_settings")
public class PrivacySettings {

	public static final int DEFAULT_TICKET_DAYS = 30;
	public static final int DEFAULT_COUPON_NAME_DAYS = 90;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(name = "contact_name")
	private String contactName;

	@Column(name = "contact_email")
	private String contactEmail;

	@Column(name = "ticket_retention_days", nullable = false)
	private int ticketRetentionDays = DEFAULT_TICKET_DAYS;

	@Column(name = "coupon_name_retention_days", nullable = false)
	private int couponNameRetentionDays = DEFAULT_COUPON_NAME_DAYS;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Os padrões (um cliente novo ainda não tem linha gravada). */
	public PrivacySettings() {
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	public String getContactName() {
		return contactName;
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public void setContactEmail(String contactEmail) {
		this.contactEmail = contactEmail;
	}

	public int getTicketRetentionDays() {
		return ticketRetentionDays;
	}

	public void setTicketRetentionDays(int ticketRetentionDays) {
		this.ticketRetentionDays = ticketRetentionDays;
	}

	public int getCouponNameRetentionDays() {
		return couponNameRetentionDays;
	}

	public void setCouponNameRetentionDays(int couponNameRetentionDays) {
		this.couponNameRetentionDays = couponNameRetentionDays;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
