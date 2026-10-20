package com.digitalsignage.backend.device;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.group.DeviceGroupRepository;
import com.digitalsignage.backend.layout.LayoutRepository;
import com.digitalsignage.backend.location.Location;
import com.digitalsignage.backend.location.LocationRepository;
import com.digitalsignage.backend.monitoring.MonitoringService;
import com.digitalsignage.backend.playlist.PlaylistRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/** Regras de várias TVs: filtros, local, grupos e ações em massa. */
@Service
public class DeviceService {

	private final DeviceRepository deviceRepository;
	private final LocationRepository locationRepository;
	private final DeviceGroupRepository groupRepository;
	private final PlaylistRepository playlistRepository;
	private final LayoutRepository layoutRepository;
	private final DeviceViews views;
	private final MonitoringService monitoring;
	private final Clock clock;

	public DeviceService(
			DeviceRepository deviceRepository,
			LocationRepository locationRepository,
			DeviceGroupRepository groupRepository,
			PlaylistRepository playlistRepository,
			LayoutRepository layoutRepository,
			DeviceViews views,
			MonitoringService monitoring,
			Clock clock) {
		this.deviceRepository = deviceRepository;
		this.locationRepository = locationRepository;
		this.groupRepository = groupRepository;
		this.playlistRepository = playlistRepository;
		this.layoutRepository = layoutRepository;
		this.views = views;
		this.monitoring = monitoring;
		this.clock = clock;
	}

	// ---------- consulta ----------

	@Transactional(readOnly = true)
	public List<DeviceResponse> search(DeviceFilter filter) {
		return views.of(deviceRepository.findAll(specificationFor(filter), Sort.by("id")));
	}

