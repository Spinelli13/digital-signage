package com.digitalsignage.backend.tenant;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.digitalsignage.backend.plan.Plan;

/** Confere o que o plano do cliente permite antes de acrescentar TV, mídia ou usuário. Limite nulo = sem limite. */
@Service
public class PlanLimits {

	private final TenantRepository tenants;
	private final UsageService usage;

	public PlanLimits(TenantRepository tenants, UsageService usage) {
		this.tenants = tenants;
		this.usage = usage;
	}

	public void requireRoomForDevice(long tenantId) {
		Plan plan = planOf(tenantId);
		if (plan.getMaxDevices() != null && usage.of(tenantId).devices() >= plan.getMaxDevices()) {
			throw new PlanLimitException("O plano " + plan.getName() + " permite até " + plan.getMaxDevices()
					+ " TV(s) e todas já estão em uso. Exclua uma TV ou peça um plano maior.");
		}
	}

	public void requireRoomForUpload(long tenantId, long bytes) {
		Plan plan = planOf(tenantId);
		if (plan.getMaxStorageMb() != null) {
			long limit = plan.getMaxStorageMb() * 1024L * 1024L;
			if (usage.of(tenantId).storageBytes() + bytes > limit) {
				throw new PlanLimitException("O plano " + plan.getName() + " permite até " + plan.getMaxStorageMb()
						+ " MB de mídias e este arquivo passaria disso. Exclua mídias que não usa mais ou peça um plano maior.");
			}
		}
	}

	public void requireRoomForUser(long tenantId) {
		Plan plan = planOf(tenantId);
		if (plan.getMaxUsers() != null && usage.of(tenantId).users() >= plan.getMaxUsers()) {
			throw new PlanLimitException("O plano " + plan.getName() + " permite até " + plan.getMaxUsers()
					+ " usuário(s) e todos já estão em uso. Desative um usuário ou peça um plano maior.");
		}
	}

	private Plan planOf(long tenantId) {
		return tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Cliente não encontrado: " + tenantId)).getPlan();
	}

}
