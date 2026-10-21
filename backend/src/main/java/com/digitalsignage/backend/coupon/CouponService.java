package com.digitalsignage.backend.coupon;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.billing.InvoiceRepository;
import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.common.RateLimitException;
import com.digitalsignage.backend.plan.Plan;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.tenant.TenantStatus;

/**
 * O resgate de cupons de assinatura. As regras ficam todas aqui, na ordem em que o cliente as sente:
 * código válido (existe, está ativo, dentro da validade e com usos sobrando) → o cliente ainda não o usou → o que
 * o cupom dá cabe na situação do cliente → um uso é reservado de forma atômica → o benefício é aplicado.
 *
 * <p>Quem erra o código demais é barrado por um tempo (sem isso dava para adivinhar cupons por força bruta).
 */
@Service
public class CouponService {

	public static final int MAX_FAILED_ATTEMPTS = 10;
	private static final Duration ATTEMPT_WINDOW = Duration.ofHours(1);
	private static final String INVALID = "Cupom inválido ou expirado.";

	/** O desconto que a próxima fatura recebe. */
	public record Discount(int cents, String code) {
	}

	/** Um cupom que o cliente resgatou, para mostrar em "Plano e cobrança". */
	public record RedeemedCoupon(
			String code, String description, String kind, int value, Integer durationMonths, Integer invoicesLeft,
			String planName, Instant redeemedAt, Instant until, boolean active) {
	}

	private final CouponRepository coupons;
	private final CouponRedemptionRepository redemptions;
	private final TenantRepository tenants;
	private final InvoiceRepository invoices;
	private final Clock clock;
	private final ZoneId zone;
	private final Map<Long, Deque<Instant>> failures = new ConcurrentHashMap<>();

	public CouponService(
			CouponRepository coupons,
			CouponRedemptionRepository redemptions,
			TenantRepository tenants,
			InvoiceRepository invoices,
			Clock clock,
			@Value("${app.billing.timezone:America/Sao_Paulo}") String zone) {
		this.coupons = coupons;
		this.redemptions = redemptions;
		this.tenants = tenants;
		this.invoices = invoices;
		this.clock = clock;
		this.zone = ZoneId.of(zone);
	}

	// ---------- resgate ----------

	@Transactional
	public RedeemedCoupon redeem(long tenantId, String userEmail, String rawCode) {
		String code = rawCode == null ? "" : rawCode.trim();
		if (code.isEmpty()) {
			throw new IllegalArgumentException("Digite o código do cupom.");
		}
		Instant now = clock.instant();
		requireNotLockedOut(tenantId, now);

		Coupon coupon = coupons.findByCodeIgnoreCase(code).filter(candidate -> usableAt(candidate, now))
				.orElseThrow(() -> failed(tenantId, now));
		Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Cliente não encontrado: " + tenantId));
		if (redemptions.existsByCouponIdAndTenantId(coupon.getId(), tenantId)) {
			throw new ConflictException("Este cupom já foi resgatado por este cliente.");
		}

		Integer invoicesLeft = null;
		switch (coupon.getKind()) {
			case PERCENT_OFF, AMOUNT_OFF -> {
				requirePaidPlan(tenant);
				if (!redemptions.findWithInvoicesLeft(tenantId).isEmpty()) {
					throw new ConflictException("Você já tem um desconto ativo. Use outro cupom de desconto quando ele acabar.");
				}
				invoicesLeft = coupon.getDurationMonths() == null ? 1 : coupon.getDurationMonths();
			}
			case FREE_DAYS -> {
				requirePaidPlan(tenant);
				extendPeriod(tenant, coupon.getValue(), now);
			}
			case PLAN_UPGRADE -> grantPlan(tenant, coupon, now);
		}

		if (coupons.claim(coupon.getId()) == 0) {
			throw new ConflictException("Este cupom já atingiu o limite de usos.");
		}
		tenants.save(tenant);
		CouponRedemption redemption = redemptions.save(new CouponRedemption(coupon.getId(), tenantId, userEmail, now, invoicesLeft));
		failures.remove(tenantId);
		return describe(redemption, coupon, tenant, now);
	}

	private boolean usableAt(Coupon coupon, Instant now) {
		return coupon.isActive()
				&& (coupon.getValidFrom() == null || !now.isBefore(coupon.getValidFrom()))
				&& (coupon.getValidUntil() == null || now.isBefore(coupon.getValidUntil()))
				&& (coupon.getMaxRedemptions() == null || coupon.getRedeemedCount() < coupon.getMaxRedemptions());
	}

	private void requirePaidPlan(Tenant tenant) {
		if (tenant.getPlan().getPriceCents() <= 0) {
			throw new ConflictException("O seu plano é gratuito: não há mensalidade para o cupom abater.");
		}
	}

