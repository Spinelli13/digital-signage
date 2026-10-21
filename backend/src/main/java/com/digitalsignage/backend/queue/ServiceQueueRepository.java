package com.digitalsignage.backend.queue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ServiceQueueRepository extends JpaRepository<ServiceQueue, Long> {

	List<ServiceQueue> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

	boolean existsByPrefixIgnoreCase(String prefix);

	/** Trava a fila até o fim da transação: quem emite senha espera a vez, e os números nunca se repetem. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select q from ServiceQueue q where q.id = :id")
	Optional<ServiceQueue> lockById(@Param("id") Long id);

}
