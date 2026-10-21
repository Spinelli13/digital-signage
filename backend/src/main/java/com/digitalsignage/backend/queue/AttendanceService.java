package com.digitalsignage.backend.queue;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.queue.AttendanceModels.CounterResponse;
import com.digitalsignage.backend.queue.AttendanceModels.Overview;
import com.digitalsignage.backend.queue.AttendanceModels.QueueResponse;
import com.digitalsignage.backend.queue.AttendanceModels.Today;
import com.digitalsignage.backend.queue.AttendanceModels.TicketResponse;
import com.digitalsignage.backend.queue.AttendanceModels.WaitingResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Fila de atendimento: filas, guichês e o caminho de cada senha (emitir, chamar, atender, concluir).
 *
 * <p>Regras que valem para todo mundo: a senha é numerada por dia e por fila (A001, A002...); quem espera há mais tempo é
 * chamado primeiro; filas prioritárias passam na frente, mas a cada {@link #PRIORITY_STREAK} chamadas prioritárias seguidas
 * uma senha normal é chamada (ninguém fica esperando para sempre); dois atendentes nunca chamam a mesma senha.
 */
@Service
public class AttendanceService {

	/** Depois de tantas chamadas prioritárias seguidas, a próxima é uma senha normal (se houver). */
	static final int PRIORITY_STREAK = 3;
	/** O nome que a pessoa informou é dado pessoal: as senhas somem depois disto. */
	public static final int RETENTION_DAYS = 30;
	private static final int MAX_WAITING_LISTED = 200;
	private static final int CLAIM_ATTEMPTS = 5;
	private static final String DEFAULT_ZONE = "America/Sao_Paulo";

	public record QueueRequest(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 3) String prefix, Boolean priority, Boolean active,
			@Size(max = 60) String timezone, Boolean remoteEnabled) {
	}

	public record CounterRequest(@NotBlank @Size(max = 40) String name, List<Long> queueIds, Boolean active) {
	}

	private final ServiceQueueRepository queues;
	private final ServiceCounterRepository counters;
	private final TicketRepository tickets;
	private final Clock clock;

	public AttendanceService(ServiceQueueRepository queues, ServiceCounterRepository counters, TicketRepository tickets, Clock clock) {
		this.queues = queues;
		this.counters = counters;
		this.tickets = tickets;
		this.clock = clock;
	}

	// ---------- filas ----------

	@Transactional(readOnly = true)
	public List<QueueResponse> listQueues() {
		return queues.findAllByOrderByNameAsc().stream().map(queue -> queueResponse(queue, openWaiting(queue))).toList();
	}

	@Transactional
	public ServiceQueue createQueue(QueueRequest request) {
		String name = cleanName(request.name(), "nome da fila");
		String prefix = cleanPrefix(request.prefix());
		if (queues.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe uma fila chamada \"" + name + "\"");
		}
		if (queues.existsByPrefixIgnoreCase(prefix)) {
			throw new ConflictException("A letra \"" + prefix + "\" já é de outra fila: cada fila precisa de uma letra diferente");
		}
		return queues.save(new ServiceQueue(name, prefix, Boolean.TRUE.equals(request.priority()), zone(request.timezone()), Boolean.TRUE.equals(request.remoteEnabled())));
	}

	@Transactional
	public ServiceQueue updateQueue(Long id, QueueRequest request) {
		ServiceQueue queue = findQueue(id);
		String name = cleanName(request.name(), "nome da fila");
		String prefix = cleanPrefix(request.prefix());
		if (!name.equalsIgnoreCase(queue.getName()) && queues.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe uma fila chamada \"" + name + "\"");
		}
		if (!prefix.equalsIgnoreCase(queue.getPrefix())) {
			if (queues.existsByPrefixIgnoreCase(prefix)) {
				throw new ConflictException("A letra \"" + prefix + "\" já é de outra fila: cada fila precisa de uma letra diferente");
			}
			// Trocar a letra no meio do dia faria o painel mostrar senhas com letras diferentes para a mesma fila.
			if (tickets.openCount(id) > 0) {
				throw new ConflictException("Só dá para trocar a letra da fila quando não há senhas esperando nem em atendimento");
			}
		}
		queue.change(name, prefix, Boolean.TRUE.equals(request.priority()), request.active() == null || request.active(), zone(request.timezone()),
				Boolean.TRUE.equals(request.remoteEnabled()));
		return queues.save(queue);
	}

	@Transactional
	public void deleteQueue(Long id) {
		ServiceQueue queue = findQueue(id);
		if (tickets.openCount(id) > 0) {
			throw new ConflictException("A fila \"" + queue.getName() + "\" ainda tem senhas esperando ou em atendimento: conclua ou cancele antes de excluir");
		}
		queues.delete(queue);
	}

	// ---------- guichês ----------

	@Transactional(readOnly = true)
	public List<CounterResponse> listCounters() {
		Map<Long, TicketResponse> current = currentByCounter();
		return counters.findAllByOrderByNameAsc().stream().map(counter -> counterResponse(counter, current.get(counter.getId()))).toList();
	}

	@Transactional
	public ServiceCounter createCounter(CounterRequest request) {
		String name = cleanName(request.name(), "nome do guichê");
		if (counters.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe um guichê chamado \"" + name + "\"");
		}
		return counters.save(new ServiceCounter(name, requireQueues(request.queueIds())));
	}

	@Transactional
	public ServiceCounter updateCounter(Long id, CounterRequest request) {
		ServiceCounter counter = findCounter(id);
		String name = cleanName(request.name(), "nome do guichê");
		if (!name.equalsIgnoreCase(counter.getName()) && counters.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Já existe um guichê chamado \"" + name + "\"");
		}
		boolean active = request.active() == null || request.active();
		if (!active && !tickets.openAt(id).isEmpty()) {
			throw new ConflictException("O guichê \"" + counter.getName() + "\" está com uma senha em andamento: conclua antes de desativá-lo");
		}
		counter.change(name, requireQueues(request.queueIds()), active);
		return counters.save(counter);
	}

	@Transactional
	public void deleteCounter(Long id) {
		ServiceCounter counter = findCounter(id);
		if (!tickets.openAt(id).isEmpty()) {
			throw new ConflictException("O guichê \"" + counter.getName() + "\" está com uma senha em andamento: conclua antes de excluí-lo");
		}
		counters.delete(counter);
	}

	// ---------- senhas ----------

	/** Emite a próxima senha da fila. {@code source}: PANEL (o atendente) ou REMOTE (a pessoa, pelo celular). */
	@Transactional
	public Ticket issue(Long queueId, String customerName, String source) {
		ServiceQueue queue = findQueue(queueId);
		if (!queue.isActive()) {
			throw new ConflictException("A fila \"" + queue.getName() + "\" está desativada");
		}
		Instant now = clock.instant();
		LocalDate day = today(queue, now);
		// Duas senhas emitidas ao mesmo tempo não podem ganhar o mesmo número: o índice único barra e o bloqueio da fila ordena.
		queues.lockById(queueId);
		int number = tickets.lastNumber(queueId, day) + 1;
		if (number == 1) {
			// Primeira senha do dia nesta fila: o que sobrou de ontem sai da fila e as senhas antigas são apagadas.
			tickets.cancelStale(queueId, day, now);
			tickets.deleteBefore(day.minusDays(RETENTION_DAYS));
		}
		String label = queue.getPrefix() + String.format("%03d", number);
		Ticket ticket = new Ticket(queueId, day, number, label, cleanCustomer(customerName), source, now);
		if ("REMOTE".equals(source)) {
			ticket.assignPublicToken(Tokens.random());
		}
		return tickets.save(ticket);
	}

	// ---------- senha pelo celular (chamado pela área pública, já com o cliente definido) ----------

	/** Teto de senhas pegas pelo celular que podem estar esperando ao mesmo tempo numa fila. */
	static final int MAX_REMOTE_WAITING = 300;

	@Transactional(readOnly = true)
	public AttendanceModels.PublicQueue publicQueue(Long queueId) {
		ServiceQueue queue = findQueue(queueId);
		return new AttendanceModels.PublicQueue(queue.getName(), queue.isActive() && queue.isRemoteEnabled(), openWaiting(queue));
	}

	@Transactional
	public AttendanceModels.PublicTicket issueRemote(Long queueId, String customerName) {
		ServiceQueue queue = findQueue(queueId);
		if (!queue.isRemoteEnabled() || !queue.isActive()) {
			throw new NoSuchElementException("Este QR Code não está mais ativo. Procure o atendimento.");
		}
		if (tickets.remoteWaiting(queueId) >= MAX_REMOTE_WAITING) {
			throw new com.digitalsignage.backend.common.RateLimitException("A fila está cheia no momento. Procure o atendimento.");
		}
		return publicTicket(issue(queueId, customerName, "REMOTE"));
	}

	@Transactional(readOnly = true)
	public AttendanceModels.PublicTicket publicTicket(Long ticketId) {
		return publicTicket(findTicket(ticketId));
	}

	/** A pessoa desistiu: sai da fila (só enquanto a senha ainda não foi chamada). */
	@Transactional
	public AttendanceModels.PublicTicket cancelRemote(Long ticketId) {
		return publicTicket(cancel(ticketId));
	}

	private AttendanceModels.PublicTicket publicTicket(Ticket ticket) {
		ServiceQueue queue = findQueue(ticket.getQueueId());
		String counter = null;
		if (ticket.getCounterId() != null) {
			counter = counters.findById(ticket.getCounterId()).map(ServiceCounter::getName).orElse(null);
		}
		List<Ticket> waiting = tickets.waitingIn(List.of(queue.getId()));
		int ahead = ticket.getStatus() == Ticket.Status.WAITING
				? (int) waiting.stream().filter(other -> other.getIssuedAt().isBefore(ticket.getIssuedAt()) || (other.getIssuedAt().equals(ticket.getIssuedAt()) && other.getId() < ticket.getId())).count()
				: 0;
		return new AttendanceModels.PublicTicket(ticket.getPublicToken(), ticket.getLabel(), queue.getName(), ticket.getStatus().name(), counter, ahead,
				waiting.size(), ticket.getCalledAt() == null ? null : ticket.getCalledAt().toEpochMilli(), ticket.getCallCount());
	}

	/** O guichê chama a próxima senha das filas que atende (prioritárias primeiro, com a rodada de justiça). */
	@Transactional
	public Ticket callNext(Long counterId) {
		ServiceCounter counter = findCounter(counterId);
		if (!counter.isActive()) {
			throw new ConflictException("O guichê \"" + counter.getName() + "\" está desativado");
		}
		List<Ticket> open = tickets.openAt(counterId);
		if (!open.isEmpty()) {
			throw new ConflictException("O guichê \"" + counter.getName() + "\" ainda está com a senha " + open.get(0).getLabel()
					+ ": conclua o atendimento antes de chamar a próxima");
		}
		List<ServiceQueue> served = queuesOf(counter);
		if (served.isEmpty()) {
			throw new ConflictException("O guichê \"" + counter.getName() + "\" não atende nenhuma fila ativa");
		}
		Instant now = clock.instant();
		Map<Long, ServiceQueue> byId = served.stream().collect(Collectors.toMap(ServiceQueue::getId, queue -> queue));
		served.forEach(queue -> tickets.cancelStale(queue.getId(), today(queue, now), now));

		for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
			List<Ticket> waiting = tickets.waitingIn(byId.keySet());
			Optional<Ticket> choice = choose(waiting, byId, recentPriorityStreak(now));
			if (choice.isEmpty()) {
				throw new ConflictException("Não há senhas aguardando para este guichê");
			}
			Long id = choice.get().getId();
			if (tickets.claim(id, counterId, now) == 1) {
				return tickets.findById(id).orElseThrow();
			}
		}
		throw new ConflictException("Outro atendente chamou a mesma senha agora. Tente de novo");
	}

	/** Chama de novo (a TV toca o aviso outra vez): só para a senha que ainda está sendo chamada. */
	@Transactional
	public Ticket recall(Long ticketId) {
		Ticket ticket = findTicket(ticketId);
		requireStatus(ticket, Ticket.Status.CALLING);
		ticket.call(ticket.getCounterId(), clock.instant());
		return tickets.save(ticket);
	}

	/** A pessoa chegou ao guichê: começa o atendimento. */
	@Transactional
	public Ticket start(Long ticketId) {
		Ticket ticket = findTicket(ticketId);
		requireStatus(ticket, Ticket.Status.CALLING);
		ticket.start(clock.instant());
		return tickets.save(ticket);
	}

	@Transactional
	public Ticket finish(Long ticketId) {
		Ticket ticket = findTicket(ticketId);
		requireStatus(ticket, Ticket.Status.CALLING, Ticket.Status.SERVING);
		Instant now = clock.instant();
		if (ticket.getStartedAt() == null) {
			ticket.start(now);
		}
		ticket.finish(Ticket.Status.DONE, now);
		return tickets.save(ticket);
	}

	/** A pessoa foi chamada e não apareceu. */
	@Transactional
	public Ticket noShow(Long ticketId) {
		Ticket ticket = findTicket(ticketId);
		requireStatus(ticket, Ticket.Status.CALLING);
		ticket.finish(Ticket.Status.NO_SHOW, clock.instant());
		return tickets.save(ticket);
	}

	/** A pessoa desistiu e saiu da fila. */
	@Transactional
	public Ticket cancel(Long ticketId) {
		Ticket ticket = findTicket(ticketId);
		requireStatus(ticket, Ticket.Status.WAITING);
		ticket.finish(Ticket.Status.CANCELED, clock.instant());
		return tickets.save(ticket);
	}

	// ---------- tela do atendente ----------

	@Transactional
	public Overview overview() {
		Instant now = clock.instant();
		List<ServiceQueue> all = queues.findAllByOrderByNameAsc();
		all.forEach(queue -> tickets.cancelStale(queue.getId(), today(queue, now), now));
		Map<Long, ServiceQueue> byId = all.stream().collect(Collectors.toMap(ServiceQueue::getId, queue -> queue));

		List<Ticket> waiting = byId.isEmpty() ? List.of() : tickets.waitingIn(byId.keySet());
		Map<Long, Long> waitingPerQueue = waiting.stream().collect(Collectors.groupingBy(Ticket::getQueueId, Collectors.counting()));
		List<QueueResponse> queueViews = all.stream().map(queue -> queueResponse(queue, waitingPerQueue.getOrDefault(queue.getId(), 0L))).toList();

		Map<Long, ServiceCounter> counterById = counters.findAllByOrderByNameAsc().stream().collect(Collectors.toMap(ServiceCounter::getId, c -> c, (a, b) -> a, java.util.LinkedHashMap::new));
		Map<Long, TicketResponse> current = new HashMap<>();
		for (Ticket ticket : tickets.allOpenAtCounters()) {
			current.put(ticket.getCounterId(), ticketResponse(ticket, byId, counterById));
		}
		List<CounterResponse> counterViews = counterById.values().stream().map(counter -> counterResponse(counter, current.get(counter.getId()))).toList();

		List<WaitingResponse> waitingViews = waiting.stream().limit(MAX_WAITING_LISTED).map(ticket -> {
			ServiceQueue queue = byId.get(ticket.getQueueId());
			return new WaitingResponse(ticket.getId(), ticket.getLabel(), ticket.getQueueId(), queue.getName(), queue.isPriority(),
					ticket.getCustomerName(), ticket.getSource(), ticket.getIssuedAt(), Duration.between(ticket.getIssuedAt(), now).toSeconds());
		}).toList();
		return new Overview(queueViews, counterViews, waitingViews, today(all, now), now.toEpochMilli());
	}

	// ---------- internos ----------

	/** Prioritárias primeiro, mas depois de {@link #PRIORITY_STREAK} seguidas chama uma normal; dentro de cada grupo, por ordem de chegada. */
	static Optional<Ticket> choose(List<Ticket> waiting, Map<Long, ServiceQueue> queues, int priorityStreak) {
		Ticket firstPriority = waiting.stream().filter(ticket -> queues.get(ticket.getQueueId()).isPriority()).findFirst().orElse(null);
		Ticket firstNormal = waiting.stream().filter(ticket -> !queues.get(ticket.getQueueId()).isPriority()).findFirst().orElse(null);
		if (firstPriority != null && firstNormal != null) {
			return Optional.of(priorityStreak >= PRIORITY_STREAK ? firstNormal : firstPriority);
		}
		return Optional.ofNullable(firstPriority != null ? firstPriority : firstNormal);
	}

	/** Quantas das últimas senhas chamadas (a primeira chamada de cada uma) foram prioritárias, em sequência. */
	private int recentPriorityStreak(Instant now) {
		Map<Long, Boolean> priority = queues.findAll().stream().collect(Collectors.toMap(ServiceQueue::getId, ServiceQueue::isPriority));
		int streak = 0;
		for (Ticket ticket : tickets.firstCalledSince(now.minus(Duration.ofHours(12)), PageRequest.of(0, PRIORITY_STREAK))) {
			if (!priority.getOrDefault(ticket.getQueueId(), false)) {
				break;
			}
			streak++;
		}
		return streak;
	}

	private List<ServiceQueue> queuesOf(ServiceCounter counter) {
		List<ServiceQueue> all = queues.findAllByOrderByNameAsc().stream().filter(ServiceQueue::isActive).toList();
		if (counter.getQueueIds().isEmpty()) {
			return all;
		}
		return all.stream().filter(queue -> counter.getQueueIds().contains(queue.getId())).toList();
	}

	private List<Long> requireQueues(List<Long> queueIds) {
		if (queueIds == null || queueIds.isEmpty()) {
			return List.of();
		}
		Set<Long> unique = new LinkedHashSet<>(queueIds);
		for (Long id : unique) {
			if (id == null || !queues.existsById(id)) {
				throw new NoSuchElementException("Fila não encontrada: " + id);
			}
		}
		return new ArrayList<>(unique);
	}

	private long openWaiting(ServiceQueue queue) {
		return tickets.waitingIn(List.of(queue.getId())).size();
	}

	private Map<Long, TicketResponse> currentByCounter() {
		Map<Long, ServiceQueue> byId = queues.findAll().stream().collect(Collectors.toMap(ServiceQueue::getId, queue -> queue));
		Map<Long, ServiceCounter> counterById = counters.findAll().stream().collect(Collectors.toMap(ServiceCounter::getId, c -> c));
		Map<Long, TicketResponse> current = new HashMap<>();
		for (Ticket ticket : tickets.allOpenAtCounters()) {
			current.put(ticket.getCounterId(), ticketResponse(ticket, byId, counterById));
		}
		return current;
	}

	public TicketResponse ticketResponse(Ticket ticket) {
		Map<Long, ServiceQueue> byId = queues.findAll().stream().collect(Collectors.toMap(ServiceQueue::getId, queue -> queue));
		Map<Long, ServiceCounter> counterById = counters.findAll().stream().collect(Collectors.toMap(ServiceCounter::getId, c -> c));
		return ticketResponse(ticket, byId, counterById);
	}

	private static TicketResponse ticketResponse(Ticket ticket, Map<Long, ServiceQueue> queues, Map<Long, ServiceCounter> counters) {
		ServiceQueue queue = queues.get(ticket.getQueueId());
		ServiceCounter counter = ticket.getCounterId() == null ? null : counters.get(ticket.getCounterId());
		return new TicketResponse(ticket.getId(), ticket.getLabel(), ticket.getQueueId(), queue == null ? null : queue.getName(),
				queue != null && queue.isPriority(), ticket.getStatus().name(), ticket.getCounterId(), counter == null ? null : counter.getName(),
				ticket.getCustomerName(), ticket.getSource(), ticket.getIssuedAt(), ticket.getCalledAt(), ticket.getStartedAt(),
				ticket.getFinishedAt(), ticket.getCallCount());
	}

	private static QueueResponse queueResponse(ServiceQueue queue, long waiting) {
		return new QueueResponse(queue.getId(), queue.getName(), queue.getPrefix(), queue.isPriority(), queue.isActive(), queue.getTimezone(), waiting,
				queue.isRemoteEnabled(), queue.getPublicToken());
	}

	/** Só o que o painel mostra de uma fila recém-salva (a quantidade que espera vem na consulta seguinte). */
	public static QueueResponse responseOf(ServiceQueue queue) {
		return queueResponse(queue, 0);
	}

	private static CounterResponse counterResponse(ServiceCounter counter, TicketResponse current) {
		return new CounterResponse(counter.getId(), counter.getName(), counter.getQueueIds(), counter.isActive(), current);
	}

	private Today today(List<ServiceQueue> all, Instant now) {
		long issued = 0, waiting = 0, serving = 0, served = 0, noShow = 0, canceled = 0;
		long waitSum = 0, waitCount = 0, serviceSum = 0, serviceCount = 0;
		for (ServiceQueue queue : all) {
			for (Ticket ticket : tickets.ofDay(today(queue, now), List.of(queue.getId()))) {
				issued++;
				switch (ticket.getStatus()) {
					case WAITING -> waiting++;
					case CALLING, SERVING -> serving++;
					case DONE -> served++;
					case NO_SHOW -> noShow++;
					case CANCELED -> canceled++;
				}
				if (ticket.getFirstCalledAt() != null) {
					waitSum += Duration.between(ticket.getIssuedAt(), ticket.getFirstCalledAt()).toSeconds();
					waitCount++;
				}
				if (ticket.getStatus() == Ticket.Status.DONE && ticket.getStartedAt() != null && ticket.getFinishedAt() != null) {
					serviceSum += Duration.between(ticket.getStartedAt(), ticket.getFinishedAt()).toSeconds();
					serviceCount++;
				}
			}
		}
		return new Today(issued, waiting, serving, served, noShow, canceled, waitCount == 0 ? null : waitSum / waitCount,
				serviceCount == 0 ? null : serviceSum / serviceCount);
	}

	LocalDate today(ServiceQueue queue, Instant now) {
		return LocalDate.ofInstant(now, ZoneId.of(queue.getTimezone()));
	}

	private ServiceQueue findQueue(Long id) {
		return queues.findById(id).orElseThrow(() -> new NoSuchElementException("Fila não encontrada: " + id));
	}

	private ServiceCounter findCounter(Long id) {
		return counters.findById(id).orElseThrow(() -> new NoSuchElementException("Guichê não encontrado: " + id));
	}

	private Ticket findTicket(Long id) {
		return tickets.findById(id).orElseThrow(() -> new NoSuchElementException("Senha não encontrada: " + id));
	}

	private static void requireStatus(Ticket ticket, Ticket.Status... allowed) {
		for (Ticket.Status status : allowed) {
			if (ticket.getStatus() == status) {
				return;
			}
		}
		throw new ConflictException("A senha " + ticket.getLabel() + " " + describe(ticket.getStatus()) + ": esta ação não se aplica");
	}

	private static String describe(Ticket.Status status) {
		return switch (status) {
			case WAITING -> "está aguardando";
			case CALLING -> "está sendo chamada";
			case SERVING -> "está em atendimento";
			case DONE -> "já foi concluída";
			case NO_SHOW -> "foi marcada como não compareceu";
			case CANCELED -> "foi cancelada";
		};
	}

	private static String cleanName(String value, String what) {
		String text = value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("Informe o " + what);
		}
		return text;
	}

	private static String cleanPrefix(String value) {
		String text = value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
		if (!text.matches("[A-Z0-9]{1,3}")) {
			throw new IllegalArgumentException("A letra da senha tem de 1 a 3 letras ou números (ex.: A, P, VIP)");
		}
		return text;
	}

	private static String cleanCustomer(String value) {
		if (value == null) {
			return null;
		}
		String text = value.replaceAll("\\p{Cntrl}", " ").strip();
		if (text.length() > 60) {
			throw new IllegalArgumentException("O nome aceita no máximo 60 caracteres");
		}
		return text.isEmpty() ? null : text;
	}

	private static String zone(String value) {
		String id = value == null || value.isBlank() ? DEFAULT_ZONE : value.strip();
		try {
			return ZoneId.of(id).getId();
		} catch (DateTimeException e) {
			throw new IllegalArgumentException("Fuso horário desconhecido: " + id);
		}
	}

}
