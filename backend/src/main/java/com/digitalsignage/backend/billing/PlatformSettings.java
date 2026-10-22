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
	private String companyName = "Vyzzu";

	@Column(name = "payment_instructions")
	private String paymentInstructions;

	@Column(name = "support_email")
	private String supportEmail;

	/** Quem responde pelo tratamento de dados (LGPD): aparece na Política de Privacidade. */
	@Column(name = "legal_name")
	private String legalName;

	@Column(name = "legal_document")
	private String legalDocument;

	@Column(name = "privacy_contact_name")
	private String privacyContactName;

	@Column(name = "privacy_contact_email")
	private String privacyContactEmail;

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

	public String getLegalName() {
		return legalName;
	}

	public void setLegalName(String legalName) {
		this.legalName = legalName;
	}

	public String getLegalDocument() {
		return legalDocument;
	}

	public void setLegalDocument(String legalDocument) {
		this.legalDocument = legalDocument;
	}

	public String getPrivacyContactName() {
		return privacyContactName;
	}

	public void setPrivacyContactName(String privacyContactName) {
		this.privacyContactName = privacyContactName;
	}

	public String getPrivacyContactEmail() {
		return privacyContactEmail;
	}

	public void setPrivacyContactEmail(String privacyContactEmail) {
		this.privacyContactEmail = privacyContactEmail;
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
