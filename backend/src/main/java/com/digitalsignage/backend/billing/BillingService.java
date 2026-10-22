package com.digitalsignage.backend.billing;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.coupon.CouponService;
import com.digitalsignage.backend.plan.Plan;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.tenant.TenantStatus;

/**
 * O ciclo de cobrança de cada cliente. A regra, em ordem:
 * <ol>
 * <li><b>Plano grátis</b> (preço 0): nunca gera fatura e o cliente fica em dia.</li>
 * <li><b>Teste (TRIAL)</b>: usa de graça até o fim do teste; nesse dia a primeira fatura é emitida.</li>
 * <li><b>Assinatura em dia (ACTIVE)</b>: {@code invoiceLeadDays} antes do fim do período emite a fatura do próximo
 * (vence no último dia do período atual).</li>
 * <li><b>Fatura vencida e não paga</b>: o cliente passa a PAST_DUE (continua usando) e, depois de
 * {@code graceDays} de atraso, a SUSPENDED (perde o painel; as TVs seguem exibindo o que já têm).</li>
 * <li><b>Pagamento</b>: baixa a fatura, estende o período e, sem outras faturas vencidas, o cliente volta a ACTIVE.</li>
 * </ol>
 * Idempotente: rodar o ciclo várias vezes no mesmo dia não emite fatura repetida.
 */
@Service
public class BillingService {

	private static final Logger log = LoggerFactory.getLogger(BillingService.class);

	private final TenantRepository tenants;
	private final InvoiceRepository invoices;
	private final PlatformSettingsRepository settingsRepository;
	private final CouponService coupons;
	private final InvoiceMailer invoiceMailer;
	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final ZoneId zone;

	public BillingService(
			TenantRepository tenants,
			InvoiceRepository invoices,
			PlatformSettingsRepository settingsRepository,
			CouponService coupons,
			InvoiceMailer invoiceMailer,
			JdbcTemplate jdbc,
			Clock clock,
			@Value("${app.billing.timezone:America/Sao_Paulo}") String zone) {
		this.tenants = tenants;
		this.invoices = invoices;
		this.settingsRepository = settingsRepository;
		this.coupons = coupons;
		this.invoiceMailer = invoiceMailer;
		this.jdbc = jdbc;
		this.clock = clock;
		this.zone = ZoneId.of(zone);
	}

	public PlatformSettings settings() {
		return settingsRepository.findById(PlatformSettings.ID)
				.orElseThrow(() -> new IllegalStateException("Configurações da plataforma ausentes"));
	}

	/** Roda o ciclo para todos os clientes. Uma falha em um cliente não impede os demais. */
	public void runCycle() {
		Instant now = clock.instant();
		for (Tenant tenant : tenants.findAllCustomers()) {
			try {
				processTenant(tenant.getId(), now);
			} catch (RuntimeException e) {
				log.error("Falha no ciclo de cobrança do cliente {}", tenant.getSlug(), e);
			}
		}
	}

	@Transactional
	public void processTenant(Long tenantId, Instant now) {
		Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Cliente não encontrado: " + tenantId));
		TenantStatus status = tenant.getStatus();
		if (status == TenantStatus.CANCELED || status == TenantStatus.SYSTEM) {
			return;
		}
		if (tenant.getBonusPlanUntil() != null && !tenant.hasBonusAt(now)) {
			// o plano liberado por cupom acabou: volta ao contratado
			tenant.setBonus(null, null);
			tenants.save(tenant);
		}
		Plan plan = tenant.getPlan();
		if (plan.getPriceCents() <= 0) {
			// plano sem cobrança: sempre em dia
			if (status != TenantStatus.ACTIVE) {
				tenant.setStatus(TenantStatus.ACTIVE);
				tenants.save(tenant);
			}
			return;
		}

		PlatformSettings settings = settings();
		issueInvoiceIfDue(tenant, plan, settings, now);
		applyOverdueRules(tenant, settings, now);
	}

	private void issueInvoiceIfDue(Tenant tenant, Plan plan, PlatformSettings settings, Instant now) {
		Instant periodStart;
		switch (tenant.getStatus()) {
			case TRIAL -> {
				if (tenant.getTrialEndsAt() == null || now.isBefore(tenant.getTrialEndsAt())) {
					return; // teste em andamento (ou sem data de fim: teste sem prazo, a operação decide)
				}
				periodStart = tenant.getTrialEndsAt();
			}
			case ACTIVE, PAST_DUE -> {
				// ACTIVE sem período definido (a operação ativou à mão): cobra a partir de agora. Já quem está em atraso sem
				// nunca ter pago (PAST_DUE sem período) não ganha fatura nova a cada ciclo: tem a primeira para pagar.
				if (tenant.getCurrentPeriodEnd() == null && tenant.getStatus() == TenantStatus.PAST_DUE) {
					return;
				}
				periodStart = tenant.getCurrentPeriodEnd() != null ? tenant.getCurrentPeriodEnd() : now;
				if (now.isBefore(periodStart.minus(settings.getInvoiceLeadDays(), ChronoUnit.DAYS))) {
					return;
				}
			}
			default -> {
				return; // SUSPENDED: nada novo é emitido; o que está aberto precisa ser pago
			}
		}
		if (invoices.existsForPeriodStart(tenant.getId(), periodStart)) {
			return;
		}
		Instant periodEnd = periodStart.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
		LocalDate due = tenant.getStatus() == TenantStatus.TRIAL
				? periodStart.atZone(zone).toLocalDate().plusDays(settings.getInvoiceLeadDays())
				: periodStart.atZone(zone).toLocalDate();
		String description = "Assinatura " + plan.getName() + " (" + periodStart.atZone(zone).toLocalDate() + " a " + periodEnd.atZone(zone).toLocalDate() + ")";
		int amount = plan.getPriceCents();
		Optional<CouponService.Discount> discount = coupons.consumeDiscount(tenant.getId(), amount);
		if (discount.isPresent()) {
			amount -= discount.get().cents();
			description += " - cupom " + discount.get().code() + " (-R$ " + money(discount.get().cents()) + ")";
		}
		Invoice invoice = create(tenant.getId(), description, periodStart, periodEnd, amount, plan.getCurrency(), due);
		if (amount == 0) {
			// o cupom cobriu tudo: não há o que cobrar, o período já vale
			invoice.markPaid(now, "CUPOM", "Valor coberto pelo cupom " + discount.map(CouponService.Discount::code).orElse(""));
			invoices.save(invoice);
			if (tenant.getCurrentPeriodEnd() == null || periodEnd.isAfter(tenant.getCurrentPeriodEnd())) {
				tenant.setCurrentPeriodEnd(periodEnd);
			}
			reactivateIfSettled(tenant, now);
		}
	}

