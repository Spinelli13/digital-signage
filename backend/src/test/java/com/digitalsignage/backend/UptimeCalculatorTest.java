package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.digitalsignage.backend.monitoring.DeviceEvent;
import com.digitalsignage.backend.monitoring.EventLevel;
import com.digitalsignage.backend.monitoring.EventType;
import com.digitalsignage.backend.monitoring.UptimeCalculator;

/** Disponibilidade a partir dos eventos de conexão, sem banco. */
class UptimeCalculatorTest {

	private static final Instant DAY_START = Instant.parse("2026-11-05T00:00:00Z");
	private static final Instant DAY_END = Instant.parse("2026-11-06T00:00:00Z");

	private static DeviceEvent event(EventType type, String at) {
		return new DeviceEvent(1L, type, EventLevel.INFO, type.name(), Instant.parse(at));
	}

	private static UptimeCalculator.Result compute(Instant from, Instant to, DeviceEvent... events) {
		return UptimeCalculator.compute(List.of(events), from, to);
	}

	@Test
	void aTvWithoutEventsHasNothingToObserve() {
		UptimeCalculator.Result result = compute(DAY_START, DAY_END);

		assertEquals(0, result.observedSeconds());
		assertNull(result.uptimePercent());
	}

	@Test
	void aTvOnlineFromTheStartIsFullyAvailable() {
		UptimeCalculator.Result result = compute(DAY_START, DAY_END, event(EventType.ONLINE, "2026-11-05T00:00:00Z"));

		assertEquals(86_400, result.onlineSeconds());
		assertEquals(0, result.offlineSeconds());
		assertEquals(100.0, result.uptimePercent());
	}

	@Test
	void timeBeforeTheFirstEventDoesNotCount() {
		UptimeCalculator.Result result = compute(DAY_START, DAY_END, event(EventType.ONLINE, "2026-11-05T12:00:00Z"));

		assertEquals(43_200, result.onlineSeconds());
		assertEquals(43_200, result.observedSeconds());
		assertEquals(100.0, result.uptimePercent());
	}

	@Test
	void countsOnlineAndOfflinePeriods() {
		UptimeCalculator.Result result = compute(DAY_START, DAY_END,
				event(EventType.ONLINE, "2026-11-05T00:00:00Z"),
				event(EventType.OFFLINE, "2026-11-05T08:00:00Z"),
				event(EventType.ONLINE, "2026-11-05T10:00:00Z"));

		assertEquals(8 * 3600 + 14 * 3600, result.onlineSeconds());
		assertEquals(2 * 3600, result.offlineSeconds());
		assertEquals(91.7, result.uptimePercent()); // 22h de 24h
	}

	@Test
	void theStateAtTheStartComesFromTheLastEventBeforeTheWindow() {
		UptimeCalculator.Result result = compute(DAY_START, DAY_END,
				event(EventType.ONLINE, "2026-11-01T09:00:00Z"),
				event(EventType.OFFLINE, "2026-11-04T23:00:00Z"),
				event(EventType.ONLINE, "2026-11-05T06:00:00Z"));

		assertEquals(6 * 3600, result.offlineSeconds()); // 00h às 06h ainda offline (desde 23h do dia anterior)
		assertEquals(18 * 3600, result.onlineSeconds());
	}

	@Test
	void aDisabledPeriodCountsForNothing() {
		UptimeCalculator.Result result = compute(DAY_START, DAY_END,
				event(EventType.ONLINE, "2026-11-05T00:00:00Z"),
				event(EventType.DISABLED, "2026-11-05T06:00:00Z"),
				event(EventType.ENABLED, "2026-11-05T18:00:00Z"),
				event(EventType.ONLINE, "2026-11-05T19:00:00Z"));

		assertEquals(6 * 3600 + 5 * 3600, result.onlineSeconds()); // 00-06 e 19-24
		assertEquals(0, result.offlineSeconds()); // 06-19 (desativada e esperando o primeiro contato) não conta
		assertEquals(100.0, result.uptimePercent());
	}

	@Test
	void theWindowIsClippedAtItsEnd() {
		Instant noon = Instant.parse("2026-11-05T12:00:00Z");

		UptimeCalculator.Result result = compute(DAY_START, noon,
				event(EventType.ONLINE, "2026-11-05T00:00:00Z"),
				event(EventType.OFFLINE, "2026-11-05T10:00:00Z"),
				event(EventType.ONLINE, "2026-11-05T15:00:00Z")); // depois do fim: não pesa

		assertEquals(10 * 3600, result.onlineSeconds());
		assertEquals(2 * 3600, result.offlineSeconds());
	}

	@Test
	void anEmptyOrBackwardsWindowIsZero() {
		assertEquals(0, compute(DAY_END, DAY_START, event(EventType.ONLINE, "2026-11-05T00:00:00Z")).observedSeconds());
		assertEquals(0, compute(DAY_START, DAY_START, event(EventType.ONLINE, "2026-11-05T00:00:00Z")).observedSeconds());
	}

	@Test
	void otherEventTypesDoNotChangeTheState() {
		UptimeCalculator.Result result = compute(DAY_START, DAY_END,
				event(EventType.ONLINE, "2026-11-05T00:00:00Z"),
				event(EventType.PLAYER_LOG, "2026-11-05T05:00:00Z"),
				event(EventType.PLAYLIST_CHANGED, "2026-11-05T06:00:00Z"));

		assertEquals(100.0, result.uptimePercent());
	}

}
