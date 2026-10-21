package com.digitalsignage.backend.promo;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import com.digitalsignage.backend.queue.Tokens;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Uma promoção da loja: o consumidor escaneia o QR Code, recebe um cupom com código único e o caixa o resgata. É da loja
 * (do cliente da plataforma), diferente dos cupons de assinatura, que são da plataforma.
 */
@Entity
@Table(name = "promotions")
public class Promotion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(name = "valid_from")
	private Instant validFrom;

	@Column(name = "valid_until")
	private Instant validUntil;

	/** Quantos dias o cupom vale depois de pego. Nulo = até o fim da promoção. */
	@Column(name = "claim_days")
	private Integer claimDays;

	/** Nulo = sem limite de cupons. */
	@Column(name = "max_claims")
	private Integer maxClaims;

	@Column(name = "claimed_count", nullable = false)
	private int claimedCount;

	@Column(nullable = false)
	private boolean active = true;

	/** O segredo que vai no QR Code: quem o tem pode pegar um cupom desta promoção. */
	@Column(name = "public_token", nullable = false, updatable = false)
	private String publicToken = Tokens.random();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Promotion() {
	}

	public Promotion(String name) {
		this.name = name;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(Instant validFrom) {
		this.validFrom = validFrom;
	}

	public Instant getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(Instant validUntil) {
		this.validUntil = validUntil;
	}

	public Integer getClaimDays() {
		return claimDays;
	}

	public void setClaimDays(Integer claimDays) {
		this.claimDays = claimDays;
	}

	public Integer getMaxClaims() {
		return maxClaims;
	}

	public void setMaxClaims(Integer maxClaims) {
		this.maxClaims = maxClaims;
	}

	public int getClaimedCount() {
		return claimedCount;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getPublicToken() {
		return publicToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
