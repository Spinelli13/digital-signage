package com.digitalsignage.backend.platform;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.billing.BillingService;
import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.plan.Plan;
import com.digitalsignage.backend.plan.PlanRepository;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.tenant.TenantStatus;
import com.digitalsignage.backend.tenant.TenantSummary;
import com.digitalsignage.backend.tenant.UsageService;
import com.digitalsignage.backend.user.Role;
import com.digitalsignage.backend.user.User;
import com.digitalsignage.backend.user.UserController;
import com.digitalsignage.backend.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Os clientes da plataforma: cadastro, plano, situação da assinatura e uso. Só o operador da plataforma. */
@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {

	public record CreateRequest(
			@NotBlank @Size(max = 150) String name,
			@NotNull Long planId,
			@NotBlank @Size(max = 150) String adminName,
			@NotBlank @Email @Size(max = 255) String adminEmail,
			@NotBlank String adminPassword,
			@Email @Size(max = 255) String billingEmail,
			@Size(max = 30) String document) {
	}

	/** Campos ausentes não mudam. */
	public record UpdateRequest(
			@Size(max = 150) String name,
			Long planId,
			TenantStatus status,
			@Email @Size(max = 255) String billingEmail,
			@Size(max = 30) String document,
			@Size(max = 1000) String notes,
			Instant trialEndsAt,
			Instant currentPeriodEnd) {
	}

	private final TenantRepository tenants;
	private final PlanRepository plans;
	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final BillingService billing;
	private final TenantSummary.Factory summaries;
	private final UsageService usageService;
	private final com.digitalsignage.backend.auth.PasswordResetService passwordResets;
	private final Clock clock;

	public PlatformTenantController(
			TenantRepository tenants,
			PlanRepository plans,
			UserRepository users,
			PasswordEncoder encoder,
			BillingService billing,
			TenantSummary.Factory summaries,
			UsageService usageService,
			com.digitalsignage.backend.auth.PasswordResetService passwordResets,
			Clock clock) {
		this.tenants = tenants;
		this.plans = plans;
		this.users = users;
		this.encoder = encoder;
		this.billing = billing;
		this.summaries = summaries;
		this.passwordResets = passwordResets;
		this.usageService = usageService;
		this.clock = clock;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<TenantSummary> list() {
		return tenants.findAllCustomers().stream().map(summaries::of).toList();
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public TenantSummary get(@PathVariable Long id) {
		return summaries.of(find(id));
	}

	/**
	 * Cadastra o cliente com o primeiro administrador. Plano com preço começa em TESTE (os dias vêm das configurações da
	 * plataforma); plano grátis já começa em dia.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public TenantSummary create(@Valid @RequestBody CreateRequest request) {
		String name = request.name().trim();
		if (tenants.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe um cliente chamado \"" + name + "\"");
		}
		String email = request.adminEmail().trim().toLowerCase();
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("Já existe um usuário com o e-mail " + email);
		}
		if (request.adminPassword().length() < UserController.MIN_PASSWORD) {
			throw new IllegalArgumentException("A senha precisa ter pelo menos " + UserController.MIN_PASSWORD + " caracteres");
		}
		Plan plan = activePlan(request.planId());

		boolean paid = plan.getPriceCents() > 0;
		int trialDays = billing.settings().getTrialDays();
		boolean trial = paid && trialDays > 0;
		Tenant tenant = new Tenant(name, uniqueSlug(name), plan, trial ? TenantStatus.TRIAL : TenantStatus.ACTIVE);
		Instant now = clock.instant();
		if (trial) {
			tenant.setTrialEndsAt(now.plus(trialDays, ChronoUnit.DAYS));
		} else if (paid) {
			tenant.setCurrentPeriodEnd(now); // sem teste: o primeiro ciclo de cobrança emite a fatura já
		}
		tenant.setBillingEmail(blankToNull(request.billingEmail()));
		tenant.setDocument(blankToNull(request.document()));
		tenant = tenants.save(tenant);
		User admin = users.save(new User(request.adminName().trim(), email, encoder.encode(request.adminPassword()), tenant.getId(), Role.ADMIN));
		passwordResets.welcome(admin, tenant.getName());
		return summaries.of(tenant);
	}

	@PutMapping("/{id}")
	@Transactional
	public TenantSummary update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		Tenant tenant = find(id);
		if (request.name() != null && !request.name().isBlank()) {
			String name = request.name().trim();
			if (!name.equalsIgnoreCase(tenant.getName()) && tenants.existsByNameIgnoreCase(name)) {
				throw new ConflictException("Já existe um cliente chamado \"" + name + "\"");
			}
			tenant.setName(name);
		}
		if (request.planId() != null && !request.planId().equals(tenant.getPlan().getId())) {
			Plan plan = activePlan(request.planId());
			requireFits(tenant, plan);
			tenant.setPlan(plan);
		}
		if (request.status() != null) {
			if (request.status() == TenantStatus.SYSTEM) {
				throw new IllegalArgumentException("A situação SYSTEM é só da reserva de TVs");
			}
			tenant.setStatus(request.status());
		}
		if (request.billingEmail() != null) {
			tenant.setBillingEmail(blankToNull(request.billingEmail()));
		}
		if (request.document() != null) {
			tenant.setDocument(blankToNull(request.document()));
		}
		if (request.notes() != null) {
			tenant.setNotes(blankToNull(request.notes()));
		}
		if (request.trialEndsAt() != null) {
			tenant.setTrialEndsAt(request.trialEndsAt());
		}
		if (request.currentPeriodEnd() != null) {
			tenant.setCurrentPeriodEnd(request.currentPeriodEnd());
		}
		return summaries.of(tenants.save(tenant));
	}

	// ---------- regras ----------

	private Tenant find(Long id) {
		if (id == null || id <= 1) {
			throw new NoSuchElementException("Cliente não encontrado: " + id);
		}
		return tenants.findById(id).orElseThrow(() -> new NoSuchElementException("Cliente não encontrado: " + id));
	}

	private Plan activePlan(Long planId) {
		Plan plan = plans.findById(planId).orElseThrow(() -> new NoSuchElementException("Plano não encontrado: " + planId));
		if (!plan.isActive()) {
			throw new IllegalArgumentException("O plano \"" + plan.getName() + "\" está desativado");
		}
		return plan;
	}

	/** Não deixa passar para um plano que o cliente já ultrapassa (as TVs e mídias dele não sumiriam, mas o limite ficaria furado). */
	private void requireFits(Tenant tenant, Plan plan) {
		UsageService.Usage usage = usageService.of(tenant.getId());
		if (plan.getMaxDevices() != null && usage.devices() > plan.getMaxDevices()) {
			throw new ConflictException("O cliente usa " + usage.devices() + " TV(s) e o plano " + plan.getName() + " permite " + plan.getMaxDevices() + ".");
		}
		if (plan.getMaxUsers() != null && usage.users() > plan.getMaxUsers()) {
			throw new ConflictException("O cliente tem " + usage.users() + " usuário(s) ativos e o plano " + plan.getName() + " permite " + plan.getMaxUsers() + ".");
		}
		if (plan.getMaxStorageMb() != null && usage.storageBytes() > plan.getMaxStorageMb() * 1024L * 1024L) {
			throw new ConflictException("O cliente usa mais espaço de mídias do que o plano " + plan.getName() + " permite (" + plan.getMaxStorageMb() + " MB).");
		}
	}

	/** "Padaria do Zé" -> "padaria-do-ze"; se já existe, acrescenta um número. */
	private String uniqueSlug(String name) {
		String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (base.isEmpty()) {
			base = "cliente";
		}
		String slug = base;
		for (int n = 2; tenants.existsBySlug(slug); n++) {
			slug = base + "-" + n;
		}
		return slug;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
