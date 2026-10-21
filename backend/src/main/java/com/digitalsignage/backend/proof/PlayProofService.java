package com.digitalsignage.backend.proof;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.monitoring.MonitoringService;
import com.digitalsignage.backend.tenant.TenantContext;

/**
 * Comprovação de exibição: recebe os registros que o Player mediu (hora e duração em milissegundos), confere a corrente de
 * hashes, guarda e responde os relatórios. SQL direto (como os relatórios), sempre com o cliente na consulta.
 */
@Service
public class PlayProofService {

	/** Relógio da TV que difere do servidor por mais que isto é corrigido (abaixo disso é só o atraso da rede). */
	static final long CLOCK_TOLERANCE_MS = 2_000;
	static final long MAX_DURATION_MS = 24L * 3_600_000;
	static final int MAX_VERIFY_ROWS = 300_000;
	static final int DAILY_KEEP_DAYS = 366;

	public record Entry(
			long seq, Long mediaId, String mediaName, String mediaType, Long playlistId, Long campaignId, String campaignName,
			String zoneName, long startedAtMs, long durationMs, Long plannedMs, boolean completed, String prevHash, String hash) {
	}

	public record IngestResult(long acknowledgedSeq, int stored, int duplicates, int rejected) {
	}

	public record Filter(Instant from, Instant to, Long deviceId, Long mediaId, Long campaignId) {
	}

	public record ProofRow(
			long id, long deviceId, String deviceName, long seq, Long mediaId, String mediaName, String mediaType, String zoneName,
			Long playlistId, Long campaignId, String campaignName, Instant startedAt, long durationMs, Long plannedMs, boolean completed,
			String hash, boolean hashValid, boolean continuous, long clockOffsetMs) {
	}

	public record Summary(long plays, long completed, long totalDurationMs, int devices) {
	}

	public record DailyRow(long deviceId, String deviceName, LocalDate day, long plays, long completed, long totalMs, long firstSeq, long lastSeq, String lastHash) {
	}

	public record DeviceCheck(
			long deviceId, String deviceName, long records, long firstSeq, long lastSeq, long missing, long brokenLinks,
			long hashMismatches, long flaggedAtIngest, String digest, String status) {
	}

	private final JdbcTemplate jdbc;
	private final MonitoringService monitoring;
	private final Clock clock;

	public PlayProofService(JdbcTemplate jdbc, MonitoringService monitoring, Clock clock) {
		this.jdbc = jdbc;
		this.monitoring = monitoring;
		this.clock = clock;
	}

	// ---------- recebimento ----------

