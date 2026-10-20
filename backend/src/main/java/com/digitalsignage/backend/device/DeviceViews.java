package com.digitalsignage.backend.device;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.layout.Layout;
import com.digitalsignage.backend.layout.LayoutRepository;
import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.media.MediaRepository;

/** Monta as respostas de TV já com o que cada uma exibe agora (campanha ativa ou playlist base) e a mídia na tela. */
@Component
public class DeviceViews {

	private final CampaignService campaignService;
	private final MediaRepository mediaRepository;
	private final LayoutRepository layoutRepository;
	private final PlayerConfigService playerConfigService;
	private final Clock clock;

	public DeviceViews(
			CampaignService campaignService,
			MediaRepository mediaRepository,
			LayoutRepository layoutRepository,
			PlayerConfigService playerConfigService,
			Clock clock) {
		this.campaignService = campaignService;
		this.mediaRepository = mediaRepository;
		this.layoutRepository = layoutRepository;
		this.playerConfigService = playerConfigService;
		this.clock = clock;
	}

	public DeviceResponse of(Device device) {
		return of(List.of(device)).get(0);
	}

	/** Para listas: as campanhas e os nomes das mídias são carregados uma vez só. */
	public List<DeviceResponse> of(Collection<Device> devices) {
		Instant now = clock.instant();
		Map<Long, CampaignService.Effective> effective = campaignService.effectiveForAll(devices);
		List<Long> mediaIds = devices.stream().map(Device::getCurrentMediaId).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> mediaNames = mediaIds.isEmpty()
				? Map.of()
				: mediaRepository.findAllById(mediaIds).stream().collect(Collectors.toMap(Media::getId, Media::getName));
		Map<Long, String> layoutNames = layoutRepository.findAllById(devices.stream().map(Device::getLayoutId).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(Layout::getId, Layout::getName));
		Map<Long, Long> desired = playerConfigService.desiredVersions(devices, effective);
		return devices.stream()
				.map(device -> DeviceResponse.from(
						device,
						effective.get(device.getId()),
						device.getCurrentMediaId() == null ? null : mediaNames.get(device.getCurrentMediaId()),
						device.getLayoutId() == null ? null : layoutNames.get(device.getLayoutId()),
						desired.get(device.getId()),
						now))
				.toList();
	}

}
