package com.digitalsignage.backend.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Um alvo de campanha: todas as TVs, uma TV, um local ou um grupo (exatamente um dos quatro). */
@Entity
@Table(name = "campaign_targets")
public class CampaignTarget {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id")
	private Campaign campaign;

	@Column(name = "all_devices", nullable = false)
	private boolean allDevices;

	@Column(name = "device_id")
	private Long deviceId;

	@Column(name = "location_id")
	private Long locationId;

	@Column(name = "group_id")
	private Long groupId;

	protected CampaignTarget() {
	}

	public static CampaignTarget all() {
		CampaignTarget target = new CampaignTarget();
		target.allDevices = true;
		return target;
	}

	public static CampaignTarget device(long id) {
		CampaignTarget target = new CampaignTarget();
		target.deviceId = id;
		return target;
	}

	public static CampaignTarget location(long id) {
		CampaignTarget target = new CampaignTarget();
		target.locationId = id;
		return target;
	}

	public static CampaignTarget group(long id) {
		CampaignTarget target = new CampaignTarget();
		target.groupId = id;
		return target;
	}

	void setCampaign(Campaign campaign) {
		this.campaign = campaign;
	}

	public TargetType type() {
		if (allDevices) {
			return TargetType.ALL;
		}
		if (deviceId != null) {
			return TargetType.DEVICE;
		}
		if (locationId != null) {
			return TargetType.LOCATION;
		}
		return TargetType.GROUP;
	}

	/** Id da TV, do local ou do grupo (nulo para "todas as TVs"). */
	public Long targetId() {
		return switch (type()) {
			case ALL -> null;
			case DEVICE -> deviceId;
			case LOCATION -> locationId;
			case GROUP -> groupId;
		};
	}

	public Long getId() {
		return id;
	}

}
