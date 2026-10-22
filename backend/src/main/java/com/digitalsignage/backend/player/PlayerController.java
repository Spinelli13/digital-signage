package com.digitalsignage.backend.player;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.digitalsignage.backend.campaign.Campaign;
import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceConnection;
import com.digitalsignage.backend.device.DeviceDiagnosticService;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.device.DeviceStatus;
import com.digitalsignage.backend.device.OfflinePlanService;
import com.digitalsignage.backend.device.PlayerConfigResponse;
import com.digitalsignage.backend.device.PlayerConfigService;
import com.digitalsignage.backend.monitoring.MonitoringService;
import com.digitalsignage.backend.security.DeviceLookup;
import com.digitalsignage.backend.security.TokenHasher;
import com.digitalsignage.backend.tenant.TenantContext;

import jakarta.validation.Valid;

/** API consumida pelo Player (autenticada por X-Device-Token, exceto o registro inicial). */
@RestController
@RequestMapping("/api/v1/player")
public class PlayerController {

	// Sem 0/O/1/I para o código ser inequívoco quando lido numa TV a distância.
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;
	private static final int MAX_PENDING_DEVICES = 100;
	private static final Duration PENDING_TTL = Duration.ofHours(24);

	private final DeviceRepository deviceRepository;
	private final PlayerConfigService playerConfigService;
	private final CampaignService campaignService;
	private final OfflinePlanService offlinePlanService;
	private final MonitoringService monitoringService;
	private final DeviceLookup deviceLookup;
	private final PanelUrl panelUrl;
	private final DeviceDiagnosticService diagnostics;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public PlayerController(
			DeviceRepository deviceRepository,
			PlayerConfigService playerConfigService,
			CampaignService campaignService,
			OfflinePlanService offlinePlanService,
			MonitoringService monitoringService,
			DeviceLookup deviceLookup,
			PanelUrl panelUrl,
			DeviceDiagnosticService diagnostics,
			Clock clock) {
		this.deviceRepository = deviceRepository;
		this.playerConfigService = playerConfigService;
		this.campaignService = campaignService;
		this.offlinePlanService = offlinePlanService;
		this.monitoringService = monitoringService;
		this.deviceLookup = deviceLookup;
		this.panelUrl = panelUrl;
		this.diagnostics = diagnostics;
		this.clock = clock;
	}

	/** Idempotente: o mesmo token registrado de novo (ex.: após reinício) devolve o estado atual. */
	@PostMapping("/register")
	public PlayerStatusResponse register(@Valid @RequestBody PlayerRegisterRequest request) {
		String tokenHash = TokenHasher.sha256Hex(request.deviceToken());

		// Uma TV já conhecida é de algum cliente; uma TV nova ainda não é de ninguém e fica na reserva até um cliente
		// digitar o código dela. Em cada caso a consulta roda "como" o dono (o registro é anônimo, sem filtro de login).
		var known = deviceLookup.byTokenHash(tokenHash);
		if (known.isPresent()) {
			return TenantContext.callAs(known.get().tenantId(), () -> deviceRepository.findById(known.get().id())
					.map(device -> PlayerStatusResponse.from(device, panelUrl))
					.orElseThrow(() -> new NoSuchElementException("Dispositivo não encontrado: " + known.get().id())));
		}
		return TenantContext.callAs(TenantContext.POOL_ID, () -> registerNew(tokenHash, request.playerVersion()));
	}

	/** O relatório de diagnóstico que a TV montou (pedido pelo painel ou aberto por quem está na frente dela). */
	@PostMapping("/diagnostics")
	@Transactional
	public void diagnostics(@AuthenticationPrincipal Long deviceId, @Valid @RequestBody PlayerDiagnosticRequest request) {
		findOrThrow(deviceId);
		diagnostics.save(deviceId, request.report().strip(), request.issues() == null ? 0 : request.issues(), clock.instant());
	}

	@GetMapping("/status")
	public PlayerStatusResponse status(@AuthenticationPrincipal Long deviceId) {
		return PlayerStatusResponse.from(findOrThrow(deviceId), panelUrl);
	}

	@GetMapping("/config")
	public PlayerConfigResponse config(@AuthenticationPrincipal Long deviceId) {
		Device device = findOrThrow(deviceId);
		List<Campaign> enabled = campaignService.enabled();
		CampaignService.Effective effective = CampaignService.effectiveFor(device, clock.instant(), enabled);
		// A agenda dos próximos dias vai junto: se a internet cair, a TV continua trocando de campanha na hora certa.
		return playerConfigService.buildFor(device, effective).withPlan(offlinePlanService.build(device, enabled));
	}

	@PostMapping("/heartbeat")
	@Transactional
	public PlayerHeartbeatResponse heartbeat(
			@AuthenticationPrincipal Long deviceId,
			@Valid @RequestBody PlayerHeartbeatRequest request) {
		Device device = findOrThrow(deviceId);
		Instant now = clock.instant();
		DeviceConnection before = DeviceConnection.of(device, now);
		Instant previousLastSeen = device.getLastSeenAt();
		String previousVersion = device.getPlayerVersion();

		device.recordHeartbeat(request.playerVersion(), request.configVersion(), now);
		device.setOfflinePlanUntil(request.planUntilMs() == null || request.planUntilMs() <= 0 ? null : Instant.ofEpochMilli(request.planUntilMs()));
		deviceRepository.save(device);

		List<Campaign> enabled = campaignService.enabled();
		CampaignService.Effective effective = CampaignService.effectiveFor(device, now, enabled);
		monitoringService.onHeartbeat(device, before, previousLastSeen, previousVersion, effective, request, now);

		PlayerConfigResponse desired = playerConfigService.buildFor(device, effective);
		long updateCheckToken = device.getUpdateRequestedAt() == null ? 0 : device.getUpdateRequestedAt().toEpochMilli();
		return new PlayerHeartbeatResponse(desired.playlistId(), desired.configVersion(), now, updateCheckToken, desired.campaignId(),
				offlinePlanService.versionFor(device, enabled),
				device.getDiagnosticRequestedAt() == null ? 0 : device.getDiagnosticRequestedAt().toEpochMilli());
	}

	private PlayerStatusResponse registerNew(String tokenHash, String playerVersion) {
		// Registros pendentes abandonados (TV desligada, código nunca digitado) não ficam acumulando.
		deviceRepository.deleteByStatusAndDeviceTokenHashIsNotNullAndCreatedAtBefore(
				DeviceStatus.PENDING.name(), Instant.now().minus(PENDING_TTL));

		if (deviceRepository.countByStatusAndDeviceTokenHashIsNotNull(DeviceStatus.PENDING.name()) >= MAX_PENDING_DEVICES) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitos dispositivos aguardando ativação");
		}

		Device device = Device.selfRegistered(generateUniqueCode(), tokenHash, playerVersion);
		return PlayerStatusResponse.from(deviceRepository.save(device), panelUrl);
	}

	private String generateUniqueCode() {
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder code = new StringBuilder(CODE_LENGTH);
			for (int i = 0; i < CODE_LENGTH; i++) {
				code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
			}
			if (!deviceRepository.existsByActivationCode(code.toString())) {
				return code.toString();
			}
		}
		throw new IllegalStateException("Não foi possível gerar um código de ativação único");
	}

	private Device findOrThrow(Long deviceId) {
		return deviceRepository.findById(deviceId)
				.orElseThrow(() -> new NoSuchElementException("Dispositivo não encontrado: " + deviceId));
	}

}
