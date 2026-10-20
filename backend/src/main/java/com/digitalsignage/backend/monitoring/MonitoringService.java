package com.digitalsignage.backend.monitoring;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceConnection;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.device.DeviceStatus;
import com.digitalsignage.backend.player.PlayerHeartbeatRequest;
import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistRepository;

/**
 * Histórico, alertas e contagem de exibições. Tudo que a TV conta no heartbeat e tudo que o servidor percebe sozinho
 * (TV que parou de falar) vira evento; alertas abrem e fecham conforme as condições.
 */
@Service
public class MonitoringService {

	private static final Duration ERROR_WINDOW = Duration.ofMinutes(10);
	private static final Duration MAX_EVENT_AGE = Duration.ofDays(7);
	private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
	private static final int MAX_MESSAGE = 500;
	private static final int MAX_COUNT_PER_REPORT = 10_000;
	private static final long MAX_SECONDS_PER_REPORT = 7L * 24 * 3600;

	private final DeviceEventRepository events;
	private final AlertRepository alerts;
	private final DeviceRepository devices;
	private final MonitoringSettingsRepository settingsRepository;
	private final PlaylistRepository playlists;
	private final JdbcTemplate jdbc;
	private final ZoneId reportZone;

	public MonitoringService(
			DeviceEventRepository events,
			AlertRepository alerts,
			DeviceRepository devices,
			MonitoringSettingsRepository settingsRepository,
			PlaylistRepository playlists,
			JdbcTemplate jdbc,
			@Value("${app.monitoring.report-timezone:America/Sao_Paulo}") String reportZone) {
		this.events = events;
		this.alerts = alerts;
		this.devices = devices;
		this.settingsRepository = settingsRepository;
		this.playlists = playlists;
		this.jdbc = jdbc;
		this.reportZone = ZoneId.of(reportZone);
	}

	public ZoneId reportZone() {
		return reportZone;
	}

	public MonitoringSettings settings() {
		return settingsRepository.findById(MonitoringSettings.ID)
				.orElseThrow(() -> new IllegalStateException("Configurações de monitoramento ausentes"));
	}

	// ---------- eventos disparados por ações do painel ----------

	@Transactional
	public void record(Long deviceId, EventType type, EventLevel level, String message, Instant at) {
		events.save(new DeviceEvent(deviceId, type, level, truncate(message), at));
	}

	@Transactional
	public void recordActivated(Device device, Instant now) {
		record(device.getId(), EventType.ACTIVATED, EventLevel.INFO, "TV ativada com o nome \"" + device.getName() + "\"", now);
	}

	/** Desativar/reativar: o tempo desativada não conta na disponibilidade (nem como online, nem como offline). */
	@Transactional
	public void recordStatusChange(Device device, String oldStatus, Instant now) {
		String status = device.getStatus();
		if (Objects.equals(oldStatus, status)) {
			return;
		}
		if (DeviceStatus.DISABLED.name().equals(status)) {
			record(device.getId(), EventType.DISABLED, EventLevel.INFO, "TV desativada no painel", now);
			device.setRecordedConnection("DISABLED");
		} else if (DeviceStatus.ACTIVE.name().equals(status) && DeviceStatus.DISABLED.name().equals(oldStatus)) {
			record(device.getId(), EventType.ENABLED, EventLevel.INFO, "TV reativada no painel", now);
			device.setRecordedConnection(null);
		}
	}

	/** O administrador escolheu (ou tirou) o layout da tela da TV. */
	@Transactional
	public void recordLayoutChanged(Device device, String layoutName, Instant now) {
		record(device.getId(), EventType.LAYOUT_CHANGED, EventLevel.INFO,
				layoutName == null ? "Layout da tela removido: a TV voltou para a tela cheia" : "Layout da tela: \"" + layoutName + "\"", now);
	}

	@Transactional
	public void recordUpdateRequested(Device device, Instant now) {
		record(device.getId(), EventType.UPDATE_REQUESTED, EventLevel.INFO,
				"Pedido de verificação de atualização do Player enviado à TV", now);
	}

	// ---------- heartbeat ----------

