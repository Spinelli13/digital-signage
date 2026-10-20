package com.digitalsignage.backend.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	List<Invoice> findByTenantIdOrderByCreatedAtDescIdDesc(Long tenantId);

	List<Invoice> findAllByOrderByCreatedAtDescIdDesc();

	@Query("select i from Invoice i where i.status = 'OPEN' and i.tenantId = :tenantId")
	List<Invoice> findOpenOf(@Param("tenantId") Long tenantId);

	/** Já existe fatura (aberta ou paga) para o período que começa neste instante? Evita emitir a mesma cobrança duas vezes. */
	@Query("select count(i) > 0 from Invoice i where i.tenantId = :tenantId and i.status <> 'VOID' and i.periodStart = :start")
	boolean existsForPeriodStart(@Param("tenantId") Long tenantId, @Param("start") Instant start);

	@Query("select count(i) from Invoice i where i.status = 'OPEN' and i.dueDate < :today and i.tenantId = :tenantId")
	long countOverdue(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);

}
