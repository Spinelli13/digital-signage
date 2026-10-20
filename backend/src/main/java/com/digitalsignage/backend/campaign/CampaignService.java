package com.digitalsignage.backend.campaign;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.device.Device;

/**
 * Decide o que cada TV deve exibir agora: a campanha ativa que a alcança e vence no desempate, ou, sem
 * nenhuma, a playlist base da própria TV.
 *
 * <p>Desempate entre campanhas ativas que alcançam a mesma TV: maior prioridade; depois o alvo mais específico
 * (TV &gt; grupo &gt; local &gt; todas); depois a mais nova. Assim o resultado nunca é ambíguo.
 */
@Service
public class CampaignService {

	/** O que a TV exibe: a playlist e, se veio de uma campanha, qual (nulo = playlist base da TV). */
	public record Effective(Long playlistId, Campaign campaign) {

		public boolean fromCampaign() {
			return campaign != null;
		}
	}

	private final CampaignRepository campaignRepository;
	private final Clock clock;

	public CampaignService(CampaignRepository campaignRepository, Clock clock) {
		this.campaignRepository = campaignRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Effective effectiveFor(Device device) {
		return effectiveFor(device, clock.instant(), campaignRepository.findEnabledWithTargets());
	}

	@Transactional(readOnly = true)
	public Effective effectiveFor(Device device, Instant at) {
		return effectiveFor(device, at, campaignRepository.findEnabledWithTargets());
	}

	/** Para listas: carrega as campanhas uma única vez. */
	@Transactional(readOnly = true)
	public Map<Long, Effective> effectiveForAll(Collection<Device> devices) {
		Instant now = clock.instant();
		List<Campaign> campaigns = campaignRepository.findEnabledWithTargets();
		Map<Long, Effective> result = new HashMap<>();
		devices.forEach(device -> result.put(device.getId(), effectiveFor(device, now, campaigns)));
		return result;
	}

	public Instant now() {
		return clock.instant();
	}

	// ---------- regra pura (sem banco) ----------

	public static Effective effectiveFor(Device device, Instant at, Collection<Campaign> enabledCampaigns) {
		Optional<Campaign> winner = enabledCampaigns.stream()
				.filter(campaign -> CampaignSchedule.isActive(campaign, at))
				.filter(campaign -> specificity(campaign, device) >= 0)
				.max(Comparator
						.comparingInt(Campaign::getPriority)
						.thenComparingInt(campaign -> specificity(campaign, device))
						.thenComparing(Campaign::getId));
		return winner
				.map(campaign -> new Effective(campaign.getPlaylistId(), campaign))
				.orElseGet(() -> new Effective(device.getCurrentPlaylistId(), null));
	}

	/** O alvo mais específico da campanha que alcança a TV; -1 se nenhum alvo alcança. */
	public static int specificity(Campaign campaign, Device device) {
		return campaign.getTargets().stream()
				.filter(target -> reaches(target, device))
				.mapToInt(target -> target.type().specificity())
				.max()
				.orElse(-1);
	}

	public static boolean reaches(Campaign campaign, Device device) {
		return specificity(campaign, device) >= 0;
	}

	private static boolean reaches(CampaignTarget target, Device device) {
		return switch (target.type()) {
			case ALL -> true;
			case DEVICE -> target.targetId().equals(device.getId());
			case LOCATION -> device.getLocation() != null && target.targetId().equals(device.getLocation().getId());
			case GROUP -> device.getGroupIds().contains(target.targetId());
		};
	}

}
