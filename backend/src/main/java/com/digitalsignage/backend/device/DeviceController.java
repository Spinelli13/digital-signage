package com.digitalsignage.backend.device;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

	private final DeviceRepository deviceRepository;
	private final DeviceService deviceService;
	private final PlayerConfigService playerConfigService;
	private final DeviceViews views;

	public DeviceController(
			DeviceRepository deviceRepository,
			DeviceService deviceService,
			PlayerConfigService playerConfigService,
			DeviceViews views) {
		this.deviceRepository = deviceRepository;
		this.deviceService = deviceService;
		this.playerConfigService = playerConfigService;
		this.views = views;
	}

	/**
	 * Lista as TVs. Todos os filtros são opcionais e se combinam: {@code connection} (ONLINE, OFFLINE, NEVER,
	 * PENDING, DISABLED), {@code locationId} ou {@code noLocation=true}, {@code groupId} e {@code q} (busca no nome
	 * da TV e no nome do local).
	 */
	@GetMapping
	public List<DeviceResponse> list(
			@RequestParam(required = false) String connection,
			@RequestParam(required = false) Long locationId,
			@RequestParam(required = false, defaultValue = "false") boolean noLocation,
			@RequestParam(required = false) Long groupId,
			@RequestParam(required = false) String q) {
		return deviceService.search(new DeviceFilter(parseConnection(connection), locationId, noLocation, groupId, q));
	}

	private static DeviceConnection parseConnection(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return DeviceConnection.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Situação inválida: " + value
					+ " (use ONLINE, OFFLINE, NEVER, PENDING ou DISABLED)");
		}
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public DeviceResponse create(@Valid @RequestBody DeviceCreateRequest request) {
		return views.of(deviceService.create(request));
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public DeviceResponse get(@PathVariable Long id) {
		return views.of(findOrThrow(id));
	}

	@PutMapping("/{id}")
	@Transactional
	public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest request) {
		return views.of(deviceService.update(id, request));
	}

	/**
	 * Vincula o dispositivo que está exibindo o código na TV: o administrador digita o código no painel,
	 * dá um nome ao dispositivo e a TV passa a receber configuração na próxima consulta.
	 */
	@PostMapping("/activate")
	@Transactional
	public DeviceResponse activate(@Valid @RequestBody ActivateDeviceRequest request) {
		return views.of(deviceService.activate(request));
	}

	/**
	 * Ação em várias TVs de uma vez (playlist, ativar/desativar, local, grupos, atualização do Player). O alvo
	 * pode ser uma lista de TVs, um grupo ou um local.
	 */
	@PostMapping("/bulk")
	public BulkResult bulk(@Valid @RequestBody BulkDeviceRequest request) {
		return deviceService.bulk(request);
	}

	/**
	 * Tira a playlist do dispositivo (PUT com currentPlaylistId ausente significa "não mudar", então remover
	 * precisa de um endpoint próprio). Idempotente. A TV passa a receber playlist vazia no próximo heartbeat.
	 */
	@DeleteMapping("/{id}/playlist")
	@Transactional
	public DeviceResponse removePlaylist(@PathVariable Long id) {
		Device device = findOrThrow(id);
		device.setCurrentPlaylistId(null);
		return views.of(deviceRepository.save(device));
	}

	/** Manda a TV verificar agora se há versão nova do Player (ela percebe no próximo heartbeat). */
	@PostMapping("/{id}/update-check")
	@Transactional
	public DeviceResponse requestUpdateCheck(@PathVariable Long id) {
		Device device = findOrThrow(id);
		device.requestUpdateCheck();
		return views.of(deviceRepository.save(device));
	}

	/** O mesmo pedido para todas as TVs ativas. Devolve quantas foram avisadas. */
	@PostMapping("/update-check")
	@Transactional
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
