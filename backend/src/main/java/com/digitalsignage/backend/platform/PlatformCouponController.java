package com.digitalsignage.backend.platform;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.coupon.Coupon;
import com.digitalsignage.backend.coupon.CouponKind;
import com.digitalsignage.backend.coupon.CouponRedemption;
import com.digitalsignage.backend.coupon.CouponRedemptionRepository;
import com.digitalsignage.backend.coupon.CouponRepository;
import com.digitalsignage.backend.plan.Plan;
import com.digitalsignage.backend.plan.PlanRepository;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Os cupons de assinatura: criar, gerar em lote, acompanhar quem usou e desligar. Só o gestor da plataforma. */
@RestController
@RequestMapping("/api/v1/platform/coupons")
public class PlatformCouponController {

	/** Letras e números sem os que se confundem (0/O, 1/I/L): o cupom é digitado à mão. */
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	public record CouponRequest(
			@NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "use só letras, números, hífen e sublinhado") String code,
			@Size(max = 255) String description,
			@NotNull CouponKind kind,
			@Min(1) int value,
			@Min(1) @Max(60) Integer durationMonths,
			Long upgradePlanId,
			Instant validFrom,
			Instant validUntil,
			@Min(1) Integer maxRedemptions,
			Boolean active) {
	}

	public record BatchRequest(
			@Size(max = 12) @Pattern(regexp = "[A-Za-z0-9]*", message = "o prefixo usa só letras e números") String prefix,
			@Min(1) @Max(500) int quantity,
			@Size(max = 60) String batch,
			@Size(max = 255) String description,
			@NotNull CouponKind kind,
			@Min(1) int value,
			@Min(1) @Max(60) Integer durationMonths,
			Long upgradePlanId,
			Instant validFrom,
			Instant validUntil) {
	}

	public record CouponResponse(
			Long id, String code, String description, String kind, int value, Integer durationMonths,
			Long upgradePlanId, String upgradePlanName, Instant validFrom, Instant validUntil,
			Integer maxRedemptions, int redeemedCount, boolean active, String batch, String state, Instant createdAt) {
	}

	public record RedemptionResponse(Long tenantId, String tenantName, String redeemedBy, Instant redeemedAt) {
	}

	public record BatchResponse(String batch, List<CouponResponse> coupons) {
	}

	private final CouponRepository coupons;
	private final CouponRedemptionRepository redemptions;
	private final PlanRepository plans;
	private final TenantRepository tenants;
	private final Clock clock;

	public PlatformCouponController(CouponRepository coupons, CouponRedemptionRepository redemptions, PlanRepository plans, TenantRepository tenants, Clock clock) {
		this.coupons = coupons;
		this.redemptions = redemptions;
		this.plans = plans;
		this.tenants = tenants;
		this.clock = clock;
	}

	@GetMapping
	public List<CouponResponse> list() {
		return coupons.findAllByOrderByCreatedAtDescIdDesc().stream().map(this::toResponse).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public CouponResponse create(@Valid @RequestBody CouponRequest request) {
		String code = request.code().trim().toUpperCase();
		if (coupons.existsByCodeIgnoreCase(code)) {
			throw new ConflictException("Já existe um cupom com o código \"" + code + "\"");
		}
		Coupon coupon = new Coupon(code, request.kind(), request.value());
		applyBenefit(coupon, request.kind(), request.value(), request.durationMonths(), request.upgradePlanId());
		coupon.setDescription(blankToNull(request.description()));
		applyValidity(coupon, request.validFrom(), request.validUntil());
		coupon.setMaxRedemptions(request.maxRedemptions());
		if (request.active() != null) {
			coupon.setActive(request.active());
		}
		return toResponse(coupons.save(coupon));
	}

	/**
	 * O código não muda. Depois que alguém usou o cupom, o que ele dá também não muda (quem já resgatou contou com isso):
	 * só a validade, o limite de usos, a descrição e a situação.
	 */
	@PutMapping("/{id}")
	@Transactional
	public CouponResponse update(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
		Coupon coupon = find(id);
		if (!coupon.getCode().equalsIgnoreCase(request.code().trim())) {
			throw new ConflictException("O código de um cupom não pode ser alterado");
		}
		boolean benefitChanged = coupon.getKind() != request.kind() || coupon.getValue() != request.value()
				|| !java.util.Objects.equals(coupon.getDurationMonths(), effectiveDuration(request.kind(), request.durationMonths()))
				|| !java.util.Objects.equals(coupon.getUpgradePlan() == null ? null : coupon.getUpgradePlan().getId(),
						request.kind() == CouponKind.PLAN_UPGRADE ? request.upgradePlanId() : null);
		if (benefitChanged) {
			if (coupon.getRedeemedCount() > 0) {
				throw new ConflictException("Este cupom já foi usado: só dá para mudar a validade, o limite de usos, a descrição e a situação.");
			}
			applyBenefit(coupon, request.kind(), request.value(), request.durationMonths(), request.upgradePlanId());
		}
		if (request.maxRedemptions() != null && request.maxRedemptions() < coupon.getRedeemedCount()) {
			throw new ConflictException("O cupom já foi usado " + coupon.getRedeemedCount() + " vez(es): o limite não pode ser menor que isso.");
		}
		coupon.setDescription(blankToNull(request.description()));
		applyValidity(coupon, request.validFrom(), request.validUntil());
		coupon.setMaxRedemptions(request.maxRedemptions());
		if (request.active() != null) {
			coupon.setActive(request.active());
		}
		return toResponse(coupons.save(coupon));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		Coupon coupon = find(id);
		if (coupon.getRedeemedCount() > 0) {
			throw new ConflictException("Este cupom já foi usado e não pode ser excluído. Desative-o para ninguém mais resgatar.");
		}
		coupons.delete(coupon);
	}

	/** Vários cupons de uma vez: cada código é novo, aleatório e de uso único (para entregar um a cada pessoa). */
	@PostMapping("/batch")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public BatchResponse batch(@Valid @RequestBody BatchRequest request) {
		String prefix = request.prefix() == null ? "" : request.prefix().trim().toUpperCase();
		String batch = request.batch() == null || request.batch().isBlank() ? "Lote " + clock.instant().toString().substring(0, 10) : request.batch().trim();
		List<Coupon> created = new ArrayList<>();
		for (int i = 0; i < request.quantity(); i++) {
			Coupon coupon = new Coupon(freshCode(prefix), request.kind(), request.value());
			applyBenefit(coupon, request.kind(), request.value(), request.durationMonths(), request.upgradePlanId());
			coupon.setDescription(blankToNull(request.description()));
			applyValidity(coupon, request.validFrom(), request.validUntil());
			coupon.setMaxRedemptions(1);
			coupon.setBatch(batch);
			created.add(coupons.save(coupon));
		}
		return new BatchResponse(batch, created.stream().map(this::toResponse).toList());
	}

	@GetMapping("/{id}/redemptions")
	@Transactional(readOnly = true)
	public List<RedemptionResponse> redemptions(@PathVariable Long id) {
		find(id);
		List<CouponRedemption> used = redemptions.findByCouponIdOrderByRedeemedAtDescIdDesc(id);
		Map<Long, String> names = tenants.findAllById(used.stream().map(CouponRedemption::getTenantId).toList()).stream()
				.collect(Collectors.toMap(Tenant::getId, Tenant::getName, (a, b) -> a));
		return used.stream().map(r -> new RedemptionResponse(r.getTenantId(), names.getOrDefault(r.getTenantId(), "?"), r.getRedeemedBy(), r.getRedeemedAt())).toList();
	}

	// ---------- regras ----------

	private void applyBenefit(Coupon coupon, CouponKind kind, int value, Integer durationMonths, Long upgradePlanId) {
		if (kind == CouponKind.PERCENT_OFF && value > 100) {
			throw new IllegalArgumentException("A porcentagem de desconto vai de 1 a 100");
		}
		coupon.setKind(kind);
		coupon.setValue(value);
		coupon.setDurationMonths(effectiveDuration(kind, durationMonths));
		if (kind == CouponKind.PLAN_UPGRADE) {
			if (upgradePlanId == null) {
				throw new IllegalArgumentException("Escolha o plano que o cupom libera");
			}
			Plan plan = plans.findById(upgradePlanId).orElseThrow(() -> new NoSuchElementException("Plano não encontrado: " + upgradePlanId));
			coupon.setUpgradePlan(plan);
		} else {
			coupon.setUpgradePlan(null);
		}
	}

	/** Descontos valem por algumas faturas (padrão: uma); os outros tipos não usam isto. */
	private static Integer effectiveDuration(CouponKind kind, Integer durationMonths) {
		return kind.isDiscount() ? (durationMonths == null ? Integer.valueOf(1) : durationMonths) : null;
	}

	private static void applyValidity(Coupon coupon, Instant from, Instant until) {
		if (from != null && until != null && !until.isAfter(from)) {
			throw new IllegalArgumentException("O fim da validade precisa ser depois do início");
		}
		coupon.setValidFrom(from);
		coupon.setValidUntil(until);
	}

	private String freshCode(String prefix) {
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder code = new StringBuilder(prefix.isEmpty() ? "" : prefix + "-");
			for (int i = 0; i < 8; i++) {
				if (i == 4) {
					code.append('-');
				}
				code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
			}
			if (!coupons.existsByCodeIgnoreCase(code.toString())) {
				return code.toString();
			}
		}
		throw new IllegalStateException("Não foi possível gerar um código novo");
	}

	private Coupon find(Long id) {
		return coupons.findById(id).orElseThrow(() -> new NoSuchElementException("Cupom não encontrado: " + id));
	}

	/** SCHEDULED (ainda não começou), EXPIRED, EXHAUSTED (usos acabaram), OFF (desligado) ou ACTIVE. */
	private String stateOf(Coupon coupon) {
		Instant now = clock.instant();
		if (!coupon.isActive()) {
			return "OFF";
		}
		if (coupon.getValidUntil() != null && !now.isBefore(coupon.getValidUntil())) {
			return "EXPIRED";
		}
		if (coupon.getMaxRedemptions() != null && coupon.getRedeemedCount() >= coupon.getMaxRedemptions()) {
			return "EXHAUSTED";
		}
		if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
			return "SCHEDULED";
		}
		return "ACTIVE";
	}

	private CouponResponse toResponse(Coupon coupon) {
		Plan plan = coupon.getUpgradePlan();
		return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getDescription(), coupon.getKind().name(), coupon.getValue(),
				coupon.getDurationMonths(), plan == null ? null : plan.getId(), plan == null ? null : plan.getName(), coupon.getValidFrom(),
				coupon.getValidUntil(), coupon.getMaxRedemptions(), coupon.getRedeemedCount(), coupon.isActive(), coupon.getBatch(),
				stateOf(coupon), coupon.getCreatedAt());
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
