package com.digitalsignage.backend.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.device.DeviceRepository;

/** A história de uma TV (linha do tempo). */
@RestController
@RequestMapping("/api/v1/devices/{deviceId}/events")
public class DeviceEventController {

	public record EventResponse(Long id, Long deviceId, String type, String level, String message, Instant occurredAt) {

		static EventResponse from(DeviceEvent event) {
			return new EventResponse(event.getId(), event.getDeviceId(), event.getType(), event.getLevel(), event.getMessage(), event.getOccurredAt());
		}
	}

	private final MonitoringService monitoring;
	private final DeviceRepository devices;

	public DeviceEventController(MonitoringService monitoring, DeviceRepository devices) {
		this.monitoring = monitoring;
		this.devices = devices;
	}

	/**
	 * Mais recentes primeiro. Para a página seguinte, passe {@code beforeId} com o id do último evento recebido.
	 * Filtros opcionais: {@code type} (ex.: PLAYER_LOG, OFFLINE) e {@code level} (INFO, WARN, ERROR).
	 */
	@GetMapping
	public List<EventResponse> list(
			@PathVariable Long deviceId,
			@RequestParam(required = false) Long beforeId,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String level,
			@RequestParam(required = false, defaultValue = "50") int limit) {
		if (!devices.existsById(deviceId)) {
			throw new NoSuchElementException("Dispositivo não encontrado: " + deviceId);
		}
		return monitoring.history(deviceId, beforeId, type, level, limit).stream().map(EventResponse::from).toList();
	}

}
