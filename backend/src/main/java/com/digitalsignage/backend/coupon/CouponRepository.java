package com.digitalsignage.backend.coupon;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

	Optional<Coupon> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<Coupon> findAllByOrderByCreatedAtDescIdDesc();

	/**
	 * Reserva um uso do cupom de forma atômica: dois clientes resgatando o último uso ao mesmo tempo não passam os dois.
	 * Devolve 1 se conseguiu e 0 se o limite já tinha sido atingido.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Coupon c set c.redeemedCount = c.redeemedCount + 1 where c.id = :id and (c.maxRedemptions is null or c.redeemedCount < c.maxRedemptions)")
	int claim(@Param("id") Long id);

}
