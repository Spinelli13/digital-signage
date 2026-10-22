package com.digitalsignage.backend.promo;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.privacy.PrivacyNotice;
import com.digitalsignage.backend.privacy.PrivacyService;
import com.digitalsignage.backend.promo.PromotionModels.CouponLookup;
import com.digitalsignage.backend.promo.PromotionModels.CouponResponse;
import com.digitalsignage.backend.promo.PromotionModels.PromotionRequest;
import com.digitalsignage.backend.promo.PromotionModels.PromotionResponse;
import com.digitalsignage.backend.promo.PromotionModels.PublicCoupon;
import com.digitalsignage.backend.promo.PromotionModels.PublicPromotion;

/**
 * Os cupons para os clientes da loja. Tudo aqui roda dentro do cliente (tenant) da requisição: o painel já vem com ele
 * definido e a área pública o define a partir do segredo do QR Code (ver {@link PublicPromotionService}).
 */
@Service
@Transactional
public class PromotionService {

	private static final int LATEST_COUPONS = 200;
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final PromotionRepository promotions;
	private final PromotionCouponRepository coupons;
	private final PrivacyService privacy;
	private final Clock clock;
	private final ZoneId zone;

	public PromotionService(PromotionRepository promotions, PromotionCouponRepository coupons, PrivacyService privacy, Clock clock,
			@Value("${app.billing.timezone:America/Sao_Paulo}") String zone) {
		this.promotions = promotions;
		this.coupons = coupons;
		this.privacy = privacy;
		this.clock = clock;
		this.zone = ZoneId.of(zone);
	}

	// ---------- o painel ----------

	@Transactional(readOnly = true)
	public List<PromotionResponse> list() {
		Instant now = clock.instant();
		Map<Long, Long> redeemed = coupons.redeemedByPromotion().stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
		return promotions.findAllByOrderByCreatedAtDescIdDesc().stream().map(promotion -> toResponse(promotion, redeemed.getOrDefault(promotion.getId(), 0L), now)).toList();
	}

