package com.digitalsignage.backend.tenant;

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

/** Um cliente da plataforma: dono de TVs, mídias, playlists e usuários, com um plano e uma situação de assinatura. */
@Entity
@Table(name = "tenants")
public class Tenant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	private Plan plan;

	/** Plano liberado por um cupom, valendo até {@code bonusPlanUntil}. O plano cobrado continua sendo {@link #plan}. */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "bonus_plan_id")
	private Plan bonusPlan;

	@Column(name = "bonus_plan_until")
	private Instant bonusPlanUntil;

	@Column(nullable = false)
	private String status = TenantStatus.TRIAL.name();

	@Column(name = "trial_ends_at")
	private Instant trialEndsAt;

	@Column(name = "current_period_end")
	private Instant currentPeriodEnd;

	@Column(name = "billing_email")
	private String billingEmail;

	private String document;

	private String notes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Tenant() {
	}

	public Tenant(String name, String slug, Plan plan, TenantStatus status) {
		this.name = name;
		this.slug = slug;
		this.plan = plan;
		this.status = status.name();
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

	public String getSlug() {
		return slug;
	}

	public Plan getPlan() {
		return plan;
	}

	public void setPlan(Plan plan) {
		this.plan = plan;
	}

	public Plan getBonusPlan() {
		return bonusPlan;
	}

	public Instant getBonusPlanUntil() {
		return bonusPlanUntil;
	}

	public void setBonus(Plan bonusPlan, Instant until) {
		this.bonusPlan = bonusPlan;
		this.bonusPlanUntil = until;
	}

	public boolean hasBonusAt(Instant now) {
		return bonusPlan != null && bonusPlanUntil != null && bonusPlanUntil.isAfter(now);
	}

	/** O plano que vale agora para limites e recursos: o liberado por cupom (se ainda vale) ou o contratado. A cobrança usa sempre {@link #getPlan()}. */
	public Plan getEffectivePlan(Instant now) {
		return hasBonusAt(now) ? bonusPlan : plan;
	}

	public TenantStatus getStatus() {
		return TenantStatus.valueOf(status);
	}

	public void setStatus(TenantStatus status) {
		this.status = status.name();
	}

	public Instant getTrialEndsAt() {
		return trialEndsAt;
	}

	public void setTrialEndsAt(Instant trialEndsAt) {
		this.trialEndsAt = trialEndsAt;
	}

	public Instant getCurrentPeriodEnd() {
		return currentPeriodEnd;
	}

	public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
		this.currentPeriodEnd = currentPeriodEnd;
	}

	public String getBillingEmail() {
		return billingEmail;
	}

	public void setBillingEmail(String billingEmail) {
		this.billingEmail = billingEmail;
	}

	public String getDocument() {
		return document;
	}

	public void setDocument(String document) {
		this.document = document;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
