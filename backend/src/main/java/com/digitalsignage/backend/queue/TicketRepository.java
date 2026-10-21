package com.digitalsignage.backend.queue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

	@Query("select coalesce(max(t.number), 0) from Ticket t where t.queueId = :queueId and t.day = :day")
	int lastNumber(@Param("queueId") Long queueId, @Param("day") LocalDate day);

	/** As senhas que esperam, da mais antiga para a mais nova (a ordem de chegada). */
	@Query("select t from Ticket t where t.status = 'WAITING' and t.queueId in :queueIds order by t.issuedAt asc, t.id asc")
	List<Ticket> waitingIn(@Param("queueIds") Collection<Long> queueIds);

	@Query("select t from Ticket t where t.status in ('CALLING', 'SERVING') and t.counterId = :counterId order by t.calledAt desc")
	List<Ticket> openAt(@Param("counterId") Long counterId);

	@Query("select t from Ticket t where t.status in ('CALLING', 'SERVING')")
	List<Ticket> allOpenAtCounters();

	/** As últimas senhas chamadas (a primeira chamada de cada uma), da mais recente para a mais antiga. */
	@Query("select t from Ticket t where t.firstCalledAt is not null and t.firstCalledAt >= :since order by t.firstCalledAt desc, t.id desc")
	List<Ticket> firstCalledSince(@Param("since") Instant since, Pageable page);

	/** As últimas chamadas (inclusive as repetidas) de algumas filas: o que o painel de chamadas mostra. */
	@Query("select t from Ticket t where t.calledAt is not null and t.calledAt >= :since and t.queueId in :queueIds and t.status <> 'CANCELED' order by t.calledAt desc, t.id desc")
	List<Ticket> recentCalls(@Param("since") Instant since, @Param("queueIds") Collection<Long> queueIds, Pageable page);

	@Query("select t from Ticket t where t.day = :day and t.queueId in :queueIds")
	List<Ticket> ofDay(@Param("day") LocalDate day, @Param("queueIds") Collection<Long> queueIds);

	/** Pega a senha para o guichê só se ela ainda estiver esperando: dois atendentes nunca chamam a mesma. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Ticket t set t.status = 'CALLING', t.counterId = :counterId, t.callCount = 1, t.calledAt = :now, t.firstCalledAt = :now where t.id = :id and t.status = 'WAITING'")
	int claim(@Param("id") Long id, @Param("counterId") Long counterId, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Ticket t set t.status = 'CANCELED', t.finishedAt = :now where t.status = 'WAITING' and t.queueId = :queueId and t.day < :today")
	int cancelStale(@Param("queueId") Long queueId, @Param("today") LocalDate today, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Ticket t where t.day < :before")
	int deleteBefore(@Param("before") LocalDate before);

	@Query("select count(t) from Ticket t where t.queueId = :queueId and t.status in ('WAITING', 'CALLING', 'SERVING')")
	long openCount(@Param("queueId") Long queueId);

}
