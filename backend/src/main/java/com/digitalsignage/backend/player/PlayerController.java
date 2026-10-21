package com.digitalsignage.backend.player;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
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

import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceConnection;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.device.DeviceStatus;
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
	private final MonitoringService monitoringService;
	private final DeviceLookup deviceLookup;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public PlayerController(
			DeviceRepository deviceRepository,
			PlayerConfigService playerConfigService,
			CampaignService campaignService,
			MonitoringService monitoringService,
			DeviceLookup deviceLookup,
			Clock clock) {
		this.deviceRepository = deviceRepository;
		this.playerConfigService = playerConfigService;
		this.campaignService = campaignService;
		this.monitoringService = monitoringService;
		this.deviceLookup = deviceLookup;
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
					.map(PlayerStatusResponse::from)
					.orElseThrow(() -> new NoSuchElementException("Dispositivo não encontrado: " + known.get().id())));
		}
		return TenantContext.callAs(TenantContext.POOL_ID, () -> registerNew(tokenHash, request.playerVersion()));
	}

	@GetMapping("/status")
	public PlayerStatusResponse status(@AuthenticationPrincipal Long deviceId) {
		return PlayerStatusResponse.from(findOrThrow(deviceId));
	}

	@GetMapping("/config")
	public PlayerConfigResponse config(@AuthenticationPrincipal Long deviceId) {
		return playerConfigService.buildFor(findOrThrow(deviceId));
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
		deviceRepository.save(device);

		CampaignService.Effective effective = campaignService.effectiveFor(device);
		monitoringService.onHeartbeat(device, before, previousLastSeen, previousVersion, effective, request, now);

		PlayerConfigResponse desired = playerConfigService.buildFor(device, effective);
		long updateCheckToken = device.getUpdateRequestedAt() == null ? 0 : device.getUpdateRequestedAt().toEpochMilli();
		return new PlayerHeartbeatResponse(desired.playlistId(), desired.configVersion(), now, updateCheckToken, desired.campaignId());
	}

	private PlayerStatusResponse registerNew(String tokenHash, String playerVersion) {
		// Registros pendentes abandonados (TV desligada, código nunca digitado) não ficam acumulando.
		deviceRepository.deleteByStatusAndDeviceTokenHashIsNotNullAndCreatedAtBefore(
				DeviceStatus.PENDING.name(), Instant.now().minus(PENDING_TTL));

		if (deviceRepository.countByStatusAndDeviceTokenHashIsNotNull(DeviceStatus.PENDING.name()) >= MAX_PENDING_DEVICES) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitos dispositivos aguardando ativação");
		}

		Device device = Device.selfRegistered(generateUniqueCode(), tokenHash, playerVersion);
		return PlayerStatusResponse.from(deviceRepository.save(device));
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
