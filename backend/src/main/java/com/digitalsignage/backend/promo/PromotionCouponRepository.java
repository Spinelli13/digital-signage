package com.digitalsignage.backend.promo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionCouponRepository extends JpaRepository<PromotionCoupon, Long> {

	Optional<PromotionCoupon> findByCode(String code);

	boolean existsByCode(String code);

	Optional<PromotionCoupon> findByPromotionIdAndClientId(Long promotionId, String clientId);

	@Query("select c from PromotionCoupon c where c.promotionId = :promotionId order by c.claimedAt desc, c.id desc")
	List<PromotionCoupon> latest(@Param("promotionId") Long promotionId, Pageable page);

	@Query("select c.promotionId, count(c) from PromotionCoupon c where c.status = 'REDEEMED' group by c.promotionId")
	List<Object[]> redeemedByPromotion();

	@Query("select count(c) from PromotionCoupon c where c.promotionId = :promotionId and c.status = 'REDEEMED'")
	long redeemedOf(@Param("promotionId") Long promotionId);

	/** Usa o cupom só se ainda estiver valendo: dois caixas lendo o mesmo código ao mesmo tempo nunca resgatam os dois. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update PromotionCoupon c set c.status = 'REDEEMED', c.redeemedAt = :now, c.redeemedBy = :by where c.id = :id and c.status = 'ISSUED' and (c.expiresAt is null or c.expiresAt > :now)")
	int redeem(@Param("id") Long id, @Param("now") Instant now, @Param("by") String by);

	/** Pedido de titular: os cupons com o nome buscado ou com o código informado (o termo já vem com os curingas do LIKE escapados). */
	@Query("select c from PromotionCoupon c where (c.claimantName is not null and lower(c.claimantName) like lower(concat('%', :term, '%')) escape '\\') or c.code = :code order by c.claimedAt desc, c.id desc")
	List<PromotionCoupon> forPrivacy(@Param("term") String term, @Param("code") String code, Pageable page);

	/** Apaga os dados pessoais (nome, aparelho e concordância); o cupom e o resgate ficam para os números e para o caixa. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update PromotionCoupon c set c.claimantName = null, c.clientId = null, c.consentAt = null, c.consentNotice = null where c.id in :ids")
	int forgetPersonalData(@Param("ids") java.util.Collection<Long> ids);

	/** O nome da pessoa é dado pessoal: some depois do prazo, o cupom em si fica para os números. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update PromotionCoupon c set c.claimantName = null, c.consentAt = null, c.consentNotice = null where c.claimantName is not null and c.claimedAt < :before")
	int forgetNamesBefore(@Param("before") Instant before);

}
