package com.digitalsignage.backend.tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.digitalsignage.backend.plan.Feature;
import com.digitalsignage.backend.plan.Plan;

/** Um cliente com o plano dele e o que está usando: o que o painel mostra em "Plano e cobrança" e na lista de clientes. */
public record TenantSummary(
		Long id,
		String name,
		String slug,
		String status,
		PlanInfo plan,
		/** O plano que vale agora (o liberado por cupom, se houver) para limites e recursos. */
		PlanInfo effectivePlan,
		/** Até quando vale o plano liberado por cupom (nulo = nenhum). */
		Instant bonusUntil,
		UsageService.Usage usage,
		Instant trialEndsAt,
		Instant currentPeriodEnd,
		String billingEmail) {

	/** Limite nulo = sem limite. {@code features} são os recursos incluídos (códigos de {@link Feature}). */
	public record PlanInfo(Long id, String code, String name, int priceCents, String currency, Integer maxDevices, Integer maxStorageMb, Integer maxUsers,
			Integer maxUploadMb, List<String> features) {

		public static PlanInfo of(Plan plan) {
			return new PlanInfo(plan.getId(), plan.getCode(), plan.getName(), plan.getPriceCents(), plan.getCurrency(),
					plan.getMaxDevices(), plan.getMaxStorageMb(), plan.getMaxUsers(), plan.getMaxUploadMb(),
					plan.getFeatures().stream().sorted().map(Feature::name).toList());
		}
	}

	@Component
	public static class Factory {

		private final UsageService usage;
		private final Clock clock;

		public Factory(UsageService usage, Clock clock) {
			this.usage = usage;
			this.clock = clock;
		}

		public TenantSummary of(Tenant tenant) {
			Instant now = clock.instant();
			return new TenantSummary(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus().name(),
					PlanInfo.of(tenant.getPlan()), PlanInfo.of(tenant.getEffectivePlan(now)), tenant.hasBonusAt(now) ? tenant.getBonusPlanUntil() : null,
					usage.of(tenant.getId()), tenant.getTrialEndsAt(), tenant.getCurrentPeriodEnd(), tenant.getBillingEmail());
		}
	}

}
