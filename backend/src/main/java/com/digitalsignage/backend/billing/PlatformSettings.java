package com.digitalsignage.backend.billing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Configurações da operação da plataforma (uma única linha). */
@Entity
@Table(name = "platform_settings")
public class PlatformSettings {

	public static final short ID = 1;

	@Id
	private Short id = ID;

	@Column(name = "company_name", nullable = false)
	private String companyName = "Smartplay";

	@Column(name = "payment_instructions")
	private String paymentInstructions;

	@Column(name = "support_email")
	private String supportEmail;

	@Column(name = "trial_days", nullable = false)
	private int trialDays = 14;

	@Column(name = "grace_days", nullable = false)
	private int graceDays = 7;

	@Column(name = "invoice_lead_days", nullable = false)
	private int invoiceLeadDays = 5;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PlatformSettings() {
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public String getPaymentInstructions() {
		return paymentInstructions;
	}

	public void setPaymentInstructions(String paymentInstructions) {
		this.paymentInstructions = paymentInstructions;
	}

	public String getSupportEmail() {
		return supportEmail;
	}

	public void setSupportEmail(String supportEmail) {
		this.supportEmail = supportEmail;
	}

	public int getTrialDays() {
		return trialDays;
	}

	public void setTrialDays(int trialDays) {
		this.trialDays = trialDays;
	}

	public int getGraceDays() {
		return graceDays;
	}

	public void setGraceDays(int graceDays) {
		this.graceDays = graceDays;
	}

	public int getInvoiceLeadDays() {
		return invoiceLeadDays;
	}

	public void setInvoiceLeadDays(int invoiceLeadDays) {
		this.invoiceLeadDays = invoiceLeadDays;
	}

}
