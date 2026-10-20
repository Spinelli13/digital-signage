package com.digitalsignage.backend.plan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

	Optional<Plan> findByCode(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<Plan> findAllByOrderBySortOrderAscIdAsc();

}