	private static String money(int cents) {
		return String.format("%d,%02d", cents / 100, cents % 100);
	}

	/** Fatura vencida deixa o cliente em atraso; passada a carência, suspende. */
	private void applyOverdueRules(Tenant tenant, PlatformSettings settings, Instant now) {
		LocalDate today = now.atZone(zone).toLocalDate();
		List<Invoice> open = invoices.findOpenOf(tenant.getId());
		long worstOverdueDays = open.stream().filter(invoice -> invoice.getDueDate().isBefore(today))
				.mapToLong(invoice -> ChronoUnit.DAYS.between(invoice.getDueDate(), today)).max().orElse(-1);
		TenantStatus target = tenant.getStatus();
		if (worstOverdueDays > settings.getGraceDays()) {
			target = TenantStatus.SUSPENDED;
		} else if (worstOverdueDays >= 1 && (target == TenantStatus.ACTIVE || target == TenantStatus.TRIAL)) {
			target = TenantStatus.PAST_DUE;
		}
		if (target != tenant.getStatus()) {
			log.info("Cliente {}: {} -> {}", tenant.getSlug(), tenant.getStatus(), target);
			tenant.setStatus(target);
			tenants.save(tenant);
		}
	}

	// ---------- operações da plataforma ----------

	/** Fatura avulsa (um serviço, um acerto) ou a primeira de um cliente que a operação quer cobrar já. */
	@Transactional
	public Invoice createManual(Long tenantId, String description, int amountCents, LocalDate dueDate) {
		Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Cliente não encontrado: " + tenantId));
		return create(tenant.getId(), description, null, null, amountCents, tenant.getPlan().getCurrency(), dueDate);
	}

	/** Registra o pagamento: baixa a fatura, estende o período do cliente e o reativa se não há outra vencida. */
	@Transactional
	public Invoice registerPayment(Long invoiceId, String method, String note) {
		Invoice invoice = find(invoiceId);
		if (invoice.getStatus() == InvoiceStatus.PAID) {
			throw new ConflictException("Esta fatura já foi paga");
		}
		if (invoice.getStatus() == InvoiceStatus.VOID) {
			throw new ConflictException("Esta fatura foi cancelada e não pode ser paga");
		}
		Instant now = clock.instant();
		invoice.markPaid(now, method == null || method.isBlank() ? "MANUAL" : method.trim().toUpperCase(), note);
		invoices.save(invoice);

		Tenant tenant = tenants.findById(invoice.getTenantId()).orElseThrow();
		if (invoice.getPeriodEnd() != null && (tenant.getCurrentPeriodEnd() == null || invoice.getPeriodEnd().isAfter(tenant.getCurrentPeriodEnd()))) {
			tenant.setCurrentPeriodEnd(invoice.getPeriodEnd());
		}
		reactivateIfSettled(tenant, now);
		return invoice;
	}

	@Transactional
	public Invoice voidInvoice(Long invoiceId) {
		Invoice invoice = find(invoiceId);
		if (invoice.getStatus() == InvoiceStatus.PAID) {
			throw new ConflictException("Uma fatura paga não pode ser cancelada");
		}
		invoice.voidIt();
		invoices.save(invoice);
		reactivateIfSettled(tenants.findById(invoice.getTenantId()).orElseThrow(), clock.instant());
		return invoice;
	}

	/** Sem fatura vencida em aberto, quem estava em atraso/suspenso/em teste pago volta a ficar em dia. */
	private void reactivateIfSettled(Tenant tenant, Instant now) {
		TenantStatus status = tenant.getStatus();
		boolean waiting = status == TenantStatus.PAST_DUE || status == TenantStatus.SUSPENDED
				|| (status == TenantStatus.TRIAL && tenant.getCurrentPeriodEnd() != null);
		if (waiting && invoices.countOverdue(tenant.getId(), now.atZone(zone).toLocalDate()) == 0) {
			tenant.setStatus(TenantStatus.ACTIVE);
			tenants.save(tenant);
		} else {
			tenants.save(tenant);
		}
	}

	private Invoice create(Long tenantId, String description, Instant start, Instant end, int amountCents, String currency, LocalDate due) {
		long sequence = jdbc.queryForObject("SELECT nextval('invoice_number_seq')", Long.class);
		String number = String.format("%d-%06d", clock.instant().atZone(zone).getYear(), sequence);
		Invoice saved = invoices.save(new Invoice(tenantId, number, description, start, end, amountCents, currency, due));
		invoiceMailer.issued(saved);
		return saved;
	}

	private Invoice find(Long id) {
		return invoices.findById(id).orElseThrow(() -> new NoSuchElementException("Fatura não encontrada: " + id));
	}

}
