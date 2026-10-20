package com.digitalsignage.backend.monitoring;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, Long> {

	List<Alert> findByResolvedAtIsNull();

	/** Abertos primeiro, depois os mais recentes. */
	@Query("select a from Alert a order by case when a.resolvedAt is null then 0 else 1 end, a.openedAt desc, a.id desc")
	List<Alert> findRecent(Pageable pageable);

	/** Aviso de abertura ainda não enviado. */
	@Query("select a from Alert a where a.notifiedAt is null and a.resolvedAt is null order by a.id")
	List<Alert> findNotYetNotified();

	/** Resolvidos cujo aviso de abertura saiu mas o de resolução não. */
	@Query("select a from Alert a where a.resolvedAt is not null and a.notifiedAt is not null and a.resolutionNotifiedAt is null order by a.id")
	List<Alert> findResolvedNotYetNotified();

	long countByResolvedAtIsNull();

}
