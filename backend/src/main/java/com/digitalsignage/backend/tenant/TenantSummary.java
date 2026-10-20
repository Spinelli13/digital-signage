package com.digitalsignage.backend.tenant;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.digitalsignage.backend.plan.Plan;

/** Um cliente com o plano dele e o que está usando: o que o painel mostra em "Plano e cobrança" e na lista de clientes. */
public record TenantSummary(
		Long id,
		String name,
		String slug,
		String status,
		PlanInfo plan,
		UsageService.Usage usage,
		Instant trialEndsAt,
		Instant currentPeriodEnd,
		String billingEmail) {

	/** Limite nulo = sem limite. */
	public record PlanInfo(Long id, String code, String name, int priceCents, String currency, Integer maxDevices, Integer maxStorageMb, Integer maxUsers) {

		public static PlanInfo of(Plan plan) {
			return new PlanInfo(plan.getId(), plan.getCode(), plan.getName(), plan.getPriceCents(), plan.getCurrency(),
					plan.getMaxDevices(), plan.getMaxStorageMb(), plan.getMaxUsers());
		}
	}

	@Component
	public static class Factory {

		private final UsageService usage;

		public Factory(UsageService usage) {
			this.usage = usage;
		}

		public TenantSummary of(Tenant tenant) {
			return new TenantSummary(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus().name(),
					PlanInfo.of(tenant.getPlan()), usage.of(tenant.getId()), tenant.getTrialEndsAt(), tenant.getCurrentPeriodEnd(),
					tenant.getBillingEmail());
		}
	}

}
