package com.digitalsignage.backend.coupon;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um cliente que resgatou um cupom. O cliente é guardado como número (a operação consulta todos os clientes). */
@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "coupon_id", nullable = false, updatable = false)
	private Long couponId;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(name = "redeemed_by")
	private String redeemedBy;

	@Column(name = "redeemed_at", nullable = false)
	private Instant redeemedAt;

	/** Descontos: quantas faturas ainda serão descontadas. */
	@Column(name = "invoices_left")
	private Integer invoicesLeft;

	protected CouponRedemption() {
	}

	public CouponRedemption(Long couponId, Long tenantId, String redeemedBy, Instant redeemedAt, Integer invoicesLeft) {
		this.couponId = couponId;
		this.tenantId = tenantId;
		this.redeemedBy = redeemedBy;
		this.redeemedAt = redeemedAt;
		this.invoicesLeft = invoicesLeft;
	}

	public Long getId() {
		return id;
	}

	public Long getCouponId() {
		return couponId;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public String getRedeemedBy() {
		return redeemedBy;
	}

	public Instant getRedeemedAt() {
		return redeemedAt;
	}

	public Integer getInvoicesLeft() {
		return invoicesLeft;
	}

	public void setInvoicesLeft(Integer invoicesLeft) {
		this.invoicesLeft = invoicesLeft;
	}

}
