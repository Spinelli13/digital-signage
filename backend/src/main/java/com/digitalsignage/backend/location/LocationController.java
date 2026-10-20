package com.digitalsignage.backend.location;

import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceConnection;
import com.digitalsignage.backend.device.DeviceRepository;

import jakarta.validation.Valid;

/** "Pontos & Locais": onde as TVs estão. Apagar um local não apaga as TVs (elas ficam sem local). */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

	private final LocationRepository locationRepository;
	private final DeviceRepository deviceRepository;

	public LocationController(LocationRepository locationRepository, DeviceRepository deviceRepository) {
		this.locationRepository = locationRepository;
		this.deviceRepository = deviceRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<LocationResponse> list() {
		List<Device> devices = deviceRepository.findAll();
		return locationRepository.findAll().stream()
				.sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
				.map(location -> responseFor(location, devices))
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public LocationResponse create(@Valid @RequestBody LocationRequest request) {
		String name = request.name().trim();
		if (locationRepository.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe um local chamado \"" + name + "\"");
		}
		Location location = locationRepository.save(new Location(name, blankToNull(request.description())));
		return LocationResponse.from(location, 0, 0);
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public LocationResponse get(@PathVariable Long id) {
		return responseFor(findOrThrow(id), deviceRepository.findByLocationIdOrderById(id));
	}

	@PutMapping("/{id}")
	@Transactional
	public LocationResponse update(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
		Location location = findOrThrow(id);
		String name = request.name().trim();
		if (locationRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new ConflictException("Já existe um local chamado \"" + name + "\"");
		}
		location.setName(name);
		location.setDescription(blankToNull(request.description()));
		locationRepository.save(location);
		return responseFor(location, deviceRepository.findByLocationIdOrderById(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		locationRepository.delete(findOrThrow(id));
	}

	private LocationResponse responseFor(Location location, List<Device> devices) {
		Instant now = Instant.now();
		List<Device> inLocation = devices.stream()
				.filter(device -> device.getLocation() != null && location.getId().equals(device.getLocation().getId()))
				.toList();
		long online = inLocation.stream()
				.filter(device -> DeviceConnection.of(device, now) == DeviceConnection.ONLINE)
				.count();
		return LocationResponse.from(location, inLocation.size(), online);
	}

	private Location findOrThrow(Long id) {
		return locationRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Local não encontrado: " + id));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
