package com.digitalsignage.backend.results;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.promo.ScanOrigin;
import com.digitalsignage.backend.proof.PlayProofService;
import com.digitalsignage.backend.tenant.TenantContext;

/**
 * Os resultados para o cliente, num lugar só: quantas vezes o conteúdo rodou (resumo diário da comprovação), quantas pessoas
 * escanearam o QR Code da promoção e quantos cupons foram pegos e usados, por dia, por TV e por promoção. Só números (nenhum
 * dado de pessoa) e sempre do cliente da requisição.
 */
@Service
public class ResultsService {

	public record Day(LocalDate day, long plays, long scans, long claimed, long redeemed) {
	}

	/** Uma TV: quantas vezes rodou e o que os QR Codes dela (origem "tv-N") renderam. */
	public record Tv(Long deviceId, String name, long plays, long scans, long claimed, long redeemed) {
	}

	/** Um QR Code que não é de uma TV (panfleto, balcão) ou o QR geral, sem origem (origem vazia). */
	public record Origin(String origin, long scans, long claimed, long redeemed) {
	}

	public record PromotionRow(Long id, String name, long scans, long claimed, long redeemed) {
	}

	public record TopMedia(String name, String type, long plays) {
	}

	public record Report(
			LocalDate from, LocalDate to, String timezone, long plays, int devicesWithPlays, long scans, long claimed, long redeemed,
			List<Day> days, List<Tv> tvs, List<Origin> origins, List<PromotionRow> promotions, List<TopMedia> topMedia) {
	}

	private static final int TOP_MEDIA = 5;

	private final JdbcTemplate jdbc;
	private final PlayProofService proof;

	public ResultsService(JdbcTemplate jdbc, PlayProofService proof) {
		this.jdbc = jdbc;
		this.proof = proof;
	}

	/** Os três contadores de uma linha (escaneamentos, cupons pegos e cupons usados). */
	private static final class Counts {
		long plays;
		long scans;
		long claimed;
		long redeemed;
	}

	@Transactional(readOnly = true)
	public Report report(LocalDate from, LocalDate to, ZoneId zone, Long promotionId) {
		long tenant = TenantContext.current();
		Map<LocalDate, Counts> byDay = new TreeMap<>();
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			byDay.put(day, new Counts());
		}
		Map<Long, Counts> byDevice = new LinkedHashMap<>();
		Map<String, Counts> byOrigin = new LinkedHashMap<>();
		Map<Long, Counts> byPromotion = new LinkedHashMap<>();
		Map<Long, String> deviceNames = new HashMap<>();

		// quantas vezes o conteúdo rodou: o resumo diário da comprovação (guardado por um ano)
		long plays = 0;
		for (PlayProofService.DailyRow row : proof.daily(from, to, null)) {
			Counts day = byDay.get(row.day());
			if (day != null) {
				day.plays += row.plays();
			}
			byDevice.computeIfAbsent(row.deviceId(), id -> new Counts()).plays += row.plays();
			deviceNames.put(row.deviceId(), row.deviceName());
			plays += row.plays();
		}
		int devicesWithPlays = (int) byDevice.values().stream().filter(c -> c.plays > 0).count();

		String promotionFilter = promotionId == null ? "" : " AND promotion_id = ?";
		long scans = 0;
		List<Object> scanArgs = new ArrayList<>(List.of(tenant, Date.valueOf(from), Date.valueOf(to)));
		if (promotionId != null) {
			scanArgs.add(promotionId);
		}
		for (Map<String, Object> row : jdbc.queryForList("SELECT day, origin, promotion_id, scans FROM promotion_scans WHERE tenant_id = ? AND day BETWEEN ? AND ?"
				+ promotionFilter, scanArgs.toArray())) {
			long n = ((Number) row.get("scans")).longValue();
			scans += n;
			add(Metric.SCANS, n, byDay, byDevice, byOrigin, byPromotion, ((Date) row.get("day")).toLocalDate(), (String) row.get("origin"),
					((Number) row.get("promotion_id")).longValue());
		}

		long claimed = 0;
		long redeemed = 0;
		java.time.Instant start = from.atStartOfDay(zone).toInstant();
		java.time.Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
		for (String column : List.of("claimed_at", "redeemed_at")) {
			boolean isClaim = "claimed_at".equals(column);
			List<Object> args = new ArrayList<>(List.of(zone.getId(), tenant, Timestamp.from(start), Timestamp.from(end)));
			if (promotionId != null) {
				args.add(promotionId);
			}
			String redeemedOnly = isClaim ? "" : " AND status = 'REDEEMED'";
			for (Map<String, Object> row : jdbc.queryForList("SELECT (" + column + " AT TIME ZONE ?)::date AS day, COALESCE(origin, '') AS origin, promotion_id, count(*) AS n "
					+ "FROM promotion_coupons WHERE tenant_id = ? AND " + column + " >= ? AND " + column + " < ?" + redeemedOnly + promotionFilter
					+ " GROUP BY 1, 2, 3", args.toArray())) {
				long n = ((Number) row.get("n")).longValue();
				add(isClaim ? Metric.CLAIMED : Metric.REDEEMED, n, byDay, byDevice, byOrigin, byPromotion, ((Date) row.get("day")).toLocalDate(),
						(String) row.get("origin"), ((Number) row.get("promotion_id")).longValue());
				if (isClaim) {
					claimed += n;
				} else {
					redeemed += n;
				}
			}
		}

