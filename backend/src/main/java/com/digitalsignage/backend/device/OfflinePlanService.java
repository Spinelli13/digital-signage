package com.digitalsignage.backend.device;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.campaign.Campaign;
import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.layout.Layout;
import com.digitalsignage.backend.layout.LayoutRepository;
import com.digitalsignage.backend.layout.LayoutZone;
import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistRepository;

/**
 * Monta a agenda offline de uma TV: percorre os próximos {@link #HORIZON} e anota, intervalo a intervalo, o que a regra de
 * campanhas ({@link CampaignService#effectiveFor(Device, Instant, Collection)}, a MESMA do heartbeat) manda exibir.
 *
 * <p>Os pontos de corte são calculados, não amostrados: o resultado de {@code isActive} só muda à meia-noite, no início e
 * no fim da janela de cada campanha (no fuso dela), então basta avaliar a regra em cada um desses instantes.
 */
@Service
public class OfflinePlanService {

	/** Quanto tempo à frente a agenda cobre. O Player renova antes de acabar, enquanto tem internet. */
	public static final Duration HORIZON = Duration.ofDays(7);

	/** Teto de segurança (uma campanha por hora em 7 dias dá 168 intervalos): acima disso a agenda é cortada. */
	static final int MAX_SEGMENTS = 300;

	private static final long MASK_53_BITS = (1L << 53) - 1;
	private static final long FNV_OFFSET = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;

	private final PlaylistRepository playlistRepository;
	private final LayoutRepository layoutRepository;
	private final Clock clock;

	public OfflinePlanService(PlaylistRepository playlistRepository, LayoutRepository layoutRepository, Clock clock) {
		this.playlistRepository = playlistRepository;
		this.layoutRepository = layoutRepository;
		this.clock = clock;
	}

	/**
	 * A "versão" da agenda: igual enquanto nada que a altere muda (campanha, alvo, playlist, layout) e diferente quando muda.
	 * Barata (não monta os intervalos): o heartbeat a devolve a cada contato. 0 = sem agenda (nenhuma campanha vale para a TV).
	 */
	@Transactional(readOnly = true)
	public long versionFor(Device device, Collection<Campaign> enabled) {
		List<Campaign> relevant = relevant(device, enabled, clock.instant());
		if (relevant.isEmpty()) {
			return 0;
		}
		Layout layout = layoutOf(device);
		Map<Long, Long> versions = playlistVersions(device, relevant, layout);

		long hash = FNV_OFFSET;
		for (Campaign campaign : relevant) {
			hash = mix(hash, campaign.getId());
			hash = mix(hash, campaign.getPriority());
			hash = mix(hash, campaign.getPlaylistId() == null ? 0 : campaign.getPlaylistId());
			hash = mix(hash, CampaignService.specificity(campaign, device));
			hash = mix(hash, campaign.getStartDate() == null ? -1 : campaign.getStartDate().toEpochDay());
			hash = mix(hash, campaign.getEndDate() == null ? -1 : campaign.getEndDate().toEpochDay());
			hash = mix(hash, campaign.getStartTime() == null ? -1 : campaign.getStartTime().toNanoOfDay());
			hash = mix(hash, campaign.getEndTime() == null ? -1 : campaign.getEndTime().toNanoOfDay());
			hash = mix(hash, daysMask(campaign.days()));
			hash = mix(hash, campaign.zone().getId().hashCode());
		}
		for (Long playlistId : candidatePlaylists(device, relevant)) {
			hash = mix(hash, playlistId == null ? 0 : playlistId);
			hash = mix(hash, PlayerConfigService.versionFor(playlistId, layout, versions));
		}
		long version = hash & MASK_53_BITS;
		return version == 0 ? 1 : version;
	}

	/** A agenda dos próximos dias; nula quando nenhuma campanha vale para a TV (a TV só tem a própria playlist). */
	@Transactional(readOnly = true)
	public OfflinePlan build(Device device, Collection<Campaign> enabled) {
		Instant start = clock.instant();
		Instant end = start.plus(HORIZON);
		List<Campaign> relevant = relevant(device, enabled, start);
		if (relevant.isEmpty()) {
			return null;
		}
		Layout layout = layoutOf(device);
		Map<Long, Long> versions = playlistVersions(device, relevant, layout);

		List<Instant> points = cutPoints(relevant, start, end);
		List<OfflinePlan.Segment> segments = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) {
			Instant from = points.get(i);
			Instant to = i + 1 < points.size() ? points.get(i + 1) : end;
			CampaignService.Effective effective = CampaignService.effectiveFor(device, from, relevant);
			Long campaignId = effective.campaign() == null ? null : effective.campaign().getId();
			Long playlistId = effective.playlistId();

			OfflinePlan.Segment last = segments.isEmpty() ? null : segments.get(segments.size() - 1);
			if (last != null && Objects.equals(last.campaignId(), campaignId) && Objects.equals(last.playlistId(), playlistId)) {
				segments.set(segments.size() - 1, new OfflinePlan.Segment(last.fromMs(), to.toEpochMilli(), campaignId, last.campaignName(),
						playlistId, last.configVersion()));
				continue;
			}
			segments.add(new OfflinePlan.Segment(from.toEpochMilli(), to.toEpochMilli(), campaignId,
					effective.campaign() == null ? null : effective.campaign().getName(), playlistId,
					PlayerConfigService.versionFor(playlistId, layout, versions)));
		}

