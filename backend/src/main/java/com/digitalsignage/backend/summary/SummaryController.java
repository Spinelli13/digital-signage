package com.digitalsignage.backend.summary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceConnection;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.group.DeviceGroupRepository;
import com.digitalsignage.backend.location.Location;
import com.digitalsignage.backend.location.LocationRepository;
import com.digitalsignage.backend.media.MediaRepository;
import com.digitalsignage.backend.playlist.PlaylistRepository;

@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {

	private static final int MAX_ATTENTION = 20;

	private final DeviceRepository deviceRepository;
	private final LocationRepository locationRepository;
	private final DeviceGroupRepository groupRepository;
	private final MediaRepository mediaRepository;
	private final PlaylistRepository playlistRepository;

	public SummaryController(
			DeviceRepository deviceRepository,
			LocationRepository locationRepository,
			DeviceGroupRepository groupRepository,
			MediaRepository mediaRepository,
			PlaylistRepository playlistRepository) {
		this.deviceRepository = deviceRepository;
		this.locationRepository = locationRepository;
		this.groupRepository = groupRepository;
		this.mediaRepository = mediaRepository;
		this.playlistRepository = playlistRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public SummaryResponse summary() {
		Instant now = Instant.now();
		List<Device> devices = deviceRepository.findAll();
		Map<Long, DeviceConnection> connections = new LinkedHashMap<>();
		devices.forEach(device -> connections.put(device.getId(), DeviceConnection.of(device, now)));

		long online = count(connections, DeviceConnection.ONLINE);
		SummaryResponse.DeviceCounts counts = new SummaryResponse.DeviceCounts(
				devices.size(),
				online,
				count(connections, DeviceConnection.OFFLINE),
				count(connections, DeviceConnection.NEVER),
				count(connections, DeviceConnection.PENDING),
				count(connections, DeviceConnection.DISABLED));

		return new SummaryResponse(
				counts,
				locationRepository.count(),
				groupRepository.count(),
				mediaRepository.count(),
				playlistRepository.count(),
				byLocation(devices, connections),
				attention(devices, connections));
	}

	private List<SummaryResponse.LocationSummary> byLocation(List<Device> devices, Map<Long, DeviceConnection> connections) {
		List<SummaryResponse.LocationSummary> result = new ArrayList<>();
		for (Location location : locationRepository.findAll()) {
			List<Device> inLocation = devices.stream()
					.filter(device -> device.getLocation() != null && location.getId().equals(device.getLocation().getId()))
					.toList();
			result.add(summaryOf(location.getId(), location.getName(), inLocation, connections));
		}
		result.sort(Comparator.comparing(SummaryResponse.LocationSummary::name, String.CASE_INSENSITIVE_ORDER));

		List<Device> withoutLocation = devices.stream().filter(device -> device.getLocation() == null).toList();
		if (!withoutLocation.isEmpty()) {
			result.add(summaryOf(null, "Sem local", withoutLocation, connections));
		}
		return result;
	}

	private static SummaryResponse.LocationSummary summaryOf(
			Long id, String name, List<Device> devices, Map<Long, DeviceConnection> connections) {
		long online = devices.stream().filter(device -> connections.get(device.getId()) == DeviceConnection.ONLINE).count();
		return new SummaryResponse.LocationSummary(id, name, devices.size(), online);
	}

	/** Ordem de urgência: offline (era para estar no ar), nunca conectou, aguardando ativação. Desativadas não alertam. */
	private List<SummaryResponse.AttentionDevice> attention(List<Device> devices, Map<Long, DeviceConnection> connections) {
		List<DeviceConnection> order = List.of(DeviceConnection.OFFLINE, DeviceConnection.NEVER, DeviceConnection.PENDING);
		return devices.stream()
				.filter(device -> order.contains(connections.get(device.getId())))
				.sorted(Comparator
						.comparingInt((Device device) -> order.indexOf(connections.get(device.getId())))
						.thenComparing(Device::getId))
				.limit(MAX_ATTENTION)
				.map(device -> new SummaryResponse.AttentionDevice(
						device.getId(),
						device.getName(),
						device.getLocation() == null ? null : device.getLocation().getName(),
						connections.get(device.getId()).name(),
						device.getLastSeenAt()))
				.toList();
	}

	private static long count(Map<Long, DeviceConnection> connections, DeviceConnection wanted) {
		return connections.values().stream().filter(connection -> connection == wanted).count();
	}

}
