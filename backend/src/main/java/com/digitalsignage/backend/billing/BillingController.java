package com.digitalsignage.backend.billing;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.coupon.CouponService;
import com.digitalsignage.backend.plan.Plan;
import com.digitalsignage.backend.plan.PlanRepository;
import com.digitalsignage.backend.security.PanelUser;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.tenant.TenantSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Plano e cobrança" do cliente: o plano, o uso, as faturas e como pagar. Só o Administrador do cliente. */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

	public record PlanOption(Long id, String code, String name, String description, int priceCents, String currency,
			Integer maxDevices, Integer maxStorageMb, Integer maxUsers, Integer maxUploadMb, List<String> features) {

		static PlanOption of(Plan plan) {
			return new PlanOption(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), plan.getPriceCents(), plan.getCurrency(),
					plan.getMaxDevices(), plan.getMaxStorageMb(), plan.getMaxUsers(), plan.getMaxUploadMb(),
					plan.getFeatures().stream().sorted().map(com.digitalsignage.backend.plan.Feature::name).toList());
		}
	}

	public record BillingResponse(
			TenantSummary tenant,
			/** CPF/CNPJ cadastrado (o resumo do cliente não o traz). */
			String document,
			List<InvoiceResponse> invoices,
			/** Como pagar (texto da operação) e como falar com ela. */
			String companyName,
			String paymentInstructions,
			String supportEmail,
			/** Os planos disponíveis (para o cliente comparar e pedir a troca). */
			List<PlanOption> plans,
			/** Os cupons que o cliente já resgatou (os que ainda valem vêm marcados). */
			List<CouponService.RedeemedCoupon> coupons) {
	}

	public record RedeemRequest(@NotBlank @Size(max = 60) String code) {
	}

	/** Campos ausentes não mudam. */
	public record ProfileRequest(@Size(max = 150) String name, @Email @Size(max = 255) String billingEmail, @Size(max = 30) String document) {
	}

	private final TenantRepository tenants;
	private final InvoiceRepository invoices;
	private final PlanRepository plans;
	private final PlatformSettingsRepository settings;
	private final CouponService coupons;
	private final TenantSummary.Factory summaries;
	private final Clock clock;
	private final ZoneId zone;

	public BillingController(
			TenantRepository tenants,
			InvoiceRepository invoices,
			PlanRepository plans,
			PlatformSettingsRepository settings,
			CouponService coupons,
			TenantSummary.Factory summaries,
			Clock clock,
			@Value("${app.billing.timezone:America/Sao_Paulo}") String zone) {
		this.tenants = tenants;
		this.invoices = invoices;
		this.plans = plans;
		this.settings = settings;
		this.coupons = coupons;
		this.summaries = summaries;
		this.clock = clock;
		this.zone = ZoneId.of(zone);
	}

	@GetMapping
	@Transactional(readOnly = true)
	public BillingResponse get(@AuthenticationPrincipal PanelUser user) {
		Tenant tenant = tenant(user);
		LocalDate today = clock.instant().atZone(zone).toLocalDate();
		PlatformSettings platform = settings.findById(PlatformSettings.ID).orElseThrow();
		return new BillingResponse(
				summaries.of(tenant),
				tenant.getDocument(),
				// só as faturas DESTE cliente (a consulta recebe o cliente: as faturas não passam pelo isolamento automático)
				invoices.findByTenantIdOrderByCreatedAtDescIdDesc(tenant.getId()).stream().map(invoice -> InvoiceResponse.of(invoice, tenant.getName(), today)).toList(),
				platform.getCompanyName(), platform.getPaymentInstructions(), platform.getSupportEmail(),
				plans.findAllByOrderBySortOrderAscIdAsc().stream().filter(Plan::isActive).filter(plan -> !"interno".equals(plan.getCode())).map(PlanOption::of).toList(),
				coupons.redeemedBy(tenant.getId()));
	}

	/** O administrador do cliente resgata um cupom de assinatura (desconto, dias grátis ou plano liberado). */
	@PostMapping("/coupon")
	public CouponService.RedeemedCoupon redeemCoupon(@AuthenticationPrincipal PanelUser user, @Valid @RequestBody RedeemRequest request) {
		return coupons.redeem(user.tenantId(), user.email(), request.code());
	}

	/** Dados cadastrais do cliente (nome e contato de cobrança). */
	@PutMapping("/profile")
	@Transactional
	public TenantSummary updateProfile(@AuthenticationPrincipal PanelUser user, @Valid @RequestBody ProfileRequest request) {
		Tenant tenant = tenant(user);
		if (request.name() != null && !request.name().isBlank()) {
			String name = request.name().trim();
			if (!name.equalsIgnoreCase(tenant.getName()) && tenants.existsByNameIgnoreCase(name)) {
				throw new ConflictException("Já existe um cliente chamado \"" + name + "\"");
			}
			tenant.setName(name);
		}
		if (request.billingEmail() != null) {
			tenant.setBillingEmail(request.billingEmail().isBlank() ? null : request.billingEmail().trim());
		}
		if (request.document() != null) {
			tenant.setDocument(request.document().isBlank() ? null : request.document().trim());
		}
		return summaries.of(tenants.save(tenant));
	}

	private Tenant tenant(PanelUser user) {
		return tenants.findById(user.tenantId()).orElseThrow(() -> new NoSuchElementException("Cliente não encontrado"));
	}

}
