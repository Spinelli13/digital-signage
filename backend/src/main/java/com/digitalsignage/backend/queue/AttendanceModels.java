package com.digitalsignage.backend.queue;

import java.time.Instant;
import java.util.List;

/** O que a API devolve para o painel de atendimento. */
public final class AttendanceModels {

	private AttendanceModels() {
	}

	public record QueueResponse(Long id, String name, String prefix, boolean priority, boolean active, String timezone, long waiting) {
	}

	public record CounterResponse(Long id, String name, List<Long> queueIds, boolean active, TicketResponse current) {
	}

	public record TicketResponse(Long id, String label, Long queueId, String queueName, boolean priority, String status, Long counterId,
			String counterName, String customerName, String source, Instant issuedAt, Instant calledAt, Instant startedAt, Instant finishedAt,
			int callCount) {
	}

	public record WaitingResponse(Long id, String label, Long queueId, String queueName, boolean priority, String customerName,
			String source, Instant issuedAt, long waitingSeconds) {
	}

	/** Os números do dia (cada fila no próprio fuso). Médias em segundos; nulas quando ainda não há dados. */
	public record Today(long issued, long waiting, long serving, long served, long noShow, long canceled, Long averageWaitSeconds,
			Long averageServiceSeconds) {
	}

	public record Overview(List<QueueResponse> queues, List<CounterResponse> counters, List<WaitingResponse> waiting, Today today,
			long serverTimeMs) {
	}

}
