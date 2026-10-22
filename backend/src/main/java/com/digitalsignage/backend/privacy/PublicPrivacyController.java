package com.digitalsignage.backend.privacy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.billing.BillingService;
import com.digitalsignage.backend.billing.PlatformSettings;

/**
 * Quem responde pelo tratamento de dados na plataforma, para a Política de Privacidade e os Termos de Uso (páginas abertas,
 * sem login): nome, razão social, CNPJ e o canal do encarregado, como a operação configurou.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicPrivacyController {

	public record PublicPrivacy(String companyName, String legalName, String legalDocument, String contactName, String contactEmail,
			String supportEmail, String version) {
	}

	private final BillingService billing;

	public PublicPrivacyController(BillingService billing) {
		this.billing = billing;
	}

	@GetMapping("/privacy")
	public PublicPrivacy privacy() {
		PlatformSettings settings = billing.settings();
		return new PublicPrivacy(settings.getCompanyName(), settings.getLegalName(), settings.getLegalDocument(), settings.getPrivacyContactName(),
				settings.getPrivacyContactEmail(), settings.getSupportEmail(), PrivacyNotice.VERSION);
	}

}
