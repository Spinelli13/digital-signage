package com.digitalsignage.backend.monitoring;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceConnection;
import com.digitalsignage.backend.device.DeviceRepository;

/** Alertas abertos/resolvidos e as configurações de monitoramento (limites e webhook). */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

	public record AlertResponse(
			Long id,
			Long deviceId,
			String deviceName,
			String location,
			AlertType type,
			String message,
			Instant openedAt,
			Instant resolvedAt,
			boolean open,
			boolean notified) {
	}

	public record SettingsResponse(
			int offlineAfterMinutes,
			int neverConnectedAfterHours,
			int errorBurstCount,
			boolean webhookConfigured,
			/** Só o host e o final do endereço: a URL inteira é um segredo e nunca volta para o painel. */
			String webhookHint,
			boolean notifyOnResolve,
			int eventsRetentionDays,
			int playLogRetentionDays) {
	}

	/** Campos ausentes não mudam. {@code webhookUrl}: vazio ("") remove o webhook. */
	public record SettingsRequest(
			Integer offlineAfterMinutes,
			Integer neverConnectedAfterHours,
			Integer errorBurstCount,
			String webhookUrl,
			Boolean notifyOnResolve,
			Integer eventsRetentionDays,
			Integer playLogRetentionDays) {
	}

	public record TestResult(boolean ok, int status, String error) {
	}

	private final AlertRepository alerts;
	private final DeviceRepository devices;
	private final MonitoringService monitoring;
	private final MonitoringSettingsRepository settingsRepository;
	private final WebhookUrlPolicy policy;
	private final WebhookNotifier webhook;
	private final Clock clock;

	public AlertController(
			AlertRepository alerts,
			DeviceRepository devices,
			MonitoringService monitoring,
			MonitoringSettingsRepository settingsRepository,
			WebhookUrlPolicy policy,
			WebhookNotifier webhook,
			Clock clock) {
		this.alerts = alerts;
		this.devices = devices;
		this.monitoring = monitoring;
		this.settingsRepository = settingsRepository;
		this.policy = policy;
		this.webhook = webhook;
		this.clock = clock;
	}

	/** {@code status=open} só os abertos; qualquer outro valor (ou nenhum) traz também os resolvidos. Abertos primeiro. */
	@GetMapping
	@Transactional(readOnly = true)
	public List<AlertResponse> list(
			@RequestParam(required = false) String status,
			@RequestParam(required = false, defaultValue = "100") int limit) {
		boolean onlyOpen = "open".equalsIgnoreCase(status);
		List<Alert> found = alerts.findRecent(PageRequest.of(0, Math.max(1, Math.min(limit, 500)))).stream()
				.filter(alert -> !onlyOpen || alert.isOpen())
				.toList();
		Map<Long, Device> byId = devices.findAllById(found.stream().map(Alert::getDeviceId).distinct().toList()).stream()
				.collect(Collectors.toMap(Device::getId, Function.identity()));
		return found.stream().map(alert -> toResponse(alert, byId.get(alert.getDeviceId()))).toList();
	}

	@GetMapping("/settings")
	@Transactional(readOnly = true)
	public SettingsResponse settings() {
		return toResponse(monitoring.settings());
	}

	@PutMapping("/settings")
	@Transactional
	public SettingsResponse update(@RequestBody SettingsRequest request) {
		MonitoringSettings settings = monitoring.settings();
		if (request.offlineAfterMinutes() != null) {
			// no mínimo 2 min: é a janela em que o servidor ainda considera a TV online (menos que isso alertaria antes de a TV constar como offline)
				settings.setOfflineAfterMinutes(inRange(request.offlineAfterMinutes(), (int) DeviceConnection.ONLINE_WINDOW.toMinutes(), 1440, "O tempo sem contato para alertar"));
		}
		if (request.neverConnectedAfterHours() != null) {
			settings.setNeverConnectedAfterHours(inRange(request.neverConnectedAfterHours(), 1, 720, "O prazo para uma TV ativada se conectar"));
		}
		if (request.errorBurstCount() != null) {
			settings.setErrorBurstCount(inRange(request.errorBurstCount(), 1, 100, "A quantidade de erros que gera alerta"));
		}
		if (request.eventsRetentionDays() != null) {
			settings.setEventsRetentionDays(inRange(request.eventsRetentionDays(), 7, 730, "O prazo de guarda do histórico"));
		}
		if (request.playLogRetentionDays() != null) {
			settings.setPlayLogRetentionDays(inRange(request.playLogRetentionDays(), 7, 365, "O prazo de guarda das exibições detalhadas"));
		}
		if (request.notifyOnResolve() != null) {
			settings.setNotifyOnResolve(request.notifyOnResolve());
		}
		if (request.webhookUrl() != null) {
			if (request.webhookUrl().isBlank()) {
				settings.setWebhookUrl(null);
			} else {
				policy.validate(request.webhookUrl());
				settings.setWebhookUrl(request.webhookUrl().trim());
			}
		}
		settingsRepository.save(settings);
		return toResponse(settings);
	}

	/** Manda uma mensagem de teste ao webhook configurado, para a pessoa conferir que chega. */
	@PostMapping("/settings/test")
	@Transactional(readOnly = true)
	public TestResult testWebhook() {
		String url = monitoring.settings().getWebhookUrl();
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Configure o endereço do webhook antes de testar");
		}
		WebhookNotifier.Result result = webhook.send(url,
				"✅ Teste do Vyzzu: os alertas chegarão aqui.",
				Map.of("event", "test", "at", clock.instant().toString()));
		return new TestResult(result.ok(), result.status(), result.error());
	}

	private static int inRange(int value, int min, int max, String what) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(what + " precisa estar entre " + min + " e " + max);
		}
		return value;
	}

	private static SettingsResponse toResponse(MonitoringSettings settings) {
		boolean configured = settings.getWebhookUrl() != null && !settings.getWebhookUrl().isBlank();
		return new SettingsResponse(
				settings.getOfflineAfterMinutes(),
				settings.getNeverConnectedAfterHours(),
				settings.getErrorBurstCount(),
				configured,
				configured ? WebhookUrlPolicy.hint(settings.getWebhookUrl()) : null,
				settings.isNotifyOnResolve(),
				settings.getEventsRetentionDays(),
				settings.getPlayLogRetentionDays());
	}

	private static AlertResponse toResponse(Alert alert, Device device) {
		return new AlertResponse(
				alert.getId(),
				alert.getDeviceId(),
				device == null ? null : device.getName(),
				device == null || device.getLocation() == null ? null : device.getLocation().getName(),
				alert.getType(),
				alert.getMessage(),
				alert.getOpenedAt(),
				alert.getResolvedAt(),
				alert.isOpen(),
				alert.getNotifiedAt() != null);
	}

}
