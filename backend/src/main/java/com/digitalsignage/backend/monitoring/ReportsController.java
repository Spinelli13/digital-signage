package com.digitalsignage.backend.monitoring;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceConnection;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.device.DeviceStatus;
import com.digitalsignage.backend.group.DeviceGroup;
import com.digitalsignage.backend.group.DeviceGroupRepository;
import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.media.MediaRepository;
import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistRepository;

/**
 * Relatórios: disponibilidade (uptime), inventário das TVs, eventos e exibição de mídias. Todos aceitam
 * {@code format=csv} (separador ";", com BOM, abre direto no Excel em português). Datas no fuso do relatório
 * (America/Sao_Paulo por padrão); {@code to} é inclusivo.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

	private static final int MAX_RANGE_DAYS = 366;
	/** O CSV é para planilhas em português: vírgula decimal, não importa a configuração do servidor. */
	private static final java.util.Locale PT_BR = java.util.Locale.forLanguageTag("pt-BR");

	public record UptimeRow(
			Long deviceId, String name, String location, Double uptimePercent,
			long onlineSeconds, long offlineSeconds, long observedSeconds, int incidents) {
	}

	public record UptimeReport(
			LocalDate from, LocalDate to, String timezone,
			Double fleetUptimePercent, long fleetOnlineSeconds, long fleetObservedSeconds,
			List<UptimeRow> devices) {
	}

	public record InventoryRow(
			Long id, String name, String location, List<String> groups, String status, String connection,
			Instant lastSeenAt, String playerVersion, String playlist, String campaign, String currentMedia,
			Instant activatedAt,
			/** O equipamento que o Player informou; nº de série, instalação e comodato só vão para o Administrador. */
			String hardwareModel, String androidVersion, Integer ramMb, String serialNumber, java.time.LocalDate installedOn, String comodatoNote) {
	}

	public record EventRow(Long id, Long deviceId, String deviceName, String type, String level, String message, Instant occurredAt) {
	}

	public record PlayRow(Long id, String label, String day, long plays, long seconds, long devices) {
	}

	public record PlaysReport(LocalDate from, LocalDate to, String groupBy, long totalPlays, long totalSeconds, List<PlayRow> rows) {
	}

	private final DeviceRepository devices;
	private final DeviceEventRepository events;
	private final DeviceGroupRepository groups;
	private final PlaylistRepository playlists;
	private final MediaRepository media;
	private final CampaignService campaigns;
	private final MonitoringService monitoring;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public ReportsController(
			DeviceRepository devices,
			DeviceEventRepository events,
			DeviceGroupRepository groups,
			PlaylistRepository playlists,
			MediaRepository media,
			CampaignService campaigns,
			MonitoringService monitoring,
			JdbcTemplate jdbc,
			Clock clock) {
		this.devices = devices;
		this.events = events;
		this.groups = groups;
		this.playlists = playlists;
		this.media = media;
		this.campaigns = campaigns;
		this.monitoring = monitoring;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ---------- disponibilidade ----------

	@GetMapping("/uptime")
	@Transactional(readOnly = true)
	public Object uptime(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long locationId,
			@RequestParam(required = false) Long groupId,
			@RequestParam(required = false) String format) {
		ZoneId zone = monitoring.reportZone();
		LocalDate lastDay = to == null ? clock.instant().atZone(zone).toLocalDate() : to;
		LocalDate firstDay = from == null ? lastDay.minusDays(6) : from;
		requireRange(firstDay, lastDay);

		Instant start = firstDay.atStartOfDay(zone).toInstant();
		Instant end = lastDay.plusDays(1).atStartOfDay(zone).toInstant();
		Instant now = clock.instant();
		Instant effectiveEnd = end.isAfter(now) ? now : end;

		Map<Long, List<DeviceEvent>> byDevice = events.findConnectionEventsBefore(effectiveEnd).stream()
				.collect(Collectors.groupingBy(DeviceEvent::getDeviceId));
		List<UptimeRow> rows = new ArrayList<>();
		long fleetOnline = 0;
		long fleetObserved = 0;
		for (Device device : filtered(locationId, groupId)) {
			if (DeviceStatus.PENDING.name().equals(device.getStatus())) {
				continue;
			}
			List<DeviceEvent> deviceEvents = byDevice.getOrDefault(device.getId(), List.of());
			UptimeCalculator.Result result = UptimeCalculator.compute(deviceEvents, start, effectiveEnd);
			int incidents = (int) deviceEvents.stream()
					.filter(event -> event.getType().equals(EventType.OFFLINE.name()))
					.filter(event -> !event.getOccurredAt().isBefore(start) && event.getOccurredAt().isBefore(effectiveEnd))
					.count();
			rows.add(new UptimeRow(device.getId(), device.getName(), locationName(device), result.uptimePercent(),
					result.onlineSeconds(), result.offlineSeconds(), result.observedSeconds(), incidents));
			fleetOnline += result.onlineSeconds();
			fleetObserved += result.observedSeconds();
		}
		rows.sort(Comparator.comparing(UptimeRow::name, String.CASE_INSENSITIVE_ORDER));
		Double fleet = fleetObserved == 0 ? null : Math.round(fleetOnline * 1000.0 / fleetObserved) / 10.0;
		UptimeReport report = new UptimeReport(firstDay, lastDay, zone.getId(), fleet, fleetOnline, fleetObserved, rows);

		if (isCsv(format)) {
			CsvWriter csv = new CsvWriter().row("TV", "Local", "Disponibilidade (%)", "Tempo online", "Tempo offline", "Tempo observado", "Quedas");
			rows.forEach(row -> csv.row(row.name(), row.location(), row.uptimePercent() == null ? "" : String.format(PT_BR, "%.1f", row.uptimePercent()),
					MonitoringService.human(java.time.Duration.ofSeconds(row.onlineSeconds())),
					MonitoringService.human(java.time.Duration.ofSeconds(row.offlineSeconds())),
					MonitoringService.human(java.time.Duration.ofSeconds(row.observedSeconds())), row.incidents()));
			return csv(csv, "disponibilidade-" + firstDay + "-a-" + lastDay + ".csv");
		}
		return report;
	}

	// ---------- inventário ----------

	@GetMapping("/inventory")
	@Transactional(readOnly = true)
	public Object inventory(@RequestParam(required = false) String format) {
		List<Device> all = devices.findAll().stream().sorted(Comparator.comparing(Device::getId)).toList();
		Map<Long, String> groupNames = groups.findAll().stream().collect(Collectors.toMap(DeviceGroup::getId, DeviceGroup::getName));
		Map<Long, String> playlistNames = playlists.findAll().stream().collect(Collectors.toMap(Playlist::getId, Playlist::getName));
		Map<Long, String> mediaNames = media.findAll().stream().collect(Collectors.toMap(Media::getId, Media::getName));
		Map<Long, CampaignService.Effective> effective = campaigns.effectiveForAll(all);
		Instant now = clock.instant();
		boolean internal = com.digitalsignage.backend.security.CurrentUser.isAtLeast(com.digitalsignage.backend.user.Role.ADMIN);

		List<InventoryRow> rows = all.stream().map(device -> {
			CampaignService.Effective content = effective.get(device.getId());
			return new InventoryRow(
					device.getId(), device.getName(), locationName(device),
					device.getGroupIds().stream().map(groupNames::get).filter(java.util.Objects::nonNull).sorted().toList(),
					device.getStatus(), DeviceConnection.of(device, now).name(), device.getLastSeenAt(), device.getPlayerVersion(),
					content.playlistId() == null ? null : playlistNames.get(content.playlistId()),
					content.fromCampaign() ? content.campaign().getName() : null,
					device.getCurrentMediaId() == null ? null : mediaNames.get(device.getCurrentMediaId()),
					device.getActivatedAt(),
					device.getHardwareModel(), device.getAndroidVersion(), device.getRamMb(),
					internal ? device.getSerialNumber() : null, internal ? device.getInstalledOn() : null, internal ? device.getComodatoNote() : null);
		}).toList();

		if (isCsv(format)) {
			CsvWriter csv = new CsvWriter().row("ID", "TV", "Local", "Grupos", "Status", "Conexão", "Último contato", "Versão do Player",
					"Playlist exibida", "Campanha", "Mídia na tela", "Ativada em", "Modelo", "Android", "Memória (MB)", "Nº de série", "Instalada em",
					"Comodato / observação");
			rows.forEach(row -> csv.row(row.id(), row.name(), row.location(), String.join(", ", row.groups()), row.status(), row.connection(),
					row.lastSeenAt(), row.playerVersion(), row.playlist(), row.campaign(), row.currentMedia(), row.activatedAt(), row.hardwareModel(), row.androidVersion(), row.ramMb(),
					row.serialNumber(), row.installedOn(), row.comodatoNote()));
			return csv(csv, "inventario-" + now.atZone(monitoring.reportZone()).toLocalDate() + ".csv");
		}
		return rows;
	}

	// ---------- eventos ----------

	@GetMapping("/events")
	@Transactional(readOnly = true)
	public Object eventsReport(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long deviceId,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String level,
			@RequestParam(required = false, defaultValue = "500") int limit,
			@RequestParam(required = false) String format) {
		ZoneId zone = monitoring.reportZone();
		LocalDate lastDay = to == null ? clock.instant().atZone(zone).toLocalDate() : to;
		LocalDate firstDay = from == null ? lastDay.minusDays(6) : from;
		requireRange(firstDay, lastDay);

		int max = isCsv(format) ? 20_000 : Math.max(1, Math.min(limit, 5000));
		List<DeviceEvent> found = events.findBetween(
				firstDay.atStartOfDay(zone).toInstant(), lastDay.plusDays(1).atStartOfDay(zone).toInstant(), deviceId,
				blankToNull(type), blankToNull(level), PageRequest.of(0, max));
		Map<Long, String> names = devices.findAllById(found.stream().map(DeviceEvent::getDeviceId).distinct().toList()).stream()
				.collect(Collectors.toMap(Device::getId, Device::getName));
		List<EventRow> rows = found.stream()
				.map(event -> new EventRow(event.getId(), event.getDeviceId(), names.get(event.getDeviceId()), event.getType(),
						event.getLevel(), event.getMessage(), event.getOccurredAt()))
				.toList();

		if (isCsv(format)) {
			CsvWriter csv = new CsvWriter().row("Quando", "TV", "Tipo", "Nível", "Mensagem");
			rows.forEach(row -> csv.row(row.occurredAt(), row.deviceName(), row.type(), row.level(), row.message()));
			return csv(csv, "eventos-" + firstDay + "-a-" + lastDay + ".csv");
		}
		return rows;
	}

	// ---------- exibição de mídias ----------

	/** {@code groupBy}: media (padrão), device ou day. */
	@GetMapping("/plays")
	@Transactional(readOnly = true)
	public Object plays(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long deviceId,
			@RequestParam(required = false) Long locationId,
			@RequestParam(required = false, defaultValue = "media") String groupBy,
			@RequestParam(required = false) String format) {
		ZoneId zone = monitoring.reportZone();
		LocalDate lastDay = to == null ? clock.instant().atZone(zone).toLocalDate() : to;
		LocalDate firstDay = from == null ? lastDay.minusDays(6) : from;
		requireRange(firstDay, lastDay);

		String select;
		String group;
		switch (groupBy.toLowerCase()) {
			case "media" -> {
				select = "m.id AS id, m.name AS label, NULL AS day";
				group = "m.id, m.name";
			}
			case "device" -> {
				select = "d.id AS id, d.name AS label, NULL AS day";
				group = "d.id, d.name";
			}
			case "day" -> {
				select = "NULL AS id, NULL AS label, to_char(p.day, 'YYYY-MM-DD') AS day";
				group = "p.day";
			}
			default -> throw new IllegalArgumentException("Agrupamento inválido: " + groupBy + " (use media, device ou day)");
		}
		StringBuilder sql = new StringBuilder("SELECT " + select + ", SUM(p.plays) AS plays, SUM(p.seconds) AS seconds, COUNT(DISTINCT p.device_id) AS devices "
				+ "FROM media_plays p JOIN media m ON m.id = p.media_id JOIN devices d ON d.id = p.device_id WHERE p.day BETWEEN ? AND ? AND d.tenant_id = ?");
		// SQL direto não passa pelo isolamento do Hibernate: o cliente entra na consulta à mão
		List<Object> args = new ArrayList<>(List.of(Date.valueOf(firstDay), Date.valueOf(lastDay), com.digitalsignage.backend.tenant.TenantContext.current()));
		if (deviceId != null) {
			sql.append(" AND p.device_id = ?");
			args.add(deviceId);
		}
		if (locationId != null) {
			sql.append(" AND d.location_id = ?");
			args.add(locationId);
		}
		sql.append(" GROUP BY ").append(group).append(groupBy.equalsIgnoreCase("day") ? " ORDER BY p.day" : " ORDER BY SUM(p.plays) DESC, 2");

		List<PlayRow> rows = jdbc.query(sql.toString(), (rs, i) -> new PlayRow(
				rs.getObject("id") == null ? null : rs.getLong("id"), rs.getString("label"), rs.getString("day"),
				rs.getLong("plays"), rs.getLong("seconds"), rs.getLong("devices")), args.toArray());
		long totalPlays = rows.stream().mapToLong(PlayRow::plays).sum();
		long totalSeconds = rows.stream().mapToLong(PlayRow::seconds).sum();

		if (isCsv(format)) {
			String first = switch (groupBy.toLowerCase()) {
				case "device" -> "TV";
				case "day" -> "Dia";
				default -> "Mídia";
			};
			CsvWriter csv = new CsvWriter().row(first, "Exibições", "Tempo total", "TVs");
			rows.forEach(row -> csv.row(row.day() != null ? row.day() : row.label(), row.plays(),
					MonitoringService.human(java.time.Duration.ofSeconds(row.seconds())), row.devices()));
			return csv(csv, "exibicoes-" + groupBy.toLowerCase() + "-" + firstDay + "-a-" + lastDay + ".csv");
		}
		return new PlaysReport(firstDay, lastDay, groupBy.toLowerCase(), totalPlays, totalSeconds, rows);
	}

	// ---------- utilidades ----------

	private List<Device> filtered(Long locationId, Long groupId) {
		return devices.findAll().stream()
				.filter(device -> locationId == null || (device.getLocation() != null && locationId.equals(device.getLocation().getId())))
				.filter(device -> groupId == null || device.getGroupIds().contains(groupId))
				.toList();
	}

	private static String locationName(Device device) {
		return device.getLocation() == null ? null : device.getLocation().getName();
	}

	private static void requireRange(LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("A data inicial não pode ser depois da data final");
		}
		if (java.time.temporal.ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw new IllegalArgumentException("O período pode ter no máximo " + MAX_RANGE_DAYS + " dias");
		}
	}

	private static boolean isCsv(String format) {
		return "csv".equalsIgnoreCase(format);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim().toUpperCase();
	}

	private static ResponseEntity<byte[]> csv(CsvWriter writer, String filename) {
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
				.body(writer.bytes());
	}

}
