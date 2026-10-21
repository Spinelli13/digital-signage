package com.digitalsignage.backend.promo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

	List<Promotion> findAllByOrderByCreatedAtDescIdDesc();

	boolean existsByNameIgnoreCase(String name);

	/**
	 * Reserva um cupom da promoção de forma atômica: dois consumidores pegando o último cupom ao mesmo tempo não passam os dois.
	 * Devolve 1 se conseguiu e 0 se os cupons já acabaram.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Promotion p set p.claimedCount = p.claimedCount + 1 where p.id = :id and (p.maxClaims is null or p.claimedCount < p.maxClaims)")
	int claimSlot(@Param("id") Long id);

}
