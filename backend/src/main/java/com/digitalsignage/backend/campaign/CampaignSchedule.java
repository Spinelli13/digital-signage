package com.digitalsignage.backend.campaign;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Regra de agenda de uma campanha: período (datas inclusivas), dias da semana e janela diária, tudo no fuso da
 * própria campanha. Funções puras, sem banco, para serem testadas com relógio fixo.
 *
 * <p>Janela que atravessa a meia-noite (ex.: 22:00 às 06:00): o dia da semana e o período contam pelo dia em que a
 * janela COMEÇA. "Sexta 22:00-06:00" vale de sexta 22:00 até sábado 06:00.
 */
public final class CampaignSchedule {

	private CampaignSchedule() {
	}

	public static boolean isActive(Campaign campaign, Instant now) {
		if (!campaign.isEnabled()) {
			return false;
		}
		ZonedDateTime local = now.atZone(campaign.zone());
		LocalDate today = local.toLocalDate();
		LocalTime time = local.toLocalTime();
		LocalTime start = startOf(campaign);
		LocalTime end = campaign.getEndTime(); // nulo = até o fim do dia

		if (!crossesMidnight(campaign)) {
			return validDay(campaign, today) && !time.isBefore(start) && (end == null || time.isBefore(end));
		}
		return (!time.isBefore(start) && validDay(campaign, today))
				|| (time.isBefore(end) && validDay(campaign, today.minusDays(1)));
	}

	public static CampaignState stateAt(Campaign campaign, Instant now) {
		if (!campaign.isEnabled()) {
			return CampaignState.PAUSED;
		}
		if (isActive(campaign, now)) {
			return CampaignState.ACTIVE;
		}
		LocalDate today = now.atZone(campaign.zone()).toLocalDate();
		if (campaign.getEndDate() != null && today.isAfter(campaign.getEndDate())) {
			return CampaignState.ENDED;
		}
		if (campaign.getStartDate() != null && today.isBefore(campaign.getStartDate())) {
			return CampaignState.SCHEDULED;
		}
		return CampaignState.WAITING;
	}

	/** O fim é antes do início (ou igual a ele, que a API recusa): a janela passa da meia-noite. */
	public static boolean crossesMidnight(Campaign campaign) {
		LocalTime end = campaign.getEndTime();
		return end != null && end.isBefore(startOf(campaign));
	}

	private static LocalTime startOf(Campaign campaign) {
		return campaign.getStartTime() == null ? LocalTime.MIN : campaign.getStartTime();
	}

	private static boolean validDay(Campaign campaign, LocalDate day) {
		return campaign.days().contains(day.getDayOfWeek())
				&& (campaign.getStartDate() == null || !day.isBefore(campaign.getStartDate()))
				&& (campaign.getEndDate() == null || !day.isAfter(campaign.getEndDate()));
	}

}
