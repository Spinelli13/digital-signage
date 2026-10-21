package com.digitalsignage.backend.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que o painel de chamadas da TV mostra: as últimas senhas chamadas (com o guichê) e quantas esperam. Vem do banco, sem
 * cache: a TV pergunta a cada poucos segundos enquanto o painel está na tela.
 */
@Service
public class QueueBoardService {

	/** Chamadas mais antigas que isto não aparecem (o painel de ontem não deve voltar no dia seguinte). */
	static final Duration RECENT = Duration.ofHours(12);
	static final int DEFAULT_CALLS = 5;

	private final ServiceQueueRepository queues;
	private final ServiceCounterRepository counters;
	private final TicketRepository tickets;
	private final Clock clock;

	public QueueBoardService(ServiceQueueRepository queues, ServiceCounterRepository counters, TicketRepository tickets, Clock clock) {
		this.queues = queues;
		this.counters = counters;
		this.tickets = tickets;
		this.clock = clock;
	}

	/** {@code settings}: {@code queueIds} (vazio = todas as filas ativas) e {@code calls} (quantas chamadas listar). */
	@Transactional(readOnly = true)
	public Map<String, Object> dataFor(Map<String, Object> settings) {
		Map<Long, ServiceQueue> active = queues.findAllByOrderByNameAsc().stream().filter(ServiceQueue::isActive)
				.collect(Collectors.toMap(ServiceQueue::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
		List<Long> wanted = idsOf(settings.get("queueIds"));
		List<Long> ids = wanted.isEmpty() ? new ArrayList<>(active.keySet()) : wanted.stream().filter(active::containsKey).toList();
		int limit = settings.get("calls") instanceof Number n ? Math.max(1, Math.min(8, n.intValue())) : DEFAULT_CALLS;

		Map<String, Object> data = new LinkedHashMap<>();
		List<Map<String, Object>> calls = new ArrayList<>();
		long waiting = 0;
		if (!ids.isEmpty()) {
			Instant since = clock.instant().minus(RECENT);
			Map<Long, ServiceCounter> counterById = counters.findAll().stream().collect(Collectors.toMap(ServiceCounter::getId, Function.identity()));
			for (Ticket ticket : tickets.recentCalls(since, ids, PageRequest.of(0, limit))) {
				ServiceQueue queue = active.get(ticket.getQueueId());
				ServiceCounter counter = ticket.getCounterId() == null ? null : counterById.get(ticket.getCounterId());
				Map<String, Object> call = new LinkedHashMap<>();
				call.put("label", ticket.getLabel());
				call.put("counter", counter == null ? "" : counter.getName());
				call.put("queue", queue == null ? "" : queue.getName());
				call.put("priority", queue != null && queue.isPriority());
				call.put("calledAtMs", ticket.getCalledAt().toEpochMilli());
				call.put("callCount", ticket.getCallCount());
				call.put("status", ticket.getStatus().name());
				calls.add(call);
			}
			waiting = tickets.waitingIn(ids).size();
		}
		data.put("calls", calls);
		data.put("waiting", waiting);
		return data;
	}

	@SuppressWarnings("unchecked")
	private static List<Long> idsOf(Object value) {
		List<Long> ids = new ArrayList<>();
		if (value instanceof List<?> list) {
			for (Object item : (List<Object>) list) {
				if (item instanceof Number number) {
					ids.add(number.longValue());
				}
			}
		}
		return ids;
	}

}
