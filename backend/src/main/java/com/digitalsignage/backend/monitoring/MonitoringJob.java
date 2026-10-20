package com.digitalsignage.backend.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantContext;
import com.digitalsignage.backend.tenant.TenantRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada minuto: percebe TVs que pararam de falar, abre e fecha alertas, avisa o webhook e, uma vez por hora,
 * apaga o histórico antigo. Desligável (app.monitoring.enabled=false) para os testes controlarem o tempo.
 */
@Component
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringJob {

	private static final Logger log = LoggerFactory.getLogger(MonitoringJob.class);
	private static final Duration PURGE_EVERY = Duration.ofHours(1);

	private final MonitoringService monitoring;
	private final AlertNotifier notifier;
	private final TenantRepository tenants;
	private final Clock clock;
	private volatile Instant lastPurge = Instant.EPOCH;

	public MonitoringJob(MonitoringService monitoring, AlertNotifier notifier, TenantRepository tenants, Clock clock) {
		this.monitoring = monitoring;
		this.notifier = notifier;
		this.tenants = tenants;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.monitoring.interval-ms:60000}", initialDelayString = "${app.monitoring.initial-delay-ms:60000}")
	public void tick() {
		Instant now = clock.instant();
		boolean purge = Duration.between(lastPurge, now).compareTo(PURGE_EVERY) >= 0;
		try {
			// Cada cliente é atendido por vez, "como" ele: o monitoramento de um nunca enxerga os dados de outro.
			for (Tenant tenant : tenants.findAllCustomers()) {
				try {
					TenantContext.runAs(tenant.getId(), () -> runFor(now, purge));
				} catch (RuntimeException e) {
					// a falha de um cliente nunca impede o ciclo dos outros nem derruba o servidor
					log.error("Falha no ciclo de monitoramento do cliente {}", tenant.getSlug(), e);
				}
			}
			if (purge) {
				lastPurge = now;
			}
		} catch (RuntimeException e) {
			log.error("Falha no ciclo de monitoramento", e);
		}
	}

	private void runFor(Instant now, boolean purge) {
		monitoring.evaluate(now);
		notifier.notifyPending(now);
		if (purge) {
			int removed = monitoring.purge(now);
			if (removed > 0) {
				log.info("Histórico antigo apagado: {} evento(s)", removed);
			}
		}
	}

}
