package com.digitalsignage.backend.device;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.digitalsignage.backend.campaign.CampaignService;

/** Monta as respostas de TV já com o que cada uma exibe agora (campanha ativa ou playlist base). */
@Component
public class DeviceViews {

	private final CampaignService campaignService;

	public DeviceViews(CampaignService campaignService) {
		this.campaignService = campaignService;
	}

	public DeviceResponse of(Device device) {
		return DeviceResponse.from(device, campaignService.effectiveFor(device));
	}

	/** Para listas: as campanhas são carregadas uma vez só. */
	public List<DeviceResponse> of(Collection<Device> devices) {
		Map<Long, CampaignService.Effective> effective = campaignService.effectiveForAll(devices);
		return devices.stream().map(device -> DeviceResponse.from(device, effective.get(device.getId()))).toList();
	}

}
