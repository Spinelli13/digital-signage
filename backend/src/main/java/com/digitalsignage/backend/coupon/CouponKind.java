package com.digitalsignage.backend.coupon;

/** O que o cupom dá a quem o resgata. */
public enum CouponKind {

	/** Porcentagem de desconto nas próximas faturas. */
	PERCENT_OFF,
	/** Valor fixo (em centavos) de desconto nas próximas faturas. */
	AMOUNT_OFF,
	/** Dias grátis: estende o teste ou o período já pago. */
	FREE_DAYS,
	/** Um plano maior liberado por alguns dias, sem mudar o que é cobrado. */
	PLAN_UPGRADE;

	public boolean isDiscount() {
		return this == PERCENT_OFF || this == AMOUNT_OFF;
	}

}