	@Transactional
	public IngestResult ingest(long deviceId, long deviceTimeMs, List<Entry> entries, Instant now) {
		long tenantId = TenantContext.current();
		long offset = now.toEpochMilli() - deviceTimeMs;
		// Só corrige quando o relógio da TV está realmente errado; abaixo disso a diferença é o tempo da viagem pela rede.
		long applied = Math.abs(offset) <= CLOCK_TOLERANCE_MS ? 0 : Math.max(-30L * 86_400_000, Math.min(30L * 86_400_000, offset));

		List<Entry> ordered = entries.stream().sorted(java.util.Comparator.comparingLong(Entry::seq)).toList();
		Map<String, Object> last = jdbc.queryForList("SELECT seq, hash FROM play_logs WHERE device_id = ? ORDER BY seq DESC LIMIT 1", deviceId)
				.stream().findFirst().orElse(null);
		long lastSeq = last == null ? 0 : ((Number) last.get("seq")).longValue();
		String lastHash = last == null ? null : (String) last.get("hash");
		long highest = lastSeq;

		int stored = 0;
		int duplicates = 0;
		int rejected = 0;
		ZoneId zone = monitoring.reportZone();
		Map<LocalDate, long[]> daily = new LinkedHashMap<>(); // dia -> [exibições, completas, ms, primeiro seq, último seq]
		Map<LocalDate, String> dailyHash = new LinkedHashMap<>();

		for (Entry entry : ordered) {
			if (!valid(entry)) {
				rejected++;
				continue;
			}
			String expected = PlayProofHash.compute(entry.prevHash(), entry.seq(), deviceId, entry.mediaId(), entry.mediaName(), entry.mediaType(),
					entry.playlistId(), entry.campaignId(), entry.zoneName(), entry.startedAtMs(), entry.durationMs(), entry.plannedMs(), entry.completed());
			boolean hashValid = expected.equals(entry.hash());
			boolean continuous = lastHash == null
					? entry.prevHash().equals(PlayProofHash.GENESIS)
					: entry.seq() == highest + 1 && entry.prevHash().equals(lastHash);
			Instant startedAt = Instant.ofEpochMilli(entry.startedAtMs() + applied);

			int inserted = jdbc.update("""
					INSERT INTO play_logs (tenant_id, device_id, seq, media_id, media_name, media_type, playlist_id, campaign_id, campaign_name,
					    zone_name, started_at, device_started_ms, clock_offset_ms, duration_ms, planned_ms, completed, prev_hash, hash,
					    hash_valid, continuous, received_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT (device_id, seq) DO NOTHING
					""", tenantId, deviceId, entry.seq(), entry.mediaId(), trim(entry.mediaName(), 255), entry.mediaType(), entry.playlistId(),
					entry.campaignId(), trim(entry.campaignName(), 150), trim(entry.zoneName(), 100), Timestamp.from(startedAt), entry.startedAtMs(),
					applied, entry.durationMs(), entry.plannedMs(), entry.completed(), entry.prevHash(), entry.hash(), hashValid, continuous,
					Timestamp.from(now));
			if (inserted == 0) {
				duplicates++;
				continue;
			}
			stored++;
			if (entry.seq() > highest) {
				highest = entry.seq();
				lastHash = entry.hash();
			}
			LocalDate day = startedAt.atZone(zone).toLocalDate();
			long[] sum = daily.computeIfAbsent(day, d -> new long[] { 0, 0, 0, Long.MAX_VALUE, 0 });
			sum[0]++;
			sum[1] += entry.completed() ? 1 : 0;
			sum[2] += entry.durationMs();
			sum[3] = Math.min(sum[3], entry.seq());
			if (entry.seq() >= sum[4]) {
				sum[4] = entry.seq();
				dailyHash.put(day, entry.hash());
			}
		}

		daily.forEach((day, sum) -> jdbc.update("""
				INSERT INTO play_log_daily (tenant_id, device_id, day, plays, completed, total_ms, first_seq, last_seq, last_hash)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (device_id, day) DO UPDATE SET plays = play_log_daily.plays + EXCLUDED.plays,
				    completed = play_log_daily.completed + EXCLUDED.completed, total_ms = play_log_daily.total_ms + EXCLUDED.total_ms,
				    first_seq = LEAST(play_log_daily.first_seq, EXCLUDED.first_seq),
				    last_seq = GREATEST(play_log_daily.last_seq, EXCLUDED.last_seq),
				    last_hash = CASE WHEN EXCLUDED.last_seq >= play_log_daily.last_seq THEN EXCLUDED.last_hash ELSE play_log_daily.last_hash END
				""", tenantId, deviceId, Date.valueOf(day), sum[0], sum[1], sum[2], sum[3], sum[4], dailyHash.get(day)));

		return new IngestResult(highest, stored, duplicates, rejected);
	}

	private static boolean valid(Entry e) {
		return e.seq() >= 1
				&& e.startedAtMs() > 0
				&& e.durationMs() >= 0 && e.durationMs() <= MAX_DURATION_MS
				&& (e.plannedMs() == null || (e.plannedMs() >= 0 && e.plannedMs() <= MAX_DURATION_MS))
				&& e.mediaName() != null && !e.mediaName().isBlank()
				&& e.mediaType() != null && e.mediaType().length() <= 10
				&& PlayProofHash.isHash(e.prevHash()) && PlayProofHash.isHash(e.hash());
	}

