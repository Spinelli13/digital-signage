package com.digitalsignage.backend.coupon;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

	boolean existsByCouponIdAndTenantId(Long couponId, Long tenantId);

	List<CouponRedemption> findByTenantIdOrderByRedeemedAtDescIdDesc(Long tenantId);

	List<CouponRedemption> findByCouponIdOrderByRedeemedAtDescIdDesc(Long couponId);

	/** Descontos deste cliente que ainda valem para alguma fatura, o mais antigo primeiro. */
	@Query("select r from CouponRedemption r where r.tenantId = :tenantId and r.invoicesLeft > 0 order by r.redeemedAt asc, r.id asc")
	List<CouponRedemption> findWithInvoicesLeft(@Param("tenantId") Long tenantId);

}
