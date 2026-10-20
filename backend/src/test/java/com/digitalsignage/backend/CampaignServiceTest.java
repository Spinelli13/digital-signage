package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.digitalsignage.backend.campaign.Campaign;
import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.campaign.CampaignService.Effective;
import com.digitalsignage.backend.campaign.CampaignTarget;
import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.location.Location;

/**
 * Qual playlist cada TV exibe agora: campanha ativa que a alcança (maior prioridade, depois alvo mais específico,
 * depois a mais nova) ou, sem campanha, a playlist base da TV. Regra pura, sem banco.
 */
class CampaignServiceTest {

	private static final Instant NOON = Instant.parse("2026-11-05T15:00:00Z"); // 12:00 em São Paulo

	private static Device tv(long id, Long basePlaylist, Long locationId, Long... groups) {
		Location location = null;
		if (locationId != null) {
			location = new Location("Local " + locationId, null);
			ReflectionTestUtils.setField(location, "id", locationId);
		}
		Device device = new Device("TV " + id, location);
		ReflectionTestUtils.setField(device, "id", id);
		device.setCurrentPlaylistId(basePlaylist);
		device.getGroupIds().addAll(List.of(groups));
		return device;
	}

	private static Campaign campaign(long id, long playlist, int priority, CampaignTarget... targets) {
		Campaign campaign = new Campaign("Campanha " + id);
		ReflectionTestUtils.setField(campaign, "id", id);
		campaign.setPlaylistId(playlist);
		campaign.setPriority(priority);
		campaign.replaceTargets(List.of(targets));
		return campaign;
	}

	private static Effective resolve(Device device, Campaign... campaigns) {
		return CampaignService.effectiveFor(device, NOON, List.of(campaigns));
	}

	@Test
	void withoutCampaignsTheTvShowsItsOwnPlaylist() {
		Effective effective = resolve(tv(1, 100L, null));

		assertEquals(100L, effective.playlistId());
		assertFalse(effective.fromCampaign());
	}

	@Test
	void withoutCampaignsAndWithoutAPlaylistThereIsNothing() {
		assertNull(resolve(tv(1, null, null)).playlistId());
	}

	@Test
	void anActiveCampaignForEveryoneOverridesTheBasePlaylist() {
		Effective effective = resolve(tv(1, 100L, null), campaign(10, 200, 50, CampaignTarget.all()));

		assertEquals(200L, effective.playlistId());
		assertTrue(effective.fromCampaign());
		assertEquals(10L, effective.campaign().getId());
	}

	@Test
	void aCampaignThatDoesNotReachTheTvIsIgnored() {
		Device device = tv(1, 100L, 5L, 7L);

		Effective effective = resolve(device,
				campaign(10, 200, 50, CampaignTarget.device(99)),
				campaign(11, 201, 50, CampaignTarget.location(6)),
				campaign(12, 202, 50, CampaignTarget.group(8)));

		assertEquals(100L, effective.playlistId());
	}

	@Test
	void aCampaignOutsideItsHoursIsIgnored() {
		Campaign night = campaign(10, 200, 50, CampaignTarget.all());
		night.setStartTime(LocalTime.of(22, 0));
		night.setEndTime(LocalTime.of(23, 0));

		assertEquals(100L, resolve(tv(1, 100L, null), night).playlistId());
	}

	@Test
	void targetsReachTheTvByIdByLocationAndByGroup() {
		Device device = tv(1, 100L, 5L, 7L);

		assertEquals(200L, resolve(device, campaign(10, 200, 50, CampaignTarget.device(1))).playlistId());
		assertEquals(201L, resolve(device, campaign(11, 201, 50, CampaignTarget.location(5))).playlistId());
		assertEquals(202L, resolve(device, campaign(12, 202, 50, CampaignTarget.group(7))).playlistId());
	}

	@Test
	void aTvWithoutLocationIsNotReachedByLocationTargets() {
		assertEquals(100L, resolve(tv(1, 100L, null), campaign(10, 200, 50, CampaignTarget.location(5))).playlistId());
	}

	@Test
	void theHigherPriorityWins() {
		Device device = tv(1, 100L, null);

		Effective effective = resolve(device,
				campaign(10, 200, 10, CampaignTarget.all()),
				campaign(11, 201, 90, CampaignTarget.all()),
				campaign(12, 202, 50, CampaignTarget.all()));

		assertEquals(201L, effective.playlistId());
	}

	/** Prioridade manda mesmo contra um alvo mais específico: o administrador decidiu assim. */
	@Test
	void priorityBeatsSpecificity() {
		Device device = tv(1, 100L, null);

		Effective effective = resolve(device,
				campaign(10, 200, 90, CampaignTarget.all()),
				campaign(11, 201, 10, CampaignTarget.device(1)));

		assertEquals(200L, effective.playlistId());
	}

	@Test
	void withEqualPriorityTheMostSpecificTargetWins() {
		Device device = tv(1, 100L, 5L, 7L);

		assertEquals(203L, resolve(device,
				campaign(10, 200, 50, CampaignTarget.all()),
				campaign(11, 201, 50, CampaignTarget.location(5)),
				campaign(12, 202, 50, CampaignTarget.group(7)),
				campaign(13, 203, 50, CampaignTarget.device(1))).playlistId());
		assertEquals(202L, resolve(device,
				campaign(10, 200, 50, CampaignTarget.all()),
				campaign(11, 201, 50, CampaignTarget.location(5)),
				campaign(12, 202, 50, CampaignTarget.group(7))).playlistId());
		assertEquals(201L, resolve(device,
				campaign(10, 200, 50, CampaignTarget.all()),
				campaign(11, 201, 50, CampaignTarget.location(5))).playlistId());
	}

	@Test
	void withEqualPriorityAndSpecificityTheNewestCampaignWins() {
		Effective effective = resolve(tv(1, 100L, null),
				campaign(10, 200, 50, CampaignTarget.all()),
				campaign(12, 202, 50, CampaignTarget.all()),
				campaign(11, 201, 50, CampaignTarget.all()));

		assertEquals(202L, effective.playlistId());
	}

	@Test
	void aCampaignWithSeveralTargetsCountsItsBestMatchForTheTie() {
		Device device = tv(1, 100L, 5L);

		Effective effective = resolve(device,
				campaign(10, 200, 50, CampaignTarget.location(5), CampaignTarget.device(1)),
				campaign(11, 201, 50, CampaignTarget.location(5)));

		assertEquals(200L, effective.playlistId());
	}

	@Test
	void theSameCampaignGivesDifferentResultsToDifferentTvs() {
		Campaign campaign = campaign(10, 200, 50, CampaignTarget.location(5));

		assertEquals(200L, resolve(tv(1, 100L, 5L), campaign).playlistId());
		assertEquals(101L, resolve(tv(2, 101L, 6L), campaign).playlistId());
	}

	@Test
	void reachesTellsWhoIsTargeted() {
		Campaign campaign = campaign(10, 200, 50, CampaignTarget.group(7));

		assertTrue(CampaignService.reaches(campaign, tv(1, null, null, 7L)));
		assertFalse(CampaignService.reaches(campaign, tv(2, null, null, 8L)));
	}

}