	private static String trim(String text, int max) {
		return text == null ? null : text.length() <= max ? text : text.substring(0, max);
	}

	// ---------- consulta ----------

	public Summary summary(Filter filter) {
		List<Object> args = new ArrayList<>();
		String where = where(filter, args);
		return jdbc.queryForObject("SELECT COUNT(*) AS plays, COALESCE(SUM(CASE WHEN p.completed THEN 1 ELSE 0 END), 0) AS completed, "
				+ "COALESCE(SUM(p.duration_ms), 0) AS total, COUNT(DISTINCT p.device_id) AS devices FROM play_logs p WHERE " + where,
				(rs, i) -> new Summary(rs.getLong("plays"), rs.getLong("completed"), rs.getLong("total"), rs.getInt("devices")), args.toArray());
	}

	public List<ProofRow> rows(Filter filter, int limit, int offset) {
		List<Object> args = new ArrayList<>();
		String where = where(filter, args);
		args.add(limit);
		args.add(offset);
		return jdbc.query("SELECT p.*, d.name AS device_name FROM play_logs p JOIN devices d ON d.id = p.device_id WHERE " + where
				+ " ORDER BY p.started_at DESC, p.id DESC LIMIT ? OFFSET ?", (rs, i) -> new ProofRow(
						rs.getLong("id"), rs.getLong("device_id"), rs.getString("device_name"), rs.getLong("seq"), (Long) rs.getObject("media_id"),
						rs.getString("media_name"), rs.getString("media_type"), rs.getString("zone_name"), (Long) rs.getObject("playlist_id"),
						(Long) rs.getObject("campaign_id"), rs.getString("campaign_name"), rs.getTimestamp("started_at").toInstant(),
						rs.getLong("duration_ms"), (Long) rs.getObject("planned_ms"), rs.getBoolean("completed"), rs.getString("hash"),
						rs.getBoolean("hash_valid"), rs.getBoolean("continuous"), rs.getLong("clock_offset_ms")), args.toArray());
	}

	public List<DailyRow> daily(LocalDate from, LocalDate to, Long deviceId) {
		List<Object> args = new ArrayList<>(List.of(TenantContext.current(), Date.valueOf(from), Date.valueOf(to)));
		String device = "";
		if (deviceId != null) {
			device = " AND k.device_id = ?";
			args.add(deviceId);
		}
		return jdbc.query("SELECT k.*, d.name AS device_name FROM play_log_daily k JOIN devices d ON d.id = k.device_id "
				+ "WHERE k.tenant_id = ? AND k.day BETWEEN ? AND ?" + device + " ORDER BY k.day DESC, d.name",
				(rs, i) -> new DailyRow(rs.getLong("device_id"), rs.getString("device_name"), rs.getDate("day").toLocalDate(), rs.getLong("plays"),
						rs.getLong("completed"), rs.getLong("total_ms"), rs.getLong("first_seq"), rs.getLong("last_seq"), rs.getString("last_hash")),
				args.toArray());
	}

	/**
	 * Confere a corrente do período, TV por TV: recalcula cada hash a partir dos campos guardados (pega registro alterado
	 * depois), confere se cada registro aponta para o hash do anterior e se nenhum número de ordem sumiu. O "resumo" é o
	 * SHA-256 de todos os hashes do período: guardá-lo (ou imprimi-lo) permite provar, depois, que nada mudou.
	 */
	public List<DeviceCheck> verify(Filter filter) {
		List<Object> args = new ArrayList<>();
		String where = where(filter, args);
		args.add(MAX_VERIFY_ROWS);
		Map<Long, Check> checks = new LinkedHashMap<>();
		jdbc.query("SELECT p.*, d.name AS device_name FROM play_logs p JOIN devices d ON d.id = p.device_id WHERE " + where
				+ " ORDER BY p.device_id, p.seq LIMIT ?", rs -> {
					long deviceId = rs.getLong("device_id");
					Check check = checks.computeIfAbsent(deviceId, id -> new Check(deviceId, rs_name(rs)));
					check.add(rs.getLong("seq"), rs.getString("prev_hash"), rs.getString("hash"), rs.getBoolean("hash_valid"), rs.getBoolean("continuous"),
							PlayProofHash.compute(rs.getString("prev_hash"), rs.getLong("seq"), deviceId, (Long) rs.getObject("media_id"),
									rs.getString("media_name"), rs.getString("media_type"), (Long) rs.getObject("playlist_id"), (Long) rs.getObject("campaign_id"),
									rs.getString("zone_name"), rs.getLong("device_started_ms"), rs.getLong("duration_ms"), (Long) rs.getObject("planned_ms"),
									rs.getBoolean("completed")));
				}, args.toArray());
		return checks.values().stream().map(Check::result).toList();
	}