	public PromotionResponse create(PromotionRequest request) {
		String name = request.name().trim();
		if (promotions.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe uma promoção chamada \"" + name + "\"");
		}
		Promotion promotion = new Promotion(name);
		apply(promotion, request);
		return toResponse(promotions.save(promotion), 0, clock.instant());
	}

	public PromotionResponse update(Long id, PromotionRequest request) {
		Promotion promotion = find(id);
		String name = request.name().trim();
		if (!name.equalsIgnoreCase(promotion.getName()) && promotions.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe uma promoção chamada \"" + name + "\"");
		}
		if (request.maxClaims() != null && request.maxClaims() < promotion.getClaimedCount()) {
			throw new ConflictException("Já foram pegos " + promotion.getClaimedCount() + " cupom(ns): o limite não pode ser menor que isso.");
		}
		promotion.setName(name);
		apply(promotion, request);
		return toResponse(promotions.save(promotion), coupons.redeemedOf(id), clock.instant());
	}

	public void delete(Long id) {
		Promotion promotion = find(id);
		if (promotion.getClaimedCount() > 0) {
			throw new ConflictException("Esta promoção já teve cupons pegos e não pode ser excluída. Desligue-a para ninguém mais pegar.");
		}
		promotions.delete(promotion);
	}

	@Transactional(readOnly = true)
	public List<CouponResponse> couponsOf(Long promotionId) {
		find(promotionId);
		Instant now = clock.instant();
		return coupons.latest(promotionId, PageRequest.of(0, LATEST_COUPONS)).stream().map(coupon -> toResponse(coupon, now)).toList();
	}

	/** O caixa digita o código: mostra o que ele é, sem dar baixa. */
	@Transactional(readOnly = true)
	public CouponLookup lookup(String rawCode) {
		PromotionCoupon coupon = findCoupon(rawCode);
		Promotion promotion = promotions.findById(coupon.getPromotionId()).orElseThrow();
		Instant now = clock.instant();
		return new CouponLookup(coupon.getCode(), coupon.statusAt(now), promotion.getName(), promotion.getDescription(), coupon.getClaimantName(),
				coupon.getClaimedAt(), coupon.getExpiresAt(), coupon.getRedeemedAt());
	}

	/** O caixa dá baixa: o cupom só vale uma vez (a baixa é atômica, dois caixas não resgatam o mesmo). */
	public CouponLookup redeem(String rawCode, String by) {
		PromotionCoupon coupon = findCoupon(rawCode);
		Instant now = clock.instant();
		switch (coupon.statusAt(now)) {
			case PromotionCoupon.REDEEMED -> throw new ConflictException("Este cupom já foi usado em " + when(coupon.getRedeemedAt()) + ".");
			case "EXPIRED" -> throw new ConflictException("Este cupom venceu em " + when(coupon.getExpiresAt()) + ".");
			default -> {
			}
		}
		if (coupons.redeem(coupon.getId(), now, by) == 0) {
			throw new ConflictException("Este cupom acabou de ser usado por outro caixa.");
		}
		return lookup(rawCode);
	}

	// ---------- a área pública ----------

	@Transactional(readOnly = true)
	public PublicPromotion publicView(Long promotionId) {
		Promotion promotion = find(promotionId);
		return new PublicPromotion(promotion.getName(), promotion.getDescription(), openAt(promotion, clock.instant()), promotion.getValidUntil(), promotion.getClaimDays(),
				privacy.couponNotice());
	}

	/** O mesmo aparelho recebe sempre o mesmo cupom; outro aparelho recebe um novo, enquanto houver. */
	public PublicCoupon claim(Long promotionId, String customerName, boolean consented, String clientId, String origin) {
		Promotion promotion = find(promotionId);
		Instant now = clock.instant();
		if (clientId != null) {
			Optional<PromotionCoupon> existing = coupons.findByPromotionIdAndClientId(promotionId, clientId);
			if (existing.isPresent()) {
				return toPublic(existing.get(), promotion, now);
			}
		}
		if (!openAt(promotion, now)) {
			throw new ConflictException(promotion.isActive() ? "Os cupons desta promoção acabaram." : "Esta promoção não está aberta agora.");
		}
		String name = customerName == null || customerName.isBlank() ? null : customerName.trim();
		if (name != null && !consented) {
			throw new IllegalArgumentException("Para informar o nome, é preciso concordar com o aviso de privacidade");
		}
		if (promotions.claimSlot(promotionId) == 0) {
			throw new ConflictException("Os cupons desta promoção acabaram.");
		}
		// o prazo de guarda do nome é do cliente (Privacidade); a limpeza horária também apaga, mesmo sem ninguém pegar cupom
		coupons.forgetNamesBefore(now.minus(Duration.ofDays(privacy.couponNameRetentionDays())));
		PromotionCoupon fresh = new PromotionCoupon(promotionId, freshCode(), name, clientId, now, expiryOf(promotion, now));
		fresh.setOrigin(ScanOrigin.clean(origin));
		if (name != null) {
			fresh.recordConsent(now, PrivacyNotice.VERSION);
		}
		PromotionCoupon coupon = coupons.saveAndFlush(fresh);
		return toPublic(coupon, promotion, now);
	}

	/** Para quando dois pedidos do mesmo aparelho chegam juntos: o segundo encontra o cupom que o primeiro criou. */
	@Transactional(readOnly = true)
	public Optional<PublicCoupon> existing(Long promotionId, String clientId) {
		if (clientId == null) {
			return Optional.empty();
		}
		Promotion promotion = find(promotionId);
		Instant now = clock.instant();
		return coupons.findByPromotionIdAndClientId(promotionId, clientId).map(coupon -> toPublic(coupon, promotion, now));
	}

	@Transactional(readOnly = true)
	public PublicCoupon publicCoupon(Long couponId) {
		PromotionCoupon coupon = coupons.findById(couponId).orElseThrow(() -> new NoSuchElementException("Este cupom não foi encontrado."));
		Promotion promotion = promotions.findById(coupon.getPromotionId()).orElseThrow();
		return toPublic(coupon, promotion, clock.instant());
	}

	// ---------- regras ----------

	/** Aberta a quem escaneia: ligada, dentro das datas e com cupons sobrando. */
	boolean openAt(Promotion promotion, Instant now) {
		return promotion.isActive()
				&& (promotion.getValidFrom() == null || !now.isBefore(promotion.getValidFrom()))
				&& (promotion.getValidUntil() == null || now.isBefore(promotion.getValidUntil()))
				&& (promotion.getMaxClaims() == null || promotion.getClaimedCount() < promotion.getMaxClaims());
	}

	/** O cupom vale pelos dias da promoção contados do dia em que foi pego, mas nunca depois do fim da promoção. */
	private static Instant expiryOf(Promotion promotion, Instant now) {
		Instant byDays = promotion.getClaimDays() == null ? null : now.plus(Duration.ofDays(promotion.getClaimDays()));
		Instant end = promotion.getValidUntil();
		if (byDays == null) {
			return end;
		}
		return end == null || byDays.isBefore(end) ? byDays : end;
	}

	private String freshCode() {
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder code = new StringBuilder(9);
			for (int i = 0; i < 8; i++) {
				if (i == 4) {
					code.append('-');
				}
				code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
			}
			if (!coupons.existsByCode(code.toString())) {
				return code.toString();
			}
		}
		throw new IllegalStateException("Não foi possível gerar um código novo");
	}

	/** "abcd 2345", "ABCD-2345" e "abcd2345" são o mesmo código. */
	static String canonical(String raw) {
		String letters = raw == null ? "" : raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
		if (letters.length() != 8) {
			throw new IllegalArgumentException("O código do cupom tem 8 letras e números (ex.: ABCD-2345).");
		}
		return letters.substring(0, 4) + "-" + letters.substring(4);
	}

	private PromotionCoupon findCoupon(String rawCode) {
		return coupons.findByCode(canonical(rawCode)).orElseThrow(() -> new NoSuchElementException("Cupom não encontrado: confira o código."));
	}

	private Promotion find(Long id) {
		return promotions.findById(id).orElseThrow(() -> new NoSuchElementException("Promoção não encontrada: " + id));
	}

	private void apply(Promotion promotion, PromotionRequest request) {
		if (request.validFrom() != null && request.validUntil() != null && !request.validUntil().isAfter(request.validFrom())) {
			throw new IllegalArgumentException("O fim da promoção precisa ser depois do início");
		}
		promotion.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
		promotion.setValidFrom(request.validFrom());
		promotion.setValidUntil(request.validUntil());
		promotion.setClaimDays(request.claimDays());
		promotion.setMaxClaims(request.maxClaims());
		if (request.active() != null) {
			promotion.setActive(request.active());
		}
	}

	private String when(Instant instant) {
		return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(instant.atZone(zone));
	}

	private String stateOf(Promotion promotion, Instant now) {
		if (!promotion.isActive()) {
			return "OFF";
		}
		if (promotion.getValidUntil() != null && !now.isBefore(promotion.getValidUntil())) {
			return "ENDED";
		}
		if (promotion.getMaxClaims() != null && promotion.getClaimedCount() >= promotion.getMaxClaims()) {
			return "EXHAUSTED";
		}
		if (promotion.getValidFrom() != null && now.isBefore(promotion.getValidFrom())) {
			return "SCHEDULED";
		}
		return "ACTIVE";
	}

	private PromotionResponse toResponse(Promotion promotion, long redeemed, Instant now) {
		return new PromotionResponse(promotion.getId(), promotion.getName(), promotion.getDescription(), promotion.getValidFrom(), promotion.getValidUntil(),
				promotion.getClaimDays(), promotion.getMaxClaims(), promotion.getClaimedCount(), redeemed, promotion.isActive(), stateOf(promotion, now),
				promotion.getPublicToken(), promotion.getCreatedAt());
	}

	private CouponResponse toResponse(PromotionCoupon coupon, Instant now) {
		return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.statusAt(now), coupon.getClaimantName(), coupon.getClaimedAt(), coupon.getExpiresAt(),
				coupon.getRedeemedAt(), coupon.getRedeemedBy());
	}

	private PublicCoupon toPublic(PromotionCoupon coupon, Promotion promotion, Instant now) {
		return new PublicCoupon(coupon.getCode(), coupon.statusAt(now), promotion.getName(), promotion.getDescription(), coupon.getExpiresAt(), coupon.getRedeemedAt(),
				coupon.getPublicToken());
	}

}