		return new Report(from, to, zone.getId(), plays, devicesWithPlays, scans, claimed, redeemed,
				byDay.entrySet().stream().map(e -> new Day(e.getKey(), e.getValue().plays, e.getValue().scans, e.getValue().claimed, e.getValue().redeemed)).toList(),
				tvs(byDevice, deviceNames, tenant),
				byOrigin.entrySet().stream().filter(e -> ScanOrigin.deviceOf(e.getKey()) == null)
						.map(e -> new Origin(e.getKey(), e.getValue().scans, e.getValue().claimed, e.getValue().redeemed))
						.sorted(Comparator.comparingLong((Origin o) -> o.scans() + o.claimed()).reversed()).toList(),
				promotions(byPromotion, tenant),
				topMedia(from, to));
	}

	private enum Metric {
		SCANS, CLAIMED, REDEEMED
	}

	/** Soma {@code n} no dia, na TV (se a origem for "tv-N") ou na origem, e na promoção: o mesmo número entra nos quatro lugares. */
	private static void add(Metric metric, long n, Map<LocalDate, Counts> byDay, Map<Long, Counts> byDevice, Map<String, Counts> byOrigin,
			Map<Long, Counts> byPromotion, LocalDate day, String origin, long promotionId) {
		List<Counts> targets = new ArrayList<>();
		Counts dayCounts = byDay.get(day);
		if (dayCounts != null) {
			targets.add(dayCounts);
		}
		Long device = ScanOrigin.deviceOf(origin);
		targets.add(device != null ? byDevice.computeIfAbsent(device, id -> new Counts()) : byOrigin.computeIfAbsent(origin == null ? "" : origin, o -> new Counts()));
		targets.add(byPromotion.computeIfAbsent(promotionId, id -> new Counts()));
		for (Counts counts : targets) {
			switch (metric) {
				case SCANS -> counts.scans += n;
				case CLAIMED -> counts.claimed += n;
				case REDEEMED -> counts.redeemed += n;
			}
		}
	}

	private List<Tv> tvs(Map<Long, Counts> byDevice, Map<Long, String> knownNames, long tenant) {
		Map<Long, String> names = new HashMap<>(knownNames);
		for (Map<String, Object> row : jdbc.queryForList("SELECT id, name FROM devices WHERE tenant_id = ?", tenant)) {
			names.putIfAbsent(((Number) row.get("id")).longValue(), (String) row.get("name"));
		}
		List<Tv> tvs = new ArrayList<>();
		byDevice.forEach((id, c) -> {
			if (c.plays + c.scans + c.claimed + c.redeemed > 0) {
				tvs.add(new Tv(id, names.get(id), c.plays, c.scans, c.claimed, c.redeemed));
			}
		});
		tvs.sort(Comparator.comparingLong((Tv t) -> t.plays() + t.scans() * 100).reversed());
		return tvs;
	}

	private List<PromotionRow> promotions(Map<Long, Counts> byPromotion, long tenant) {
		Map<Long, String> names = new HashMap<>();
		for (Map<String, Object> row : jdbc.queryForList("SELECT id, name FROM promotions WHERE tenant_id = ?", tenant)) {
			names.put(((Number) row.get("id")).longValue(), (String) row.get("name"));
		}
		List<PromotionRow> rows = new ArrayList<>();
		byPromotion.forEach((id, c) -> {
			if (c.scans + c.claimed + c.redeemed > 0) {
				rows.add(new PromotionRow(id, names.getOrDefault(id, "—"), c.scans, c.claimed, c.redeemed));
			}
		});
		rows.sort(Comparator.comparingLong((PromotionRow r) -> r.scans() + r.claimed()).reversed());
		return rows;
	}

	private List<TopMedia> topMedia(LocalDate from, LocalDate to) {
		Map<String, long[]> totals = new LinkedHashMap<>();
		Map<String, String> types = new HashMap<>();
		for (PlayProofService.MediaDailyRow row : proof.dailyByMedia(from, to, null)) {
			String name = row.mediaName() == null ? "—" : row.mediaName();
			totals.computeIfAbsent(name, k -> new long[1])[0] += row.plays();
			types.putIfAbsent(name, row.mediaType());
		}
		return totals.entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
				.limit(TOP_MEDIA)
				.map(e -> new TopMedia(e.getKey(), types.get(e.getKey()), e.getValue()[0]))
				.toList();
	}
}
