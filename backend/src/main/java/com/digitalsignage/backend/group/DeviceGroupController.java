package com.digitalsignage.backend.group;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

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

/** Grupos de TVs. Quem está no grupo fica registrado em cada TV (Device.groupIds). */
@RestController
@RequestMapping("/api/v1/groups")
public class DeviceGroupController {

	private final DeviceGroupRepository groupRepository;
	private final DeviceRepository deviceRepository;

	public DeviceGroupController(DeviceGroupRepository groupRepository, DeviceRepository deviceRepository) {
		this.groupRepository = groupRepository;
		this.deviceRepository = deviceRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<DeviceGroupResponse> list() {
		return groupRepository.findAll().stream()
				.sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
				.map(group -> responseFor(group, deviceRepository.findByGroupId(group.getId())))
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public DeviceGroupResponse create(@Valid @RequestBody DeviceGroupRequest request) {
		String name = request.name().trim();
		if (groupRepository.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe um grupo chamado \"" + name + "\"");
		}
		DeviceGroup group = groupRepository.save(new DeviceGroup(name, blankToNull(request.description())));
		if (request.deviceIds() != null) {
			replaceMembers(group.getId(), request.deviceIds());
		}
		return responseFor(group, deviceRepository.findByGroupId(group.getId()));
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public DeviceGroupResponse get(@PathVariable Long id) {
		return responseFor(findOrThrow(id), deviceRepository.findByGroupId(id));
	}

	@PutMapping("/{id}")
	@Transactional
	public DeviceGroupResponse update(@PathVariable Long id, @Valid @RequestBody DeviceGroupRequest request) {
		DeviceGroup group = findOrThrow(id);
		String name = request.name().trim();
		if (groupRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new ConflictException("Já existe um grupo chamado \"" + name + "\"");
		}
		group.setName(name);
		group.setDescription(blankToNull(request.description()));
		groupRepository.save(group);
		if (request.deviceIds() != null) {
			replaceMembers(id, request.deviceIds());
		}
		return responseFor(group, deviceRepository.findByGroupId(id));
	}

	/** Define exatamente quem está no grupo (quem não estiver na lista sai). */
	@PutMapping("/{id}/devices")
	@Transactional
	public DeviceGroupResponse setMembers(@PathVariable Long id, @RequestBody GroupMembersRequest request) {
		DeviceGroup group = findOrThrow(id);
		replaceMembers(id, request.deviceIds() == null ? List.of() : request.deviceIds());
		return responseFor(group, deviceRepository.findByGroupId(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		groupRepository.delete(findOrThrow(id));
	}

	private void replaceMembers(Long groupId, List<Long> requestedIds) {
		Set<Long> wanted = new LinkedHashSet<>(requestedIds);
		List<Device> wantedDevices = deviceRepository.findAllById(wanted);
		if (wantedDevices.size() != wanted.size()) {
			throw new NoSuchElementException("Dispositivo não encontrado entre: " + wanted);
		}
		for (Device current : deviceRepository.findByGroupId(groupId)) {
			if (!wanted.contains(current.getId())) {
				current.getGroupIds().remove(groupId);
				deviceRepository.save(current);
			}
		}
		for (Device device : wantedDevices) {
			if (device.getGroupIds().add(groupId)) {
				deviceRepository.save(device);
			}
		}
	}

	private DeviceGroupResponse responseFor(DeviceGroup group, List<Device> members) {
		Instant now = Instant.now();
		long online = members.stream()
				.filter(device -> DeviceConnection.of(device, now) == DeviceConnection.ONLINE)
				.count();
		return DeviceGroupResponse.from(group, members.stream().map(Device::getId).toList(), online);
	}

	private DeviceGroup findOrThrow(Long id) {
		return groupRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Grupo não encontrado: " + id));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