	/**
	 * Chamado a cada heartbeat (dentro da transação dele, com a TV já atualizada): grava as transições (voltou a
	 * conectar, trocou de versão, mudou o conteúdo), as mensagens relatadas pelo Player e a contagem de exibições.
	 */
	@Transactional
	public void onHeartbeat(
			Device device,
			DeviceConnection before,
			Instant previousLastSeen,
			String previousVersion,
			CampaignService.Effective effective,
			PlayerHeartbeatRequest request,
			Instant now) {
		boolean active = DeviceStatus.ACTIVE.name().equals(device.getStatus());

		if (active && before != DeviceConnection.ONLINE) {
			if (before == DeviceConnection.OFFLINE && previousLastSeen != null && !"OFFLINE".equals(device.getRecordedConnection())) {
				// o servidor ainda não tinha percebido a queda: registra-a (retroativa) para a linha do tempo ficar completa
				record(device.getId(), EventType.OFFLINE, EventLevel.WARN, "TV ficou sem contato com o servidor",
						previousLastSeen.plus(DeviceConnection.ONLINE_WINDOW));
			}
			String message = before == DeviceConnection.NEVER || previousLastSeen == null
					? "Primeiro contato da TV com o servidor"
					: "TV voltou a se conectar, depois de " + human(Duration.between(previousLastSeen, now)) + " sem contato";
			record(device.getId(), EventType.ONLINE, EventLevel.INFO, message, now);
		}
		if (active) {
			device.setRecordedConnection("ONLINE");
		}

		if (previousVersion != null && request.playerVersion() != null && !previousVersion.equals(request.playerVersion())) {
			record(device.getId(), EventType.PLAYER_UPDATED, EventLevel.INFO,
					"Player atualizado de " + previousVersion + " para " + request.playerVersion(), now);
		}

		Long campaignId = effective.fromCampaign() ? effective.campaign().getId() : null;
		if (!Objects.equals(device.getLastPlaylistId(), effective.playlistId()) || !Objects.equals(device.getLastCampaignId(), campaignId)) {
			record(device.getId(), EventType.PLAYLIST_CHANGED, EventLevel.INFO, contentMessage(effective), now);
			device.setLastContent(effective.playlistId(), campaignId);
		}

		device.setCurrentMediaId(request.currentMediaId());
		recordPlayerEvents(device, request.events(), now);
		recordPlays(device, request.plays(), now);
	}

	private String contentMessage(CampaignService.Effective effective) {
		if (effective.playlistId() == null) {
			return "A TV ficou sem playlist para exibir";
		}
		String playlist = playlists.findById(effective.playlistId()).map(Playlist::getName).orElse("#" + effective.playlistId());
		return effective.fromCampaign()
				? "Exibindo a playlist \"" + playlist + "\" da campanha \"" + effective.campaign().getName() + "\""
				: "Exibindo a playlist \"" + playlist + "\"";
	}

	private void recordPlayerEvents(Device device, List<PlayerHeartbeatRequest.PlayerEvent> reported, Instant now) {
		if (reported == null) {
			return;
		}
		for (PlayerHeartbeatRequest.PlayerEvent event : reported) {
			if (event == null || event.message() == null || event.message().isBlank()) {
				continue;
			}
			Instant at = clampTime(event.at(), now);
			String message = truncate(event.message().trim());
			// um heartbeat reenviado (a resposta se perdeu) não pode duplicar o que o servidor já gravou
			if (events.existsByDeviceIdAndTypeAndMessageAndOccurredAt(device.getId(), EventType.PLAYER_LOG.name(), message, at)) {
				continue;
			}
			events.save(new DeviceEvent(device.getId(), EventType.PLAYER_LOG, levelOf(event.level()), message, at));
		}
	}

	private static Instant clampTime(Instant reported, Instant now) {
		if (reported == null || reported.isAfter(now.plus(MAX_CLOCK_SKEW)) || reported.isBefore(now.minus(MAX_EVENT_AGE))) {
			return now;
		}
		return reported;
	}