	/** Dias grátis: empurra o fim do teste (ou do período pago). Quem está devendo regulariza antes. */
	private void extendPeriod(Tenant tenant, int days, Instant now) {
		if (!invoices.findOpenOf(tenant.getId()).isEmpty() || tenant.getStatus() == TenantStatus.PAST_DUE) {
			throw new ConflictException("Há uma fatura em aberto. Regularize antes de usar um cupom de dias grátis.");
		}
		if (tenant.getStatus() == TenantStatus.TRIAL) {
			if (tenant.getTrialEndsAt() == null) {
				throw new ConflictException("O teste deste cliente não tem data para acabar: não há o que estender.");
			}
			Instant base = tenant.getTrialEndsAt().isAfter(now) ? tenant.getTrialEndsAt() : now;
			tenant.setTrialEndsAt(base.plus(Duration.ofDays(days)));
			return;
		}
		Instant base = tenant.getCurrentPeriodEnd() != null && tenant.getCurrentPeriodEnd().isAfter(now) ? tenant.getCurrentPeriodEnd() : now;
		tenant.setCurrentPeriodEnd(base.plus(Duration.ofDays(days)));
	}

	private void grantPlan(Tenant tenant, Coupon coupon, Instant now) {
		Plan plan = coupon.getUpgradePlan();
		if (plan == null || !plan.isActive()) {
			throw new ConflictException("O plano deste cupom não está mais disponível.");
		}
		if (tenant.hasBonusAt(now)) {
			throw new ConflictException("Você já usa um plano liberado por cupom até " + day(tenant.getBonusPlanUntil()) + ". Use outro quando ele acabar.");
		}
		if (plan.getId().equals(tenant.getPlan().getId())) {
			throw new ConflictException("Este cupom libera o plano que você já tem.");
		}
		tenant.setBonus(plan, now.plus(Duration.ofDays(coupon.getValue())));
	}

	private String day(Instant instant) {
		return DateTimeFormatter.ofPattern("dd/MM/yyyy").format(instant.atZone(zone));
	}

	// ---------- tentativas erradas ----------

	private IllegalArgumentException failed(long tenantId, Instant now) {
		Deque<Instant> attempts = failures.computeIfAbsent(tenantId, id -> new ArrayDeque<>());
		synchronized (attempts) {
			attempts.addLast(now);
			prune(attempts, now);
		}
		return new IllegalArgumentException(INVALID);
	}

	private void requireNotLockedOut(long tenantId, Instant now) {
		Deque<Instant> attempts = failures.get(tenantId);
		if (attempts == null) {
			return;
		}
		synchronized (attempts) {
			prune(attempts, now);
			if (attempts.size() >= MAX_FAILED_ATTEMPTS) {
				throw new RateLimitException("Muitas tentativas com cupons que não existem. Tente de novo daqui a pouco.");
			}
		}
	}

	private static void prune(Deque<Instant> attempts, Instant now) {
		while (!attempts.isEmpty() && attempts.peekFirst().isBefore(now.minus(ATTEMPT_WINDOW))) {
			attempts.removeFirst();
		}
	}

	// ---------- cobrança ----------

	/**
	 * Usado pelo ciclo de cobrança ao emitir uma fatura: aplica (e gasta uma fatura de) o desconto do cliente, se houver.
	 * Roda dentro da transação do ciclo.
	 */
	public Optional<Discount> consumeDiscount(long tenantId, int priceCents) {
		for (CouponRedemption redemption : redemptions.findWithInvoicesLeft(tenantId)) {
			Optional<Coupon> found = coupons.findById(redemption.getCouponId());
			if (found.isEmpty() || !found.get().getKind().isDiscount()) {
				continue;
			}
			Coupon coupon = found.get();
			int cents = coupon.getKind() == CouponKind.PERCENT_OFF
					? (int) Math.round(priceCents * coupon.getValue() / 100.0)
					: Math.min(coupon.getValue(), priceCents);
			redemption.setInvoicesLeft(redemption.getInvoicesLeft() - 1);
			redemptions.save(redemption);
			return Optional.of(new Discount(Math.min(cents, priceCents), coupon.getCode()));
		}
		return Optional.empty();
	}

	// ---------- consulta ----------

	@Transactional(readOnly = true)
	public List<RedeemedCoupon> redeemedBy(long tenantId) {
		Instant now = clock.instant();
		Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Cliente não encontrado: " + tenantId));
		List<CouponRedemption> mine = redemptions.findByTenantIdOrderByRedeemedAtDescIdDesc(tenantId);
		Map<Long, Coupon> byId = coupons.findAllById(mine.stream().map(CouponRedemption::getCouponId).toList()).stream()
				.collect(Collectors.toMap(Coupon::getId, Function.identity()));
		return mine.stream().filter(redemption -> byId.containsKey(redemption.getCouponId()))
				.map(redemption -> describe(redemption, byId.get(redemption.getCouponId()), tenant, now)).toList();
	}

	private RedeemedCoupon describe(CouponRedemption redemption, Coupon coupon, Tenant tenant, Instant now) {
		Instant until = null;
		boolean active = false;
		String planName = null;
		switch (coupon.getKind()) {
			case PERCENT_OFF, AMOUNT_OFF -> active = redemption.getInvoicesLeft() != null && redemption.getInvoicesLeft() > 0;
			case FREE_DAYS -> {
			}
			case PLAN_UPGRADE -> {
				until = redemption.getRedeemedAt().plus(Duration.ofDays(coupon.getValue()));
				active = until.isAfter(now);
				planName = coupon.getUpgradePlan() == null ? null : coupon.getUpgradePlan().getName();
			}
		}
		return new RedeemedCoupon(coupon.getCode(), coupon.getDescription(), coupon.getKind().name(), coupon.getValue(), coupon.getDurationMonths(),
				redemption.getInvoicesLeft(), planName, redemption.getRedeemedAt(), until, active);
	}

}
