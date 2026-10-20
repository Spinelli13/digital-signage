package com.digitalsignage.backend.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada minuto: percebe TVs que pararam de falar, abre e fecha alertas, avisa o webhook e, uma vez por hora,
 * apaga o histórico antigo. Desligável (app.monitoring.enabled=false) para os testes controlarem o tempo.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringJob {

	private static final Logger log = LoggerFactory.getLogger(MonitoringJob.class);
	private static final Duration PURGE_EVERY = Duration.ofHours(1);

	private final MonitoringService monitoring;
	private final AlertNotifier notifier;
	private final Clock clock;
	private volatile Instant lastPurge = Instant.EPOCH;

	public MonitoringJob(MonitoringService monitoring, AlertNotifier notifier, Clock clock) {
		this.monitoring = monitoring;
		this.notifier = notifier;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.monitoring.interval-ms:60000}", initialDelayString = "${app.monitoring.initial-delay-ms:60000}")
	public void tick() {
		Instant now = clock.instant();
		try {
			monitoring.evaluate(now);
			notifier.notifyPending(now);
			if (Duration.between(lastPurge, now).compareTo(PURGE_EVERY) >= 0) {
				int removed = monitoring.purge(now);
				lastPurge = now;
				if (removed > 0) {
					log.info("Histórico antigo apagado: {} evento(s)", removed);
				}
			}
		} catch (RuntimeException e) {
			// uma falha aqui nunca pode derrubar o servidor nem impedir o próximo ciclo
			log.error("Falha no ciclo de monitoramento", e);
		}
	}

}
