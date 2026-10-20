package com.digitalsignage.backend.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.device.DeviceRepository;

/**
 * Avisa o webhook quando um alerta abre e (se configurado) quando ele se resolve. Sem webhook, nada é enviado e nada
 * é marcado: ao configurar um depois, só os alertas que ainda estão abertos são avisados.
 */
@Service
public class AlertNotifier {

	/** Se o webhook não responde, tenta de novo a cada ciclo por até esse tempo; depois desiste (e marca como avisado). */
	static final Duration GIVE_UP_AFTER = Duration.ofHours(1);

	private final AlertRepository alerts;
	private final DeviceRepository devices;
	private final MonitoringService monitoring;
	private final WebhookNotifier webhook;

	public AlertNotifier(AlertRepository alerts, DeviceRepository devices, MonitoringService monitoring, WebhookNotifier webhook) {
		this.alerts = alerts;
		this.devices = devices;
		this.monitoring = monitoring;
		this.webhook = webhook;
	}

	/** Devolve quantos avisos foram enviados. */
	@Transactional
	public int notifyPending(Instant now) {
		MonitoringSettings settings = monitoring.settings();
		String url = settings.getWebhookUrl();
		if (url == null || url.isBlank()) {
			return 0;
		}
		int sent = 0;
		for (Alert alert : alerts.findNotYetNotified()) {
			WebhookNotifier.Result result = webhook.send(url, "⚠️ " + alert.getMessage(), payload("alert.opened", alert));
			if (result.ok() || Duration.between(alert.getOpenedAt(), now).compareTo(GIVE_UP_AFTER) > 0) {
				alert.markNotified(now);
			}
			sent += result.ok() ? 1 : 0;
		}
		for (Alert alert : alerts.findResolvedNotYetNotified()) {
			if (!settings.isNotifyOnResolve()) {
				alert.markResolutionNotified(now);
				continue;
			}
			WebhookNotifier.Result result = webhook.send(url, "✅ Resolvido: " + alert.getMessage(), payload("alert.resolved", alert));
			if (result.ok() || Duration.between(alert.getResolvedAt(), now).compareTo(GIVE_UP_AFTER) > 0) {
				alert.markResolutionNotified(now);
			}
			sent += result.ok() ? 1 : 0;
		}
		return sent;
	}

	private Map<String, Object> payload(String event, Alert alert) {
		Map<String, Object> alertData = new LinkedHashMap<>();
		alertData.put("id", alert.getId());
		alertData.put("type", alert.getType().name());
		alertData.put("message", alert.getMessage());
		alertData.put("openedAt", alert.getOpenedAt().toString());
		alertData.put("resolvedAt", alert.getResolvedAt() == null ? null : alert.getResolvedAt().toString());
		alertData.put("deviceId", alert.getDeviceId());
		devices.findById(alert.getDeviceId()).ifPresent(device -> {
			alertData.put("deviceName", device.getName());
			alertData.put("location", device.getLocation() == null ? null : device.getLocation().getName());
		});
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("event", event);
		payload.put("alert", alertData);
		return payload;
	}

}
