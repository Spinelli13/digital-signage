package com.digitalsignage.backend.platform;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.billing.BillingService;
import com.digitalsignage.backend.billing.Invoice;
import com.digitalsignage.backend.billing.InvoiceRepository;
import com.digitalsignage.backend.billing.InvoiceResponse;
import com.digitalsignage.backend.billing.InvoiceStatus;
import com.digitalsignage.backend.billing.PlatformSettings;
import com.digitalsignage.backend.billing.PlatformSettingsRepository;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.tenant.TenantStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Faturas, configurações de cobrança e a visão geral da operação da plataforma. */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformBillingController {

	public record ManualInvoiceRequest(@NotBlank @Size(max = 255) String description, @Min(0) int amountCents, @NotNull LocalDate dueDate) {
	}

	public record PaymentRequest(@Size(max = 20) String method, @Size(max = 500) String note) {
	}

	/** Campos ausentes não mudam. */
	public record SettingsRequest(
			@Size(max = 150) String companyName,
			@Size(max = 2000) String paymentInstructions,
			@Size(max = 255) String supportEmail,
			@Size(max = 160) String legalName,
			@Size(max = 30) String legalDocument,
			@Size(max = 120) String privacyContactName,
			@Size(max = 255) String privacyContactEmail,
			@Min(0) @Max(90) Integer trialDays,
			@Min(0) @Max(60) Integer graceDays,
			@Min(0) @Max(30) Integer invoiceLeadDays) {
	}

	public record SettingsResponse(String companyName, String paymentInstructions, String supportEmail, String legalName, String legalDocument,
			String privacyContactName, String privacyContactEmail, int trialDays, int graceDays, int invoiceLeadDays) {

		static SettingsResponse of(PlatformSettings settings) {
			return new SettingsResponse(settings.getCompanyName(), settings.getPaymentInstructions(), settings.getSupportEmail(),
					settings.getLegalName(), settings.getLegalDocument(), settings.getPrivacyContactName(), settings.getPrivacyContactEmail(),
					settings.getTrialDays(), settings.getGraceDays(), settings.getInvoiceLeadDays());
		}
	}

	public record Overview(
			long tenants,
			Map<String, Long> tenantsByStatus,
			long devices,
			/** Receita mensal recorrente: o preço do plano dos clientes em dia ou em atraso (ainda assinantes). */
			long monthlyRecurringCents,
			long openInvoices,
			long openInvoicesCents,
			long overdueInvoices,
			long overdueInvoicesCents) {
	}

	private final InvoiceRepository invoices;
	private final TenantRepository tenants;
	private final BillingService billing;
	private final PlatformSettingsRepository settings;
	private final org.springframework.jdbc.core.JdbcTemplate jdbc;
	private final Clock clock;
	private final ZoneId zone;

	public PlatformBillingController(
			InvoiceRepository invoices,
			TenantRepository tenants,
			BillingService billing,
			PlatformSettingsRepository settings,
			org.springframework.jdbc.core.JdbcTemplate jdbc,
			Clock clock,
			@Value("${app.billing.timezone:America/Sao_Paulo}") String zone) {
		this.invoices = invoices;
		this.tenants = tenants;
		this.billing = billing;
		this.settings = settings;
		this.jdbc = jdbc;
		this.clock = clock;
		this.zone = ZoneId.of(zone);
	}

	// ---------- faturas ----------

	/** Todas as faturas (mais recentes primeiro); filtros opcionais por situação e por cliente. */
	@GetMapping("/invoices")
	@Transactional(readOnly = true)
	public List<InvoiceResponse> list(@RequestParam(required = false) String status, @RequestParam(required = false) Long tenantId) {
		Map<Long, String> names = tenants.findAll().stream().collect(Collectors.toMap(Tenant::getId, Tenant::getName));
		LocalDate today = today();
		return invoices.findAllByOrderByCreatedAtDescIdDesc().stream()
				.filter(invoice -> status == null || status.isBlank() || invoice.getStatus().name().equalsIgnoreCase(status))
				.filter(invoice -> tenantId == null || tenantId.equals(invoice.getTenantId()))
				.map(invoice -> InvoiceResponse.of(invoice, names.get(invoice.getTenantId()), today))
				.toList();
	}

	@PostMapping("/tenants/{tenantId}/invoices")
	@ResponseStatus(HttpStatus.CREATED)
	public InvoiceResponse createManual(@PathVariable Long tenantId, @Valid @RequestBody ManualInvoiceRequest request) {
		Invoice invoice = billing.createManual(tenantId, request.description().trim(), request.amountCents(), request.dueDate());
		return respond(invoice);
	}

	@PostMapping("/invoices/{id}/pay")
	public InvoiceResponse pay(@PathVariable Long id, @Valid @RequestBody(required = false) PaymentRequest request) {
		return respond(billing.registerPayment(id, request == null ? null : request.method(), request == null ? null : request.note()));
	}

	@PostMapping("/invoices/{id}/void")
	public InvoiceResponse voidInvoice(@PathVariable Long id) {
		return respond(billing.voidInvoice(id));
	}

	/** Roda o ciclo de cobrança agora (emitir faturas, marcar atraso, suspender). O sistema também roda sozinho a cada hora. */
	@PostMapping("/billing/run")
	public Map<String, String> runNow() {
		billing.runCycle();
		return Map.of("status", "ok");
	}

	// ---------- configurações ----------

	@GetMapping("/settings")
	public SettingsResponse settings() {
		return SettingsResponse.of(billing.settings());
	}

	@PutMapping("/settings")
	@Transactional
	public SettingsResponse updateSettings(@Valid @RequestBody SettingsRequest request) {
		PlatformSettings current = billing.settings();
		if (request.companyName() != null && !request.companyName().isBlank()) {
			current.setCompanyName(request.companyName().trim());
		}
		if (request.paymentInstructions() != null) {
			current.setPaymentInstructions(request.paymentInstructions().isBlank() ? null : request.paymentInstructions().trim());
		}
		if (request.supportEmail() != null) {
			current.setSupportEmail(request.supportEmail().isBlank() ? null : request.supportEmail().trim());
		}
		if (request.legalName() != null) {
			current.setLegalName(request.legalName().isBlank() ? null : request.legalName().trim());
		}
		if (request.legalDocument() != null) {
			current.setLegalDocument(request.legalDocument().isBlank() ? null : request.legalDocument().trim());
		}
		if (request.privacyContactName() != null) {
			current.setPrivacyContactName(request.privacyContactName().isBlank() ? null : request.privacyContactName().trim());
		}
		if (request.privacyContactEmail() != null) {
			current.setPrivacyContactEmail(request.privacyContactEmail().isBlank() ? null : request.privacyContactEmail().trim());
		}
		if (request.trialDays() != null) {
			current.setTrialDays(request.trialDays());
		}
		if (request.graceDays() != null) {
			current.setGraceDays(request.graceDays());
		}
		if (request.invoiceLeadDays() != null) {
			current.setInvoiceLeadDays(request.invoiceLeadDays());
		}
		return SettingsResponse.of(settings.save(current));
	}

	// ---------- visão geral ----------

	@GetMapping("/overview")
	@Transactional(readOnly = true)
	public Overview overview() {
		List<Tenant> all = tenants.findAllCustomers();
		Map<TenantStatus, Long> byStatus = new EnumMap<>(TenantStatus.class);
		all.forEach(tenant -> byStatus.merge(tenant.getStatus(), 1L, Long::sum));
		long mrr = all.stream()
				.filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE || tenant.getStatus() == TenantStatus.PAST_DUE)
				.mapToLong(tenant -> tenant.getPlan().getPriceCents()).sum();
		long devices = jdbc.queryForObject("SELECT count(*) FROM devices WHERE status <> 'PENDING'", Long.class);
		LocalDate today = today();
		List<Invoice> open = invoices.findAll().stream().filter(invoice -> invoice.getStatus() == InvoiceStatus.OPEN).toList();
		List<Invoice> overdue = open.stream().filter(invoice -> invoice.getDueDate().isBefore(today)).toList();
		return new Overview(all.size(),
				byStatus.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)),
				devices, mrr, open.size(), open.stream().mapToLong(Invoice::getAmountCents).sum(),
				overdue.size(), overdue.stream().mapToLong(Invoice::getAmountCents).sum());
	}

	private InvoiceResponse respond(Invoice invoice) {
		String name = tenants.findById(invoice.getTenantId()).map(Tenant::getName).orElse(null);
		return InvoiceResponse.of(invoice, name, today());
	}

	private LocalDate today() {
		return clock.instant().atZone(zone).toLocalDate();
	}

}
