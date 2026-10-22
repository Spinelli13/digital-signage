package com.digitalsignage.backend.queue;

import java.time.Instant;
import java.util.List;

/** O que a API devolve para o painel de atendimento. */
public final class AttendanceModels {

	private AttendanceModels() {
	}

	public record QueueResponse(Long id, String name, String prefix, boolean priority, boolean active, String timezone, long waiting,
			boolean remoteEnabled, String publicToken) {

		/** O segredo do QR Code é do Administrador: os outros papéis não o veem. */
		public QueueResponse withoutToken() {
			return new QueueResponse(id, name, prefix, priority, active, timezone, waiting, remoteEnabled, null);
		}
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

		public Overview withoutTokens() {
			return new Overview(queues.stream().map(QueueResponse::withoutToken).toList(), counters, waiting, today, serverTimeMs);
		}
	}

	/** O que a pessoa vê na página pública da fila (sem entrar no painel); {@code notice} é o aviso de privacidade do nome. */
	public record PublicQueue(String name, boolean open, long waiting, com.digitalsignage.backend.privacy.PrivacyNotice.Info notice) {
	}

	/** O que a pessoa vê da própria senha: nunca o nome nem os dados de outras pessoas. */
	public record PublicTicket(String ticketToken, String label, String queueName, String status, String counter, int ahead, long waiting,
			Long calledAtMs, int callCount) {
	}

}
