package com.digitalsignage.backend.device;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

	private final DeviceRepository deviceRepository;
	private final PlaylistRepository playlistRepository;
	private final PlayerConfigService playerConfigService;

	public DeviceController(
			DeviceRepository deviceRepository,
			PlaylistRepository playlistRepository,
			PlayerConfigService playerConfigService) {
		this.deviceRepository = deviceRepository;
		this.playlistRepository = playlistRepository;
		this.playerConfigService = playerConfigService;
	}

	@GetMapping
	public List<DeviceResponse> list() {
		return deviceRepository.findAll().stream().map(DeviceResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeviceResponse create(@Valid @RequestBody DeviceCreateRequest request) {
		Device device = new Device(request.name(), request.location());
		return DeviceResponse.from(deviceRepository.save(device));
	}

	@GetMapping("/{id}")
	public DeviceResponse get(@PathVariable Long id) {
		return DeviceResponse.from(findOrThrow(id));
	}

	@PutMapping("/{id}")
	public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest request) {
		Device device = findOrThrow(id);
		device.setName(request.name());
		device.setLocation(request.location());

		if (request.status() != null) {
			device.setStatus(DeviceStatus.valueOf(request.status().toUpperCase()).name());
		}

		if (request.currentPlaylistId() != null) {
			Playlist playlist = playlistRepository.findById(request.currentPlaylistId())
					.orElseThrow(() -> new NoSuchElementException(
							"Playlist não encontrada: " + request.currentPlaylistId()));
			device.setCurrentPlaylistId(playlist.getId());
		}

		return DeviceResponse.from(deviceRepository.save(device));
	}

	/**
	 * Vincula o dispositivo que está exibindo o código na TV: o administrador digita o código no painel,
	 * dá um nome ao dispositivo e a TV passa a receber configuração na próxima consulta.
	 */
	@PostMapping("/activate")
	public DeviceResponse activate(@Valid @RequestBody ActivateDeviceRequest request) {
		String code = request.activationCode().trim().toUpperCase();
		Device device = deviceRepository.findByActivationCodeAndStatus(code, DeviceStatus.PENDING.name())
				.orElseThrow(() -> new NoSuchElementException("Código de ativação inválido ou expirado"));

		device.activate(request.name(), request.location());
		return DeviceResponse.from(deviceRepository.save(device));
	}

	/**
	 * Tira a playlist do dispositivo (PUT com currentPlaylistId ausente significa "não mudar", então remover
	 * precisa de um endpoint próprio). Idempotente. A TV passa a receber playlist vazia no próximo heartbeat.
	 */
	@DeleteMapping("/{id}/playlist")
	public DeviceResponse removePlaylist(@PathVariable Long id) {
		Device device = findOrThrow(id);
		device.setCurrentPlaylistId(null);
		return DeviceResponse.from(deviceRepository.save(device));
	}

	/** Manda a TV verificar agora se há versão nova do Player (ela percebe no próximo heartbeat). */
	@PostMapping("/{id}/update-check")
	public DeviceResponse requestUpdateCheck(@PathVariable Long id) {
		Device device = findOrThrow(id);
		device.requestUpdateCheck();
		return DeviceResponse.from(deviceRepository.save(device));
	}

	/** O mesmo pedido para todas as TVs ativas. Devolve quantas foram avisadas. */
	@PostMapping("/update-check")
	public Map<String, Integer> requestUpdateCheckForAll() {
		List<Device> active = deviceRepository.findAll().stream()
				.filter(device -> DeviceStatus.ACTIVE.name().equals(device.getStatus()))
				.toList();
		active.forEach(Device::requestUpdateCheck);
		deviceRepository.saveAll(active);
		return Map.of("requested", active.size());
	}

	@GetMapping("/{id}/config")
	public PlayerConfigResponse config(@PathVariable Long id) {
		return playerConfigService.buildFor(findOrThrow(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		deviceRepository.delete(findOrThrow(id));
	}

	private Device findOrThrow(Long id) {
		return deviceRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Dispositivo não encontrado: " + id));
	}

}
