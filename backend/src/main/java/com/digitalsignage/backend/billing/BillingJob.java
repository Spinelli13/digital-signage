package com.digitalsignage.backend.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** De hora em hora: emite as faturas que chegaram na hora e aplica atraso/suspensão. Desligável nos testes. */
@Component
@ConditionalOnProperty(name = "app.billing.enabled", havingValue = "true", matchIfMissing = true)
public class BillingJob {

	private final BillingService billing;

	public BillingJob(BillingService billing) {
		this.billing = billing;
	}

	@Scheduled(fixedDelayString = "${app.billing.interval-ms:3600000}", initialDelayString = "${app.billing.initial-delay-ms:120000}")
	public void tick() {
		billing.runCycle();
	}

}
