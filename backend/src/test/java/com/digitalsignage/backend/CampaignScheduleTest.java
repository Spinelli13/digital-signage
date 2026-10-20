package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.digitalsignage.backend.campaign.Campaign;
import com.digitalsignage.backend.campaign.CampaignSchedule;
import com.digitalsignage.backend.campaign.CampaignState;

/**
 * Regra de agenda das campanhas, sem banco. Fuso padrão: America/Sao_Paulo (UTC-3, sem horário de verão).
 * Calendário usado: 05/11/2026 é quinta-feira; 06 sexta; 07 sábado; 08 domingo; 09 segunda.
 */
class CampaignScheduleTest {

	/** Instante a partir de uma data/hora de parede em São Paulo (UTC-3). */
	private static Instant parseSp(String date, String time) {
		return java.time.ZonedDateTime
				.of(LocalDate.parse(date), LocalTime.parse(time), java.time.ZoneId.of("America/Sao_Paulo"))
				.toInstant();
	}

	private static Campaign campaign() {
		return new Campaign("Teste");
	}

	private static Campaign window(String start, String end) {
		Campaign campaign = campaign();
		campaign.setStartTime(start == null ? null : LocalTime.parse(start));
		campaign.setEndTime(end == null ? null : LocalTime.parse(end));
		return campaign;
	}

	@Test
	void withoutAnyLimitItIsAlwaysActive() {
		Campaign campaign = campaign();

		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "00:00")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-08", "23:59")));
	}

	@Test
	void aPausedCampaignIsNeverActive() {
		Campaign campaign = campaign();
		campaign.setEnabled(false);

		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "12:00")));
		assertEquals(CampaignState.PAUSED, CampaignSchedule.stateAt(campaign, parseSp("2026-11-05", "12:00")));
	}

	@Test
	void aDailyWindowStartsAtTheStartAndEndsExclusivelyAtTheEnd() {
		Campaign campaign = window("18:00", "23:00");

		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "17:59")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "18:00")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "22:59")));
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "23:00")));
	}

	/** Regressão de fuso: a janela é em horário de São Paulo, não em UTC. */
	@Test
	void theWindowIsEvaluatedInTheCampaignTimezoneNotInUtc() {
		Campaign campaign = window("18:00", "23:00");

		assertFalse(CampaignSchedule.isActive(campaign, Instant.parse("2026-11-05T20:59:59Z"))); // 17:59:59 em SP
		assertTrue(CampaignSchedule.isActive(campaign, Instant.parse("2026-11-05T21:00:00Z"))); // 18:00 em SP
		assertTrue(CampaignSchedule.isActive(campaign, Instant.parse("2026-11-06T01:59:59Z"))); // 22:59:59 em SP
		assertFalse(CampaignSchedule.isActive(campaign, Instant.parse("2026-11-06T02:00:00Z"))); // 23:00 em SP
	}

	@Test
	void anotherTimezoneMovesTheWindow() {
		Campaign campaign = window("18:00", "23:00");
		campaign.setTimezone("America/New_York"); // UTC-5 em novembro (já sem horário de verão)

		assertTrue(CampaignSchedule.isActive(campaign, Instant.parse("2026-11-05T23:30:00Z"))); // 18:30 em NY
		assertFalse(CampaignSchedule.isActive(campaign, Instant.parse("2026-11-05T21:30:00Z"))); // 16:30 em NY
	}

	@Test
	void onlyAStartTimeMeansUntilTheEndOfTheDay() {
		Campaign campaign = window("08:00", null);

		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "07:59")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "08:00")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "23:59")));
	}

	@Test
	void onlyAnEndTimeMeansFromMidnight() {
		Campaign campaign = window(null, "12:00");

		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "00:00")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "11:59")));
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "12:00")));
	}

	@Test
	void theDatePeriodIncludesBothEndDays() {
		Campaign campaign = campaign();
		campaign.setStartDate(LocalDate.parse("2026-11-05"));
		campaign.setEndDate(LocalDate.parse("2026-11-07"));

		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-04", "23:59")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-05", "00:00")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-07", "23:59")));
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-08", "00:00")));
	}

	@Test
	void onlyTheChosenWeekdaysCount() {
		Campaign campaign = campaign();
		campaign.setDays(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "10:00"))); // sexta
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-07", "10:00"))); // sábado
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-08", "10:00"))); // domingo
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-09", "10:00"))); // segunda
	}

	@Test
	void aWindowThatCrossesMidnightBelongsToTheDayItStarted() {
		Campaign campaign = window("22:00", "06:00");
		campaign.setDays(Set.of(DayOfWeek.FRIDAY));

		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "21:59"))); // sexta antes de começar
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "22:00"))); // sexta, começou
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "23:59")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-07", "03:00"))); // sábado de madrugada: ainda é a janela de sexta
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-07", "06:00"))); // terminou
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-07", "23:00"))); // sábado à noite: sábado não vale
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "03:00"))); // sexta de madrugada: seria a janela de quinta
	}

	@Test
	void aWindowThatCrossesMidnightFinishesEvenAfterTheLastDayOfThePeriod() {
		Campaign campaign = window("22:00", "06:00");
		campaign.setEndDate(LocalDate.parse("2026-11-06")); // sexta

		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "23:00")));
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-07", "05:59"))); // a janela de sexta ainda termina
		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-07", "22:30"))); // a de sábado já não começa
	}

	@Test
	void aWindowThatCrossesMidnightDoesNotStartBeforeTheFirstDay() {
		Campaign campaign = window("22:00", "06:00");
		campaign.setStartDate(LocalDate.parse("2026-11-06"));

		assertFalse(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "03:00"))); // seria a janela de quinta
		assertTrue(CampaignSchedule.isActive(campaign, parseSp("2026-11-06", "22:30")));
	}

	@Test
	void detectsWindowsThatCrossMidnight() {
		assertTrue(CampaignSchedule.crossesMidnight(window("22:00", "06:00")));
		assertFalse(CampaignSchedule.crossesMidnight(window("08:00", "18:00")));
		assertFalse(CampaignSchedule.crossesMidnight(window(null, null)));
		assertFalse(CampaignSchedule.crossesMidnight(window("08:00", null)));
		assertFalse(CampaignSchedule.crossesMidnight(window(null, "00:00")));
	}

	@Test
	void describesTheStateOfTheCampaignAtAMoment() {
		Campaign campaign = window("18:00", "23:00");
		campaign.setStartDate(LocalDate.parse("2026-11-05"));
		campaign.setEndDate(LocalDate.parse("2026-11-06"));

		assertEquals(CampaignState.SCHEDULED, CampaignSchedule.stateAt(campaign, parseSp("2026-11-04", "19:00")));
		assertEquals(CampaignState.ACTIVE, CampaignSchedule.stateAt(campaign, parseSp("2026-11-05", "19:00")));
		assertEquals(CampaignState.WAITING, CampaignSchedule.stateAt(campaign, parseSp("2026-11-05", "10:00")));
		assertEquals(CampaignState.ENDED, CampaignSchedule.stateAt(campaign, parseSp("2026-11-07", "19:00")));
	}

}
