package com.digitalsignage.backend.billing;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.digitalsignage.backend.mail.EmailService;
import com.digitalsignage.backend.mail.EmailTemplates;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.user.Role;
import com.digitalsignage.backend.user.UserRepository;

/** Avisa o cliente por e-mail que uma fatura foi emitida: vai para o e-mail de cobrança, ou, sem ele, para os administradores. */
@Component
public class InvoiceMailer {

	private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final EmailService email;
	private final UserRepository users;
	private final TenantRepository tenants;
	private final PlatformSettingsRepository settings;

	public InvoiceMailer(EmailService email, UserRepository users, TenantRepository tenants, PlatformSettingsRepository settings) {
		this.email = email;
		this.users = users;
		this.tenants = tenants;
		this.settings = settings;
	}

	/** Faturas de valor zero (cobertas por cupom) não geram aviso. Nunca lança: o aviso é um extra, a fatura já existe. */
	public void issued(Invoice invoice) {
		if (invoice.getAmountCents() <= 0 || !email.enabled()) {
			return;
		}
		try {
			Tenant tenant = tenants.findById(invoice.getTenantId()).orElse(null);
			if (tenant == null) {
				return;
			}
			List<String> to = new ArrayList<>();
			if (tenant.getBillingEmail() != null && !tenant.getBillingEmail().isBlank()) {
				to.add(tenant.getBillingEmail().strip());
			} else {
				users.findByTenantIdOrderByName(tenant.getId()).stream()
						.filter(user -> user.isActive() && Role.ADMIN.name().equals(user.getRole().name()))
						.forEach(user -> to.add(user.getEmail()));
			}
			PlatformSettings platform = settings.findAll().stream().findFirst().orElse(null);
			String company = platform == null || platform.getCompanyName() == null || platform.getCompanyName().isBlank() ? "Vyzzu" : platform.getCompanyName();
			EmailTemplates.Message message = EmailTemplates.invoice(company, tenant.getName(), invoice.getDescription(),
					String.format("%d,%02d", invoice.getAmountCents() / 100, invoice.getAmountCents() % 100), invoice.getDueDate().format(BRAZILIAN_DATE),
					platform == null ? null : platform.getPaymentInstructions(), platform == null ? null : platform.getSupportEmail());
			email.sendToAll(to, message.subject(), message.body());
		} catch (RuntimeException e) {
			// o aviso é um extra: nunca derruba a emissão da fatura
		}
	}
}
