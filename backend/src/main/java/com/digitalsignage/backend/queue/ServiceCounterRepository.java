package com.digitalsignage.backend.queue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCounterRepository extends JpaRepository<ServiceCounter, Long> {

	List<ServiceCounter> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

}
