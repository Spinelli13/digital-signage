package com.digitalsignage.backend.screenalert;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.device.DeviceStatus;
import com.digitalsignage.backend.group.DeviceGroup;
import com.digitalsignage.backend.group.DeviceGroupRepository;
import com.digitalsignage.backend.screenalert.ScreenAlert.Target;
import com.digitalsignage.backend.screenalert.ScreenAlert.Tone;
import com.digitalsignage.backend.security.CurrentUser;
import com.digitalsignage.backend.security.PanelUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** "Alertar agora": manda um aviso em tela cheia para uma TV, um grupo ou todas, e encerra quando quiser. Editor e acima. */
@RestController
@RequestMapping("/api/v1/screen-alerts")
public class ScreenAlertController {

	/** Os avisos recentes e os que ainda estão no ar. */
	public record AlertResponse(
			Long id,
			String title,
			String message,
			Tone tone,
			Target targetType,
			Long targetId,
			/** O nome do grupo ou da TV; nulo em "todas as TVs" ou quando o grupo/TV foi excluído depois. */
			String targetName,
			String createdBy,
			Instant createdAt,
			Instant expiresAt,
			Instant endedAt,
			String endedBy,
			/** ACTIVE (no ar), ENDED (encerrado por alguém) ou EXPIRED (o prazo acabou). */
			String state,
			/** Quantas TVs ativas devem mostrá-lo agora; nulo se ele não está no ar. */
			Integer devices) {
	}

	public record CreateRequest(
			String title,
			String message,
			@NotNull Tone tone,
			@NotNull Target targetType,
			Long targetId,
			/** Minutos no ar (1 a 1440); ausente = 15. */
			Integer durationMinutes) {
	}

	private final ScreenAlertService service;
	private final DeviceRepository devices;
	private final DeviceGroupRepository groups;
	private final Clock clock;

	public ScreenAlertController(ScreenAlertService service, DeviceRepository devices, DeviceGroupRepository groups, Clock clock) {
		this.service = service;
		this.devices = devices;
		this.groups = groups;
		this.clock = clock;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<AlertResponse> list() {
		Instant now = clock.instant();
		Map<Long, ScreenAlert> merged = new LinkedHashMap<>();
		service.active(now).forEach(alert -> merged.put(alert.getId(), alert));
		service.recent().forEach(alert -> merged.putIfAbsent(alert.getId(), alert));
		return respond(merged.values().stream().sorted(Comparator.comparing(ScreenAlert::getCreatedAt).reversed().thenComparing(ScreenAlert::getId, Comparator.reverseOrder())).toList(), now);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public AlertResponse create(@Valid @RequestBody CreateRequest request) {
		Instant now = clock.instant();
		ScreenAlert alert = service.create(request.title(), request.message(), request.tone(), request.targetType(), request.targetId(),
				request.durationMinutes(), currentName(), now);
		return respond(List.of(alert), now).get(0);
	}

	@PostMapping("/{id}/end")
	@Transactional
	public AlertResponse end(@PathVariable Long id) {
		Instant now = clock.instant();
		return respond(List.of(service.end(id, currentName(), now)), now).get(0);
	}

	private static String currentName() {
		return CurrentUser.get().map(PanelUser::name).orElse(null);
	}

	private List<AlertResponse> respond(List<ScreenAlert> alerts, Instant now) {
		Map<Long, String> groupNames = new LinkedHashMap<>();
		for (DeviceGroup group : groups.findAll()) {
			groupNames.put(group.getId(), group.getName());
		}
		List<Device> all = devices.findAll();
		Map<Long, String> deviceNames = new LinkedHashMap<>();
		all.forEach(device -> deviceNames.put(device.getId(), device.getName()));
		List<Device> active = all.stream().filter(device -> DeviceStatus.ACTIVE.name().equals(device.getStatus())).toList();
		List<AlertResponse> out = new ArrayList<>();
		for (ScreenAlert alert : alerts) {
			boolean live = alert.isActiveAt(now);
			String targetName = switch (alert.getTargetType()) {
				case ALL -> null;
				case GROUP -> groupNames.get(alert.getTargetId());
				case DEVICE -> deviceNames.get(alert.getTargetId());
			};
			String state = alert.getEndedAt() != null ? "ENDED" : live ? "ACTIVE" : "EXPIRED";
			Integer reach = live ? (int) active.stream().filter(alert::appliesTo).count() : null;
			out.add(new AlertResponse(alert.getId(), alert.getTitle(), alert.getMessage(), alert.getTone(), alert.getTargetType(), alert.getTargetId(),
					targetName, alert.getCreatedBy(), alert.getCreatedAt(), alert.getExpiresAt(), alert.getEndedAt(), alert.getEndedBy(), state, reach));
		}
		return out;
	}
}