	private static EventLevel levelOf(String text) {
		if (text == null) {
			return EventLevel.INFO;
		}
		try {
			return EventLevel.valueOf(text.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return EventLevel.INFO;
		}
	}

	private void recordPlays(Device device, List<PlayerHeartbeatRequest.PlayRecord> plays, Instant now) {
		if (plays == null || plays.isEmpty()) {
			return;
		}
		Set<Long> mediaIds = plays.stream()
				.filter(Objects::nonNull)
				.map(PlayerHeartbeatRequest.PlayRecord::mediaId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (mediaIds.isEmpty()) {
			return;
		}
		// só mídias que ainda existem (uma mídia apagada no meio do caminho não pode derrubar o heartbeat)
		String placeholders = mediaIds.stream().map(id -> "?").collect(Collectors.joining(","));
		Set<Long> existing = Set.copyOf(jdbc.queryForList("SELECT id FROM media WHERE id IN (" + placeholders + ")", Long.class, mediaIds.toArray()));
		LocalDate day = now.atZone(reportZone).toLocalDate();

		Map<Long, long[]> totals = new HashMap<>();
		for (PlayerHeartbeatRequest.PlayRecord play : plays) {
			if (play == null || play.mediaId() == null || !existing.contains(play.mediaId()) || play.count() <= 0) {
				continue;
			}
			long[] sum = totals.computeIfAbsent(play.mediaId(), id -> new long[2]);
			sum[0] += Math.min(play.count(), MAX_COUNT_PER_REPORT);
			sum[1] += Math.max(0, Math.min(play.seconds(), MAX_SECONDS_PER_REPORT));
		}
		totals.forEach((mediaId, sum) -> jdbc.update("""
				INSERT INTO media_plays (device_id, media_id, day, plays, seconds) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (device_id, media_id, day)
				DO UPDATE SET plays = media_plays.plays + EXCLUDED.plays, seconds = media_plays.seconds + EXCLUDED.seconds
				""", device.getId(), mediaId, Date.valueOf(day), (int) sum[0], sum[1]));
	}

	// ---------- avaliação periódica (TV que parou de falar, alertas) ----------

	/** Roda a cada minuto: percebe TVs que pararam de falar e abre/fecha alertas. */
	@Transactional
	public void evaluate(Instant now) {
		MonitoringSettings settings = settings();
		Duration offlineAfter = Duration.ofMinutes(settings.getOfflineAfterMinutes());
		Duration neverAfter = Duration.ofHours(settings.getNeverConnectedAfterHours());
		Map<String, Alert> open = alerts.findByResolvedAtIsNull().stream()
				.collect(Collectors.toMap(alert -> key(alert.getDeviceId(), alert.getType()), alert -> alert));

		for (Device device : devices.findAll()) {
			DeviceConnection connection = DeviceConnection.of(device, now);
			boolean active = DeviceStatus.ACTIVE.name().equals(device.getStatus());

			if (connection == DeviceConnection.OFFLINE && !"OFFLINE".equals(device.getRecordedConnection())) {
				record(device.getId(), EventType.OFFLINE, EventLevel.WARN, "TV ficou sem contato com o servidor",
						device.getLastSeenAt().plus(DeviceConnection.ONLINE_WINDOW));
				device.setRecordedConnection("OFFLINE");
			}

			boolean offlineLong = active && device.getLastSeenAt() != null
					&& Duration.between(device.getLastSeenAt(), now).compareTo(offlineAfter) >= 0;
			reconcile(open, device, AlertType.OFFLINE, offlineLong, now,
					() -> label(device) + " está sem contato com o servidor há mais de " + human(offlineAfter));

			boolean neverLong = active && device.getLastSeenAt() == null && device.getActivatedAt() != null
					&& Duration.between(device.getActivatedAt(), now).compareTo(neverAfter) >= 0;
			reconcile(open, device, AlertType.NEVER_CONNECTED, neverLong, now,
					() -> label(device) + " foi ativada, mas ainda não se conectou ao servidor (há mais de " + human(neverAfter) + ")");

			long errors = events.countByDeviceIdAndTypeAndLevelAndOccurredAtGreaterThanEqual(
					device.getId(), EventType.PLAYER_LOG.name(), EventLevel.ERROR.name(), now.minus(ERROR_WINDOW));
			boolean burst = active && errors >= settings.getErrorBurstCount();
			reconcile(open, device, AlertType.PLAYER_ERRORS, burst, now,
					() -> label(device) + " relatou " + errors + " erros nos últimos " + human(ERROR_WINDOW));
		}
	}

	private void reconcile(Map<String, Alert> open, Device device, AlertType type, boolean problem, Instant now,
			java.util.function.Supplier<String> message) {
		Alert existing = open.get(key(device.getId(), type.name()));
		if (problem && existing == null) {
			alerts.save(new Alert(device.getId(), type, message.get(), now));
		} else if (!problem && existing != null) {
			existing.resolve(now);
		}
	}

	private static String key(Long deviceId, Object type) {
		return deviceId + ":" + type;
	}

	private static String label(Device device) {
		return device.getLocation() == null ? "A TV \"" + device.getName() + "\"" : "A TV \"" + device.getName() + "\" (" + device.getLocation().getName() + ")";
	}

	/** Apaga o que passou do prazo de guarda (histórico, alertas resolvidos e contagem de exibições). */
	@Transactional
	public int purge(Instant now) {
		int retentionDays = settings().getEventsRetentionDays();
		Instant before = now.minus(Duration.ofDays(retentionDays));
		int removed = events.deleteOlderThan(before);
		jdbc.update("DELETE FROM alerts WHERE resolved_at IS NOT NULL AND resolved_at < ?", java.sql.Timestamp.from(before));
		jdbc.update("DELETE FROM media_plays WHERE day < ?", Date.valueOf(before.atZone(reportZone).toLocalDate()));
		return removed;
	}

	// ---------- consulta ----------

	@Transactional(readOnly = true)
	public List<DeviceEvent> history(Long deviceId, Long beforeId, String type, String level, int limit) {
		return events.findPage(deviceId, beforeId, blankToNull(type), blankToNull(level), PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim().toUpperCase();
	}

	// ---------- utilidades ----------

	static String truncate(String message) {
		return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE - 1) + "…";
	}

	/** "5 min", "2 h 10 min", "3 d 4 h". */
	public static String human(Duration duration) {
		long minutes = Math.max(0, duration.toMinutes());
		if (minutes < 1) {
			return "menos de 1 min";
		}
		if (minutes < 60) {
			return minutes + " min";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return minutes % 60 == 0 ? hours + " h" : hours + " h " + (minutes % 60) + " min";
		}
		long days = hours / 24;
		return hours % 24 == 0 ? days + " d" : days + " d " + (hours % 24) + " h";
	}

}
