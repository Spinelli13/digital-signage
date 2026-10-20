package com.digitalsignage.backend.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

	Optional<Tenant> findBySlug(String slug);

	boolean existsBySlug(String slug);

	boolean existsByNameIgnoreCase(String name);

	/** Os clientes de verdade (sem a reserva), para as tarefas agendadas e a lista da operação. */
	@Query("select t from Tenant t where t.id <> 1 order by t.name")
	List<Tenant> findAllCustomers();

	long countByPlanId(Long planId);

}
