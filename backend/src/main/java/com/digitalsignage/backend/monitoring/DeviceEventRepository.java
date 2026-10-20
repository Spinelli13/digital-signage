package com.digitalsignage.backend.monitoring;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long> {

	/** Mais recentes primeiro; {@code beforeId} (opcional) continua de onde a página anterior parou. */
	@Query("""
			select e from DeviceEvent e
			where e.deviceId = :deviceId
			  and (:beforeId is null or e.id < :beforeId)
			  and (:type is null or e.type = :type)
			  and (:level is null or e.level = :level)
			order by e.id desc
			""")
	List<DeviceEvent> findPage(
			@Param("deviceId") Long deviceId,
			@Param("beforeId") Long beforeId,
			@Param("type") String type,
			@Param("level") String level,
			Pageable pageable);

	/** Para os relatórios (todas as TVs ou uma), no intervalo. */
	@Query("""
			select e from DeviceEvent e
			where e.occurredAt >= :from and e.occurredAt < :to
			  and (:deviceId is null or e.deviceId = :deviceId)
			  and (:type is null or e.type = :type)
			  and (:level is null or e.level = :level)
			order by e.occurredAt desc, e.id desc
			""")
	List<DeviceEvent> findBetween(
			@Param("from") Instant from,
			@Param("to") Instant to,
			@Param("deviceId") Long deviceId,
			@Param("type") String type,
			@Param("level") String level,
			Pageable pageable);

	/** Eventos que mudam a situação de conexão, em ordem de tempo (base do cálculo de disponibilidade). */
	@Query("""
			select e from DeviceEvent e
			where e.type in ('ONLINE', 'OFFLINE', 'DISABLED', 'ENABLED')
			  and e.occurredAt < :to
			order by e.deviceId, e.occurredAt, e.id
			""")
	List<DeviceEvent> findConnectionEventsBefore(@Param("to") Instant to);

	boolean existsByDeviceIdAndTypeAndMessageAndOccurredAt(Long deviceId, String type, String message, Instant occurredAt);

	long countByDeviceIdAndTypeAndLevelAndOccurredAtGreaterThanEqual(
			Long deviceId, String type, String level, Instant since);

	@Modifying
	@Transactional
	@Query("delete from DeviceEvent e where e.occurredAt < :before")
	int deleteOlderThan(@Param("before") Instant before);

}
