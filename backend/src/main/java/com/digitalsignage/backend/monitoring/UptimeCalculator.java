package com.digitalsignage.backend.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Disponibilidade de UMA TV num intervalo, reconstruída a partir dos eventos de conexão (ONLINE, OFFLINE,
 * DISABLED, ENABLED), em ordem de tempo. Função pura, sem banco.
 *
 * <p>Estados: ONLINE e OFFLINE contam como tempo observado; "desativada" e "desconhecido" (antes do primeiro
 * evento ou logo depois de reativar, até o primeiro contato) não contam para nada.
 */
public final class UptimeCalculator {

	public record Result(long onlineSeconds, long offlineSeconds) {

		public long observedSeconds() {
			return onlineSeconds + offlineSeconds;
		}

		/** Percentual de tempo online sobre o tempo observado; nulo se não houve nada a observar. */
		public Double uptimePercent() {
			long observed = observedSeconds();
			return observed == 0 ? null : Math.round(onlineSeconds * 1000.0 / observed) / 10.0;
		}
	}

	private enum State {
		ONLINE,
		OFFLINE,
		EXCLUDED
	}

	private UptimeCalculator() {
	}

	/**
	 * @param connectionEvents eventos de conexão da TV até {@code to}, em ordem de tempo
	 * @param from início do intervalo (inclusive)
	 * @param to fim do intervalo (exclusivo); nunca depois de "agora"
	 */
	public static Result compute(List<DeviceEvent> connectionEvents, Instant from, Instant to) {
		if (!to.isAfter(from)) {
			return new Result(0, 0);
		}
		State state = State.EXCLUDED;
		Instant cursor = from;
		long online = 0;
		long offline = 0;

		for (DeviceEvent event : connectionEvents) {
			State next = stateAfter(event, state);
			if (event.getOccurredAt().isAfter(from)) {
				Instant at = event.getOccurredAt().isBefore(to) ? event.getOccurredAt() : to;
				long seconds = Duration.between(cursor, at).getSeconds();
				if (state == State.ONLINE) {
					online += seconds;
				} else if (state == State.OFFLINE) {
					offline += seconds;
				}
				cursor = at;
			}
			state = next;
		}
		long tail = Duration.between(cursor, to).getSeconds();
		if (state == State.ONLINE) {
			online += tail;
		} else if (state == State.OFFLINE) {
			offline += tail;
		}
		return new Result(online, offline);
	}

	private static State stateAfter(DeviceEvent event, State current) {
		return switch (EventType.valueOf(event.getType())) {
			case ONLINE -> State.ONLINE;
			case OFFLINE -> State.OFFLINE;
			case DISABLED, ENABLED -> State.EXCLUDED; // reativada: só volta a contar a partir do primeiro contato
			default -> current;
		};
	}

}
