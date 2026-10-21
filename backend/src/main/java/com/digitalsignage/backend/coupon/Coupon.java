package com.digitalsignage.backend.coupon;

import java.time.Instant;

import com.digitalsignage.backend.plan.Plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Um cupom de assinatura, criado pelo gestor da plataforma. Não pertence a nenhum cliente (não usa o isolamento
 * automático): qualquer cliente pode resgatá-lo, uma vez, dentro das regras de validade e de usos.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String code;

	private String description;

	@Column(nullable = false)
	private String kind;

	/** Porcentagem (1 a 100), centavos ou dias, conforme o tipo. */
	@Column(nullable = false)
	private int value;

	/** Descontos: em quantas faturas vale. */
	@Column(name = "duration_months")
	private Integer durationMonths;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "upgrade_plan_id")
	private Plan upgradePlan;

	@Column(name = "valid_from")
	private Instant validFrom;

	@Column(name = "valid_until")
	private Instant validUntil;

	/** Nulo = sem limite de usos. */
	@Column(name = "max_redemptions")
	private Integer maxRedemptions;

	@Column(name = "redeemed_count", nullable = false)
	private int redeemedCount;

	@Column(nullable = false)
	private boolean active = true;

	private String batch;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Coupon() {
	}

	public Coupon(String code, CouponKind kind, int value) {
		this.code = code;
		this.kind = kind.name();
		this.value = value;
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

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public CouponKind getKind() {
		return CouponKind.valueOf(kind);
	}

	public void setKind(CouponKind kind) {
		this.kind = kind.name();
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public Integer getDurationMonths() {
		return durationMonths;
	}

	public void setDurationMonths(Integer durationMonths) {
		this.durationMonths = durationMonths;
	}

	public Plan getUpgradePlan() {
		return upgradePlan;
	}

	public void setUpgradePlan(Plan upgradePlan) {
		this.upgradePlan = upgradePlan;
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

	public Integer getMaxRedemptions() {
		return maxRedemptions;
	}

	public void setMaxRedemptions(Integer maxRedemptions) {
		this.maxRedemptions = maxRedemptions;
	}

	public int getRedeemedCount() {
		return redeemedCount;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getBatch() {
		return batch;
	}

	public void setBatch(String batch) {
		this.batch = batch;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
