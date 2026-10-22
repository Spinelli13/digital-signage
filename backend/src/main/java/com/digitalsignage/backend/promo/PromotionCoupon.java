package com.digitalsignage.backend.promo;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import com.digitalsignage.backend.queue.Tokens;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um cupom pego por um consumidor: o código que o caixa confere e o segredo com que a pessoa acompanha o próprio cupom. */
@Entity
@Table(name = "promotion_coupons")
public class PromotionCoupon {

	public static final String ISSUED = "ISSUED";
	public static final String REDEEMED = "REDEEMED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(name = "promotion_id", nullable = false, updatable = false)
	private Long promotionId;

	@Column(nullable = false, updatable = false)
	private String code;

	@Column(nullable = false)
	private String status = ISSUED;

	/** Dado pessoal opcional: apagado depois de alguns dias. */
	@Column(name = "claimant_name")
	private String claimantName;

	/** O aparelho que pegou (código aleatório guardado pela página). Some quando a pessoa pede a exclusão dos dados dela. */
	@Column(name = "client_id")
	private String clientId;

	/** Quando a pessoa concordou com o aviso de privacidade ao informar o nome, e qual versão do aviso viu. */
	@Column(name = "consent_at")
	private Instant consentAt;

	@Column(name = "consent_notice")
	private String consentNotice;

	@Column(name = "public_token", nullable = false, updatable = false)
	private String publicToken = Tokens.random();

	/** De que QR Code veio o cupom ("tv-12", "balcao"...); nulo = sem origem (ver {@link ScanOrigin}). */
	private String origin;

	@Column(name = "claimed_at", nullable = false, updatable = false)
	private Instant claimedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "redeemed_at")
	private Instant redeemedAt;

	@Column(name = "redeemed_by")
	private String redeemedBy;

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

	protected PromotionCoupon() {
	}

	public void setOrigin(String origin) {
		this.origin = origin == null || origin.isEmpty() ? null : origin;
	}

	public String getOrigin() {
		return origin;
	}

	public PromotionCoupon(Long promotionId, String code, String claimantName, String clientId, Instant claimedAt, Instant expiresAt) {
		this.promotionId = promotionId;
		this.code = code;
		this.claimantName = claimantName;
		this.clientId = clientId;
		this.claimedAt = claimedAt;
		this.expiresAt = expiresAt;
	}

	/** ISSUED (valendo), REDEEMED (usado no caixa) ou EXPIRED (passou da validade sem ser usado). */
	public String statusAt(Instant now) {
		if (REDEEMED.equals(status)) {
			return REDEEMED;
		}
		return expiresAt != null && !now.isBefore(expiresAt) ? "EXPIRED" : ISSUED;
	}

	public Long getId() {
		return id;
	}

	public Long getPromotionId() {
		return promotionId;
	}

	public String getCode() {
		return code;
	}

	public String getClaimantName() {
		return claimantName;
	}

	public String getPublicToken() {
		return publicToken;
	}

	public Instant getClaimedAt() {
		return claimedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRedeemedAt() {
		return redeemedAt;
	}

	public String getRedeemedBy() {
		return redeemedBy;
	}

}
