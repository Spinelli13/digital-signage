package com.digitalsignage.backend.player;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.device.DeviceStatus;
import com.digitalsignage.backend.device.PlayerConfigResponse;
import com.digitalsignage.backend.device.PlayerConfigService;
import com.digitalsignage.backend.security.TokenHasher;

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
	private final SecureRandom random = new SecureRandom();

	public PlayerController(DeviceRepository deviceRepository, PlayerConfigService playerConfigService) {
		this.deviceRepository = deviceRepository;
		this.playerConfigService = playerConfigService;
	}

	/** Idempotente: o mesmo token registrado de novo (ex.: após reinício) devolve o estado atual. */
	@PostMapping("/register")
	public PlayerStatusResponse register(@Valid @RequestBody PlayerRegisterRequest request) {
		String tokenHash = TokenHasher.sha256Hex(request.deviceToken());

		return deviceRepository.findByDeviceTokenHash(tokenHash)
				.map(PlayerStatusResponse::from)
				.orElseGet(() -> registerNew(tokenHash, request.playerVersion()));
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
	public PlayerHeartbeatResponse heartbeat(
			@AuthenticationPrincipal Long deviceId,
			@Valid @RequestBody PlayerHeartbeatRequest request) {
		Device device = findOrThrow(deviceId);
		device.recordHeartbeat(request.playerVersion(), request.configVersion());
		deviceRepository.save(device);

		PlayerConfigResponse desired = playerConfigService.buildFor(device);
		long updateCheckToken = device.getUpdateRequestedAt() == null ? 0 : device.getUpdateRequestedAt().toEpochMilli();
		return new PlayerHeartbeatResponse(desired.playlistId(), desired.configVersion(), Instant.now(), updateCheckToken);
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
