package com.digitalsignage.backend.device;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

	private final DeviceRepository deviceRepository;

	public DeviceController(DeviceRepository deviceRepository) {
		this.deviceRepository = deviceRepository;
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

		return DeviceResponse.from(deviceRepository.save(device));
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
