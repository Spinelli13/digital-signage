package com.digitalsignage.backend.campaign;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.group.DeviceGroup;
import com.digitalsignage.backend.group.DeviceGroupRepository;
import com.digitalsignage.backend.location.Location;
import com.digitalsignage.backend.location.LocationRepository;
import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

	private static final int MAX_PRIORITY = 1000;
	private static final int DEFAULT_PRIORITY = 50;

	private final CampaignRepository campaignRepository;
	private final CampaignService campaignService;
	private final PlaylistRepository playlistRepository;
	private final DeviceRepository deviceRepository;
	private final LocationRepository locationRepository;
	private final DeviceGroupRepository groupRepository;

	public CampaignController(
			CampaignRepository campaignRepository,
			CampaignService campaignService,
			PlaylistRepository playlistRepository,
			DeviceRepository deviceRepository,
			LocationRepository locationRepository,
			DeviceGroupRepository groupRepository) {
		this.campaignRepository = campaignRepository;
		this.campaignService = campaignService;
		this.playlistRepository = playlistRepository;
		this.deviceRepository = deviceRepository;
		this.locationRepository = locationRepository;
		this.groupRepository = groupRepository;
	}

	/** Ativas primeiro, depois as que aguardam horário, as agendadas, as pausadas e as encerradas. */
	@GetMapping
	@Transactional(readOnly = true)
	public List<CampaignResponse> list() {
		Instant now = campaignService.now();
		List<Device> devices = deviceRepository.findAll();
		Lookups lookups = lookups();
		return campaignRepository.findAllWithTargets().stream()
				.map(campaign -> responseFor(campaign, devices, lookups, now))
				.sorted(Comparator
						.comparingInt((CampaignResponse response) -> order(response.state()))
						.thenComparing(CampaignResponse::priority, Comparator.reverseOrder())
						.thenComparing(CampaignResponse::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public CampaignResponse create(@Valid @RequestBody CampaignRequest request) {
		Campaign campaign = new Campaign(request.name().trim());
		apply(campaign, request);
		campaignRepository.save(campaign);
		return one(campaign.getId());
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public CampaignResponse get(@PathVariable Long id) {
		return one(id);
	}

	@PutMapping("/{id}")
	@Transactional
	public CampaignResponse update(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
		Campaign campaign = findOrThrow(id);
		apply(campaign, request);
		campaignRepository.save(campaign);
		return one(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		campaignRepository.delete(findOrThrow(id));
	}

	/**
	 * "O que esta TV exibiria em tal momento?" (padrão: agora). Serve para o painel simular a agenda antes de
	 * publicar e para conferir por que uma TV mostra o que mostra.
	 */
	@GetMapping("/preview")
	@Transactional(readOnly = true)
	public PreviewResponse preview(@RequestParam Long deviceId, @RequestParam(required = false) Instant at) {
		Device device = deviceRepository.findById(deviceId)
				.orElseThrow(() -> new NoSuchElementException("Dispositivo não encontrado: " + deviceId));
		Instant moment = at == null ? campaignService.now() : at;
		CampaignService.Effective effective = campaignService.effectiveFor(device, moment);
		String playlistName = effective.playlistId() == null
				? null
				: playlistRepository.findById(effective.playlistId()).map(Playlist::getName).orElse(null);
		return new PreviewResponse(
				deviceId,
				moment,
				effective.playlistId(),
				playlistName,
				effective.fromCampaign() ? "CAMPAIGN" : effective.playlistId() == null ? "NONE" : "BASE",
				effective.fromCampaign() ? effective.campaign().getId() : null,
				effective.fromCampaign() ? effective.campaign().getName() : null);
	}

	public record PreviewResponse(
			Long deviceId,
			Instant at,
			Long playlistId,
			String playlistName,
			/** CAMPAIGN (uma campanha vence), BASE (a playlist da própria TV) ou NONE (a TV não tem nada). */
			String source,
			Long campaignId,
			String campaignName) {
	}

	// ---------- validação e montagem ----------

	private void apply(Campaign campaign, CampaignRequest request) {
		if (request.startDate() != null && request.endDate() != null && request.startDate().isAfter(request.endDate())) {
			throw new IllegalArgumentException("A data de início não pode ser depois da data de fim");
		}
		if (request.startTime() != null && request.startTime().equals(request.endTime())) {
			throw new IllegalArgumentException(
					"O horário de início e o de fim não podem ser iguais (para o dia todo, deixe os dois vazios)");
		}
		int priority = request.priority() == null ? DEFAULT_PRIORITY : request.priority();
		if (priority < 0 || priority > MAX_PRIORITY) {
			throw new IllegalArgumentException("A prioridade precisa estar entre 0 e " + MAX_PRIORITY);
		}
		String timezone = request.timezone() == null || request.timezone().isBlank()
				? Campaign.DEFAULT_TIMEZONE
				: request.timezone().trim();
		try {
			ZoneId.of(timezone);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Fuso horário inválido: " + timezone);
		}
		Playlist playlist = playlistRepository.findById(request.playlistId())
				.orElseThrow(() -> new NoSuchElementException("Playlist não encontrada: " + request.playlistId()));

		campaign.setName(request.name().trim());
		campaign.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
		campaign.setPlaylistId(playlist.getId());
		campaign.setPriority(priority);
		campaign.setEnabled(request.enabled() == null || request.enabled());
		campaign.setStartDate(request.startDate());
		campaign.setEndDate(request.endDate());
		campaign.setStartTime(request.startTime());
		campaign.setEndTime(request.endTime());
		campaign.setDays(parseDays(request.daysOfWeek()));
		campaign.setTimezone(timezone);
		campaign.replaceTargets(buildTargets(request.targets()));
	}

	private static Set<DayOfWeek> parseDays(List<Integer> values) {
		if (values == null || values.isEmpty()) {
			return EnumSet.allOf(DayOfWeek.class);
		}
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		for (Integer value : values) {
			if (value == null || value < 1 || value > 7) {
				throw new IllegalArgumentException("Dia da semana inválido: " + value + " (use 1 = segunda ... 7 = domingo)");
			}
			days.add(DayOfWeek.of(value));
		}
		return days;
	}

	private List<CampaignTarget> buildTargets(List<CampaignRequest.TargetRequest> requested) {
		if (requested == null || requested.isEmpty()) {
			throw new IllegalArgumentException("Escolha para quem é a campanha (todas as TVs, TVs, locais ou grupos)");
		}
		Set<String> seen = new LinkedHashSet<>();
		List<CampaignTarget> targets = new ArrayList<>();
		for (CampaignRequest.TargetRequest target : requested) {
			if (target.type() == TargetType.ALL) {
				if (target.id() != null) {
					throw new IllegalArgumentException("\"Todas as TVs\" não leva um id");
				}
			} else if (target.id() == null) {
				throw new IllegalArgumentException("Informe o id do alvo do tipo " + target.type());
			}
			if (!seen.add(target.type() + ":" + target.id())) {
				continue; // alvo repetido
			}
			targets.add(switch (target.type()) {
				case ALL -> CampaignTarget.all();
				case DEVICE -> {
					requireExists(deviceRepository.existsById(target.id()), "Dispositivo", target.id());
					yield CampaignTarget.device(target.id());
				}
				case LOCATION -> {
					requireExists(locationRepository.existsById(target.id()), "Local", target.id());
					yield CampaignTarget.location(target.id());
				}
				case GROUP -> {
					requireExists(groupRepository.existsById(target.id()), "Grupo", target.id());
					yield CampaignTarget.group(target.id());
				}
			});
		}
		return targets;
	}

	private static void requireExists(boolean exists, String kind, Long id) {
		if (!exists) {
			throw new NoSuchElementException(kind + " não encontrado: " + id);
		}
	}

	// ---------- resposta ----------

	private record Lookups(
			Map<Long, String> playlists, Map<Long, String> devices, Map<Long, String> locations, Map<Long, String> groups) {
	}

	private Lookups lookups() {
		return new Lookups(
				playlistRepository.findAll().stream().collect(Collectors.toMap(Playlist::getId, Playlist::getName)),
				deviceRepository.findAll().stream().collect(Collectors.toMap(Device::getId, Device::getName)),
				locationRepository.findAll().stream().collect(Collectors.toMap(Location::getId, Location::getName)),
				groupRepository.findAll().stream().collect(Collectors.toMap(DeviceGroup::getId, DeviceGroup::getName)));
	}

	private CampaignResponse one(Long id) {
		Campaign campaign = campaignRepository.findAllWithTargets().stream()
				.filter(candidate -> candidate.getId().equals(id))
				.findFirst()
				.orElseThrow(() -> new NoSuchElementException("Campanha não encontrada: " + id));
		return responseFor(campaign, deviceRepository.findAll(), lookups(), campaignService.now());
	}

	private CampaignResponse responseFor(Campaign campaign, List<Device> devices, Lookups lookups, Instant now) {
		List<CampaignResponse.TargetInfo> targets = campaign.getTargets().stream()
				.sorted(Comparator.comparing((CampaignTarget target) -> target.type().ordinal()).thenComparing(CampaignTarget::getId))
				.map(target -> new CampaignResponse.TargetInfo(target.type(), target.targetId(), nameOf(target, lookups)))
				.toList();
		long deviceCount = devices.stream().filter(device -> CampaignService.reaches(campaign, device)).count();
		return new CampaignResponse(
				campaign.getId(),
				campaign.getName(),
				campaign.getDescription(),
				campaign.getPlaylistId(),
				lookups.playlists().get(campaign.getPlaylistId()),
				campaign.getPriority(),
				campaign.isEnabled(),
				campaign.getStartDate(),
				campaign.getEndDate(),
				campaign.getStartTime(),
				campaign.getEndTime(),
				campaign.days().stream().sorted().map(DayOfWeek::getValue).toList(),
				campaign.getTimezone(),
				targets,
				CampaignSchedule.stateAt(campaign, now),
				deviceCount,
				campaign.getCreatedAt(),
				campaign.getUpdatedAt());
	}

	private static String nameOf(CampaignTarget target, Lookups lookups) {
		Function<Map<Long, String>, String> byId = names -> names.get(target.targetId());
		return switch (target.type()) {
			case ALL -> null;
			case DEVICE -> byId.apply(lookups.devices());
			case LOCATION -> byId.apply(lookups.locations());
			case GROUP -> byId.apply(lookups.groups());
		};
	}

	private static int order(CampaignState state) {
		return switch (state) {
			case ACTIVE -> 0;
			case WAITING -> 1;
			case SCHEDULED -> 2;
			case PAUSED -> 3;
			case ENDED -> 4;
		};
	}

	private Campaign findOrThrow(Long id) {
		return campaignRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Campanha não encontrada: " + id));
	}

}
