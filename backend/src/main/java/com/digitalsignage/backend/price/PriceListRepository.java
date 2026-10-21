package com.digitalsignage.backend.price;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	List<PriceList> findAllByOrderByNameAsc();

}
