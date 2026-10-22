package com.digitalsignage.backend.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.billing.PlatformSettingsRepository;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.mail.EmailService;
import com.digitalsignage.backend.mail.EmailTemplates;

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
	private final EmailService email;
	private final PlatformSettingsRepository platform;

	public AlertNotifier(AlertRepository alerts, DeviceRepository devices, MonitoringService monitoring, WebhookNotifier webhook, EmailService email,
			PlatformSettingsRepository platform) {
		this.alerts = alerts;
		this.devices = devices;
		this.monitoring = monitoring;
		this.webhook = webhook;
		this.email = email;
		this.platform = platform;
	}

	/** Devolve quantos avisos foram enviados. */
	@Transactional
	public int notifyPending(Instant now) {
		MonitoringSettings settings = monitoring.settings();
		String url = settings.getWebhookUrl();
		boolean hook = url != null && !url.isBlank();
		List<String> recipients = recipients(settings);
		boolean mail = !recipients.isEmpty() && email.enabled();
		if (!hook && !mail) {
			return 0;
		}
		String company = company();
		int sent = 0;
		for (Alert alert : alerts.findNotYetNotified()) {
			boolean ok = false;
			if (hook) {
				ok |= webhook.send(url, "⚠️ " + alert.getMessage(), payload("alert.opened", alert)).ok();
			}
			if (mail) {
				EmailTemplates.Message message = EmailTemplates.tvAlert(company, alert.getMessage(), false);
				ok |= email.sendToAll(recipients, message.subject(), message.body()) > 0;
			}
			if (ok || Duration.between(alert.getOpenedAt(), now).compareTo(GIVE_UP_AFTER) > 0) {
				alert.markNotified(now);
			}
			sent += ok ? 1 : 0;
		}
		for (Alert alert : alerts.findResolvedNotYetNotified()) {
			if (!settings.isNotifyOnResolve()) {
				alert.markResolutionNotified(now);
				continue;
			}
			boolean ok = false;
			if (hook) {
				ok |= webhook.send(url, "✅ Resolvido: " + alert.getMessage(), payload("alert.resolved", alert)).ok();
			}
			if (mail) {
				EmailTemplates.Message message = EmailTemplates.tvAlert(company, alert.getMessage(), true);
				ok |= email.sendToAll(recipients, message.subject(), message.body()) > 0;
			}
			if (ok || Duration.between(alert.getResolvedAt(), now).compareTo(GIVE_UP_AFTER) > 0) {
				alert.markResolutionNotified(now);
			}
			sent += ok ? 1 : 0;
		}
		return sent;
	}

	/** Os endereços de e-mail dos avisos (separados por vírgula, ponto e vírgula ou espaço). */
	static List<String> recipients(MonitoringSettings settings) {
		String text = settings.getAlertEmail();
		if (text == null || text.isBlank()) {
			return List.of();
		}
		return Arrays.stream(text.split("[,;\\s]+")).map(String::strip).filter(address -> !address.isEmpty()).distinct().toList();
	}

	private String company() {
		return platform.findAll().stream().findFirst().map(s -> s.getCompanyName()).filter(name -> name != null && !name.isBlank()).orElse("Vyzzu");
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