		long until = end.toEpochMilli();
		if (segments.size() > MAX_SEGMENTS) {
			segments = new ArrayList<>(segments.subList(0, MAX_SEGMENTS));
			until = segments.get(segments.size() - 1).toMs();
		}

		Set<Long> used = segments.stream().map(OfflinePlan.Segment::playlistId).filter(Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<Long, Playlist> loaded = playlistRepository.findAllById(used).stream().collect(Collectors.toMap(Playlist::getId, p -> p));
		List<OfflinePlan.PlanPlaylist> playlists = used.stream()
				.filter(loaded::containsKey)
				.map(id -> new OfflinePlan.PlanPlaylist(id, PlayerConfigService.itemsOf(loaded.get(id))))
				.toList();

		return new OfflinePlan(versionFor(device, enabled), start.toEpochMilli(), until, segments, playlists);
	}

	/**
	 * As campanhas que alcançam a TV e ainda podem valer dentro do horizonte (não terminaram e não começam depois dele).
	 * Uma janela que atravessa a meia-noite ainda vale na madrugada do dia seguinte ao último dia do período.
	 */
	static List<Campaign> relevant(Device device, Collection<Campaign> enabled, Instant now) {
		Instant horizonEnd = now.plus(HORIZON);
		return enabled.stream()
				.filter(campaign -> CampaignService.reaches(campaign, device))
				.filter(campaign -> {
					ZoneId zone = campaign.zone();
					LocalDate today = now.atZone(zone).toLocalDate();
					LocalDate lastDay = horizonEnd.atZone(zone).toLocalDate();
					boolean notEnded = campaign.getEndDate() == null || !campaign.getEndDate().isBefore(today.minusDays(1));
					boolean startsInTime = campaign.getStartDate() == null || !campaign.getStartDate().isAfter(lastDay);
					return notEnded && startsInTime;
				})
				.sorted(Comparator.comparing(Campaign::getId))
				.toList();
	}

	/** O início e o fim de cada intervalo possível: meia-noite, começo e fim da janela de cada campanha, no fuso dela. */
	private static List<Instant> cutPoints(List<Campaign> campaigns, Instant start, Instant end) {
		TreeSet<Instant> cuts = new TreeSet<>();
		cuts.add(start);
		for (Campaign campaign : campaigns) {
			ZoneId zone = campaign.zone();
			LocalDate first = start.atZone(zone).toLocalDate().minusDays(1);
			LocalDate last = end.atZone(zone).toLocalDate().plusDays(1);
			for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
				addCut(cuts, day, LocalTime.MIN, zone, start, end);
				if (campaign.getStartTime() != null) {
					addCut(cuts, day, campaign.getStartTime(), zone, start, end);
				}
				if (campaign.getEndTime() != null) {
					addCut(cuts, day, campaign.getEndTime(), zone, start, end);
				}
			}
		}
		return new ArrayList<>(cuts);
	}

	private static void addCut(TreeSet<Instant> cuts, LocalDate day, LocalTime time, ZoneId zone, Instant start, Instant end) {
		Instant at = ZonedDateTime.of(day, time, zone).toInstant();
		if (at.isAfter(start) && at.isBefore(end)) {
			cuts.add(at);
		}
	}

	private Layout layoutOf(Device device) {
		return device.getLayoutId() == null ? null : layoutRepository.findById(device.getLayoutId()).orElse(null);
	}

	/** A playlist da própria TV (pode ser nula) e a de cada campanha: os candidatos a "principal" em algum intervalo. */
	private static Set<Long> candidatePlaylists(Device device, List<Campaign> campaigns) {
		Set<Long> ids = new LinkedHashSet<>();
		ids.add(device.getCurrentPlaylistId());
		campaigns.stream().map(Campaign::getPlaylistId).filter(Objects::nonNull).sorted().forEach(ids::add);
		return ids;
	}

	/** A versão de cada playlist envolvida: as candidatas e as das outras zonas do layout. */
	private Map<Long, Long> playlistVersions(Device device, List<Campaign> campaigns, Layout layout) {
		Set<Long> ids = new LinkedHashSet<>(candidatePlaylists(device, campaigns));
		if (layout != null) {
			layout.getZones().stream().map(LayoutZone::getPlaylistId).filter(Objects::nonNull).forEach(ids::add);
		}
		ids.remove(null);
		Map<Long, Long> versions = new HashMap<>();
		playlistRepository.findAllById(ids).forEach(playlist -> versions.put(playlist.getId(), playlist.getConfigVersion()));
		return versions;
	}

	private static long daysMask(Set<DayOfWeek> days) {
		long mask = 0;
		for (DayOfWeek day : days) {
			mask |= 1L << day.getValue();
		}
		return mask;
	}

	private static long mix(long hash, long value) {
		return (hash ^ value) * FNV_PRIME;
	}

}
