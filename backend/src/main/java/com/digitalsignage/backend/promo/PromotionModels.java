package com.digitalsignage.backend.promo;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Os formatos de entrada e saída dos cupons para os clientes da loja (painel e página pública). */
public final class PromotionModels {

	private PromotionModels() {
	}

	public record PromotionRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 500) String description,
			Instant validFrom,
			Instant validUntil,
			@Min(1) Integer claimDays,
			@Min(1) Integer maxClaims,
			Boolean active) {
	}

	/** {@code state}: ACTIVE, SCHEDULED (ainda não começou), ENDED, EXHAUSTED (os cupons acabaram) ou OFF. O segredo só vai para quem edita. */
	public record PromotionResponse(
			Long id, String name, String description, Instant validFrom, Instant validUntil, Integer claimDays, Integer maxClaims,
			int claimedCount, long redeemedCount, boolean active, String state, String publicToken, Instant createdAt) {

		PromotionResponse withoutToken() {
			return new PromotionResponse(id, name, description, validFrom, validUntil, claimDays, maxClaims, claimedCount, redeemedCount, active, state, null, createdAt);
		}
	}

	/** {@code status}: ISSUED (valendo), REDEEMED ou EXPIRED. */
	public record CouponResponse(
			Long id, String code, String status, String claimantName, Instant claimedAt, Instant expiresAt, Instant redeemedAt, String redeemedBy) {
	}

	public record RedeemRequest(@NotBlank @Size(max = 40) String code) {
	}

	/** O que o caixa vê ao conferir um código antes de dar baixa. */
	public record CouponLookup(
			String code, String status, String promotionName, String promotionDescription, String claimantName,
			Instant claimedAt, Instant expiresAt, Instant redeemedAt) {
	}

	/** O que o consumidor vê ao escanear o QR Code da promoção; {@code notice} é o aviso de privacidade do nome. */
	public record PublicPromotion(String name, String description, boolean open, Instant validUntil, Integer claimDays,
			com.digitalsignage.backend.privacy.PrivacyNotice.Info notice) {
	}

	/** O cupom do consumidor: o código para mostrar no caixa. */
	public record PublicCoupon(String code, String status, String promotionName, String promotionDescription, Instant expiresAt, Instant redeemedAt, String token) {
	}

	/** {@code consent}: a pessoa concordou com o aviso de privacidade (obrigatório para o nome ser guardado). */
	public record ClaimRequest(@Size(max = 60) String customerName, Boolean consent) {
	}

}