	private Specification<Device> specificationFor(DeviceFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			Instant threshold = Instant.now().minus(DeviceConnection.ONLINE_WINDOW);
			Predicate active = cb.equal(root.get("status"), DeviceStatus.ACTIVE.name());

			if (filter.connection() != null) {
				switch (filter.connection()) {
					case ONLINE -> {
						predicates.add(active);
						predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("lastSeenAt"), threshold));
					}
					case OFFLINE -> {
						predicates.add(active);
						predicates.add(cb.lessThan(root.<Instant>get("lastSeenAt"), threshold));
					}
					case NEVER -> {
						predicates.add(active);
						predicates.add(cb.isNull(root.get("lastSeenAt")));
					}
					case PENDING -> predicates.add(cb.equal(root.get("status"), DeviceStatus.PENDING.name()));
					case DISABLED -> predicates.add(cb.equal(root.get("status"), DeviceStatus.DISABLED.name()));
				}
			}

			if (filter.noLocation()) {
				predicates.add(cb.isNull(root.get("location")));
			} else if (filter.locationId() != null) {
				predicates.add(cb.equal(root.get("location").get("id"), filter.locationId()));
			}

			if (filter.groupId() != null) {
				Join<Device, Long> group = root.join("groupIds");
				predicates.add(cb.equal(group, filter.groupId()));
			}

			if (filter.query() != null && !filter.query().isBlank()) {
				String like = "%" + escapeLike(filter.query().trim().toLowerCase()) + "%";
				Join<Device, Location> location = root.join("location", JoinType.LEFT);
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("name")), like, '\\'),
						cb.like(cb.lower(location.get("name")), like, '\\')));
			}

			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	// ---------- criação e edição ----------

	@Transactional
	public Device create(DeviceCreateRequest request) {
		Device device = new Device(request.name(), resolveLocation(request.locationId(), request.location()));
		if (request.groupIds() != null) {
			replaceGroups(device, request.groupIds());
		}
		return deviceRepository.save(device);
	}

	@Transactional
	public Device update(Long id, DeviceUpdateRequest request) {
		Device device = find(id);
		String oldStatus = device.getStatus();
		device.setName(request.name());

		if (request.locationId() != null) {
			device.setLocation(resolveLocation(request.locationId(), null));
		} else if (request.location() != null) {
			device.setLocation(resolveLocation(null, request.location()));
		}

		if (request.status() != null) {
			device.setStatus(DeviceStatus.valueOf(request.status().toUpperCase()).name());
			monitoring.recordStatusChange(device, oldStatus, clock.instant());
		}

		if (request.currentPlaylistId() != null) {
			device.setCurrentPlaylistId(requirePlaylist(request.currentPlaylistId()));
		}

		if (request.layoutId() != null && !request.layoutId().equals(device.getLayoutId())) {
			device.setLayoutId(requireLayout(request.layoutId()));
			monitoring.recordLayoutChanged(device, layoutName(device.getLayoutId()), clock.instant());
		}

		if (request.groupIds() != null) {
			replaceGroups(device, request.groupIds());
		}

		return deviceRepository.save(device);
	}

	/**
	 * Vincula a TV que está exibindo o código: o administrador digita o código no painel, dá um nome (e, se quiser,
	 * local e grupos) e a TV passa a receber configuração na próxima consulta.
	 */
	@Transactional
	public Device activate(ActivateDeviceRequest request) {
		String code = request.activationCode().trim().toUpperCase();
		Device device = deviceRepository.findByActivationCodeAndStatus(code, DeviceStatus.PENDING.name())
				.orElseThrow(() -> new NoSuchElementException("Código de ativação inválido ou expirado"));

		device.activate(request.name(), resolveLocation(request.locationId(), request.location()));
		if (request.groupIds() != null) {
			replaceGroups(device, request.groupIds());
		}
		Device saved = deviceRepository.save(device);
		monitoring.recordActivated(saved, clock.instant());
		return saved;
	}

	/** Por id (precisa existir) ou por nome (acha o local de mesmo nome ou cria). Vazio = sem local. */
	@Transactional
	public Location resolveLocation(Long locationId, String locationName) {
		if (locationId != null) {
			return locationRepository.findById(locationId)
					.orElseThrow(() -> new NoSuchElementException("Local não encontrado: " + locationId));
		}
		if (locationName == null || locationName.isBlank()) {
			return null;
		}
		String name = locationName.trim();
		return locationRepository.findByNameIgnoreCase(name)
				.orElseGet(() -> locationRepository.save(new Location(name, null)));
	}

	private void replaceGroups(Device device, Collection<Long> groupIds) {
		Set<Long> wanted = new LinkedHashSet<>(groupIds);
		requireGroups(wanted);
		device.getGroupIds().clear();
		device.getGroupIds().addAll(wanted);
	}

	private void requireGroups(Collection<Long> ids) {
		long found = groupRepository.findAllById(ids).size();
		if (found != ids.size()) {
			throw new NoSuchElementException("Grupo não encontrado: " + ids);
		}
	}

	private Long requirePlaylist(Long playlistId) {
		return playlistRepository.findById(playlistId)
				.orElseThrow(() -> new NoSuchElementException("Playlist não encontrada: " + playlistId))
				.getId();
	}

	private Long requireLayout(Long layoutId) {
		return layoutRepository.findById(layoutId)
				.orElseThrow(() -> new NoSuchElementException("Layout não encontrado: " + layoutId))
				.getId();
	}

	private String layoutName(Long layoutId) {
		return layoutId == null ? null : layoutRepository.findById(layoutId).map(layout -> layout.getName()).orElse(null);
	}

	private Device find(Long id) {
		return deviceRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Dispositivo não encontrado: " + id));
	}

	// ---------- ações em massa ----------

	@Transactional
	public BulkResult bulk(BulkDeviceRequest request) {
		List<Device> targets = resolveTargets(request);
		List<Device> changed = new ArrayList<>();

		switch (request.action()) {
			case ASSIGN_PLAYLIST -> {
				if (request.playlistId() == null) {
					throw new IllegalArgumentException("Informe a playlist (playlistId)");
				}
				Long playlistId = requirePlaylist(request.playlistId());
				targets.forEach(device -> device.setCurrentPlaylistId(playlistId));
				changed.addAll(targets);
			}
			case REMOVE_PLAYLIST -> {
				targets.forEach(device -> device.setCurrentPlaylistId(null));
				changed.addAll(targets);
			}
			case ENABLE -> targets.stream()
					.filter(device -> DeviceStatus.DISABLED.name().equals(device.getStatus()))
					.forEach(device -> {
						String oldStatus = device.getStatus();
						device.setStatus(DeviceStatus.ACTIVE.name());
						monitoring.recordStatusChange(device, oldStatus, clock.instant());
						changed.add(device);
					});
			case DISABLE -> targets.stream()
					.filter(device -> DeviceStatus.ACTIVE.name().equals(device.getStatus()))
					.forEach(device -> {
						String oldStatus = device.getStatus();
						device.setStatus(DeviceStatus.DISABLED.name());
						monitoring.recordStatusChange(device, oldStatus, clock.instant());
						changed.add(device);
					});
			case CHECK_UPDATE -> targets.stream()
					.filter(device -> DeviceStatus.ACTIVE.name().equals(device.getStatus()))
					.forEach(device -> {
						device.requestUpdateCheck();
						monitoring.recordUpdateRequested(device, clock.instant());
						changed.add(device);
					});
			case SET_LOCATION -> {
				if (request.locationId() == null) {
					throw new IllegalArgumentException("Informe o local (locationId)");
				}
				Location location = resolveLocation(request.locationId(), null);
				targets.forEach(device -> device.setLocation(location));
				changed.addAll(targets);
			}
			case CLEAR_LOCATION -> {
				targets.forEach(device -> device.setLocation(null));
				changed.addAll(targets);
			}
			case ADD_TO_GROUP -> {
				Long groupId = requireGroupParameter(request);
				targets.forEach(device -> device.getGroupIds().add(groupId));
				changed.addAll(targets);
			}
			case REMOVE_FROM_GROUP -> {
				Long groupId = requireGroupParameter(request);
				targets.forEach(device -> device.getGroupIds().remove(groupId));
				changed.addAll(targets);
			}
			case ASSIGN_LAYOUT -> {
				if (request.layoutId() == null) {
					throw new IllegalArgumentException("Informe o layout (layoutId)");
				}
				Long layoutId = requireLayout(request.layoutId());
				String layoutName = layoutName(layoutId);
				targets.stream().filter(device -> !layoutId.equals(device.getLayoutId())).forEach(device -> {
					device.setLayoutId(layoutId);
					monitoring.recordLayoutChanged(device, layoutName, clock.instant());
				});
				changed.addAll(targets);
			}
			case REMOVE_LAYOUT -> {
				targets.stream().filter(device -> device.getLayoutId() != null).forEach(device -> {
					device.setLayoutId(null);
					monitoring.recordLayoutChanged(device, null, clock.instant());
				});
				changed.addAll(targets);
			}
		}

		deviceRepository.saveAll(changed);
		return new BulkResult(changed.size());
	}

	private Long requireGroupParameter(BulkDeviceRequest request) {
		if (request.groupId() == null) {
			throw new IllegalArgumentException("Informe o grupo (groupId)");
		}
		requireGroups(List.of(request.groupId()));
		return request.groupId();
	}

	private List<Device> resolveTargets(BulkDeviceRequest request) {
		boolean byIds = request.deviceIds() != null && !request.deviceIds().isEmpty();
		boolean byGroup = request.targetGroupId() != null;
		boolean byLocation = request.targetLocationId() != null;

		if ((byIds ? 1 : 0) + (byGroup ? 1 : 0) + (byLocation ? 1 : 0) != 1) {
			throw new IllegalArgumentException(
					"Escolha exatamente um alvo: deviceIds, targetGroupId ou targetLocationId");
		}

		if (byIds) {
			Set<Long> ids = new LinkedHashSet<>(request.deviceIds());
			List<Device> devices = deviceRepository.findAllById(ids);
			if (devices.size() != ids.size()) {
				throw new NoSuchElementException("Dispositivo não encontrado entre: " + ids);
			}
			return devices;
		}
		if (byGroup) {
			requireGroups(List.of(request.targetGroupId()));
			return deviceRepository.findByGroupId(request.targetGroupId());
		}
		if (!locationRepository.existsById(request.targetLocationId())) {
			throw new NoSuchElementException("Local não encontrado: " + request.targetLocationId());
		}
		return deviceRepository.findByLocationIdOrderById(request.targetLocationId());
	}

}
