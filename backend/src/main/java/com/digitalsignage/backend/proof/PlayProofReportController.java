package com.digitalsignage.backend.proof;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.monitoring.CsvWriter;
import com.digitalsignage.backend.monitoring.MonitoringService;

/** Relatório da comprovação de exibição: linha a linha, conferência da corrente e resumo diário (JSON e CSV). */
@RestController
@RequestMapping("/api/v1/reports/proof")
public class PlayProofReportController {

	private static final int MAX_RANGE_DAYS = 93;
	private static final int MAX_PAGE = 500;
	private static final int MAX_CSV_ROWS = 100_000;
	private static final DateTimeFormatter WITH_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

	public record Report(
			LocalDate from, LocalDate to, String timezone, long total, long completed, long totalDurationMs, int devices,
			int offset, int limit, List<PlayProofService.ProofRow> rows) {
	}

	public record Verification(LocalDate from, LocalDate to, String timezone, boolean ok, String digest, List<PlayProofService.DeviceCheck> devices) {
	}

	private final PlayProofService service;
	private final MonitoringService monitoring;
	private final Clock clock;

	public PlayProofReportController(PlayProofService service, MonitoringService monitoring, Clock clock) {
		this.service = service;
		this.monitoring = monitoring;
		this.clock = clock;
	}

	@GetMapping
	public Object report(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long deviceId,
			@RequestParam(required = false) Long mediaId,
			@RequestParam(required = false) Long campaignId,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false) String format) {
		ZoneId zone = monitoring.reportZone();
		LocalDate last = to == null ? clock.instant().atZone(zone).toLocalDate() : to;
		LocalDate first = from == null ? last : from;
		requireRange(first, last);
		PlayProofService.Filter filter = filter(first, last, deviceId, mediaId, campaignId, zone);

		if ("csv".equalsIgnoreCase(format)) {
			CsvWriter csv = new CsvWriter().row("Início (com milissegundos)", "TV", "Mídia", "Tipo", "Zona", "Campanha", "Playlist", "Duração (ms)",
					"Prevista (ms)", "Resultado", "Nº", "Hash", "Corrente conferida", "Correção do relógio da TV (ms)");
			service.rows(filter, MAX_CSV_ROWS, 0).forEach(row -> csv.row(
					WITH_MILLIS.format(row.startedAt().atZone(zone)), row.deviceName(), row.mediaName(), row.mediaType(), row.zoneName(),
					row.campaignName(), row.playlistId(), row.durationMs(), row.plannedMs(), row.completed() ? "Completa" : "Interrompida",
					row.seq(), row.hash(), row.hashValid() && row.continuous() ? "sim" : "NÃO", row.clockOffsetMs()));
			return csv(csv, "comprovacao-" + first + "-a-" + last + ".csv");
		}

		PlayProofService.Summary summary = service.summary(filter);
		int size = Math.max(1, Math.min(limit, MAX_PAGE));
		int skip = Math.max(0, offset);
		return new Report(first, last, zone.getId(), summary.plays(), summary.completed(), summary.totalDurationMs(), summary.devices(), skip, size,
				service.rows(filter, size, skip));
	}

	@GetMapping("/verify")
	public Verification verify(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long deviceId) {
		ZoneId zone = monitoring.reportZone();
		LocalDate last = to == null ? clock.instant().atZone(zone).toLocalDate() : to;
		LocalDate first = from == null ? last : from;
		requireRange(first, last);
		List<PlayProofService.DeviceCheck> checks = service.verify(filter(first, last, deviceId, null, null, zone));
		boolean ok = checks.stream().allMatch(check -> "OK".equals(check.status()));
		String digest = PlayProofHash.sha256(String.join("", checks.stream().map(PlayProofService.DeviceCheck::digest).toList()));
		return new Verification(first, last, zone.getId(), ok, checks.isEmpty() ? null : digest, checks);
	}

	@GetMapping("/daily")
	public List<PlayProofService.DailyRow> daily(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long deviceId) {
		LocalDate last = to == null ? clock.instant().atZone(monitoring.reportZone()).toLocalDate() : to;
		LocalDate first = from == null ? last.minusDays(29) : from;
		if (first.isAfter(last)) {
			throw new IllegalArgumentException("A data inicial não pode ser depois da data final");
		}
		return service.daily(first, last, deviceId);
	}

	/** Quantas vezes cada mídia (e campanha) rodou em cada dia, por TV: dura um ano, mesmo depois do detalhe ser apagado. */
	@GetMapping("/daily-media")
	public Object dailyByMedia(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long deviceId,
			@RequestParam(required = false) String format) {
		LocalDate last = to == null ? clock.instant().atZone(monitoring.reportZone()).toLocalDate() : to;
		LocalDate first = from == null ? last.minusDays(29) : from;
		if (first.isAfter(last)) {
			throw new IllegalArgumentException("A data inicial não pode ser depois da data final");
		}
		List<PlayProofService.MediaDailyRow> rows = service.dailyByMedia(first, last, deviceId);
		if ("csv".equalsIgnoreCase(format)) {
			CsvWriter csv = new CsvWriter().row("Dia", "TV", "Mídia", "Tipo", "Campanha", "Exibições", "Completas", "Tempo no ar (s)");
			rows.forEach(row -> csv.row(row.day().toString(), row.deviceName(), row.mediaName(), row.mediaType(), row.campaignName(), row.plays(),
					row.completed(), row.totalMs() / 1000));
			return csv(csv, "exibicoes-por-midia-" + first + "-a-" + last + ".csv");
		}
		return rows;
	}

	private static PlayProofService.Filter filter(LocalDate first, LocalDate last, Long deviceId, Long mediaId, Long campaignId, ZoneId zone) {
		Instant start = first.atStartOfDay(zone).toInstant();
		Instant end = last.plusDays(1).atStartOfDay(zone).toInstant();
		return new PlayProofService.Filter(start, end, deviceId, mediaId, campaignId);
	}

	private static void requireRange(LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("A data inicial não pode ser depois da data final");
		}
		if (java.time.temporal.ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw new IllegalArgumentException("A comprovação aceita no máximo " + MAX_RANGE_DAYS + " dias por consulta (são muitos registros por dia)");
		}
	}

	private static ResponseEntity<byte[]> csv(CsvWriter writer, String filename) {
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
				.body(writer.bytes());
	}

}