	private static String rs_name(java.sql.ResultSet rs) {
		try {
			return rs.getString("device_name");
		} catch (java.sql.SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Acumula a conferência de uma TV. */
	private static final class Check {
		private final long deviceId;
		private final String name;
		private long records;
		private long first = -1;
		private long last = -1;
		private long missing;
		private long broken;
		private long mismatches;
		private long flagged;
		private String previousHash;
		private final StringBuilder hashes = new StringBuilder();

		Check(long deviceId, String name) {
			this.deviceId = deviceId;
			this.name = name;
		}

		void add(long seq, String prevHash, String hash, boolean validAtIngest, boolean continuousAtIngest, String recomputed) {
			records++;
			if (first < 0) {
				first = seq;
			}
			if (last >= 0 && seq > last + 1) {
				missing += seq - last - 1;
			}
			if (previousHash != null && !prevHash.equals(previousHash) && seq == last + 1) {
				broken++;
			}
			if (!recomputed.equals(hash)) {
				mismatches++;
			}
			if (!validAtIngest || !continuousAtIngest) {
				flagged++;
			}
			previousHash = hash;
			last = seq;
			hashes.append(hash);
		}

		DeviceCheck result() {
			boolean ok = missing == 0 && broken == 0 && mismatches == 0 && flagged == 0;
			return new DeviceCheck(deviceId, name, records, first, last, missing, broken, mismatches, flagged,
					PlayProofHash.sha256(hashes.toString()), ok ? "OK" : "ATENCAO");
		}
	}

	private static String where(Filter filter, List<Object> args) {
		StringBuilder where = new StringBuilder("p.tenant_id = ? AND p.started_at >= ? AND p.started_at < ?");
		args.add(TenantContext.current());
		args.add(Timestamp.from(filter.from()));
		args.add(Timestamp.from(filter.to()));
		if (filter.deviceId() != null) {
			where.append(" AND p.device_id = ?");
			args.add(filter.deviceId());
		}
		if (filter.mediaId() != null) {
			where.append(" AND p.media_id = ?");
			args.add(filter.mediaId());
		}
		if (filter.campaignId() != null) {
			where.append(" AND p.campaign_id = ?");
			args.add(filter.campaignId());
		}
		return where.toString();
	}

	// ---------- prazo de guarda ----------

	/** Apaga o detalhe mais antigo que o prazo do cliente e os resumos diários com mais de um ano. Roda "como" o cliente. */
	@Transactional
	public int purge(Instant now, int retentionDays) {
		long tenantId = TenantContext.current();
		int removed = jdbc.update("DELETE FROM play_logs WHERE tenant_id = ? AND started_at < ?", tenantId,
				Timestamp.from(now.minus(java.time.Duration.ofDays(retentionDays))));
		jdbc.update("DELETE FROM play_log_daily WHERE tenant_id = ? AND day < ?", tenantId,
				Date.valueOf(now.atZone(monitoring.reportZone()).toLocalDate().minusDays(DAILY_KEEP_DAYS)));
		return removed;
	}

	public Instant now() {
		return clock.instant();
	}

}
