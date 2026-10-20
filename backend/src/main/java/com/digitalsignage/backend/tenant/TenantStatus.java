package com.digitalsignage.backend.tenant;

/**
 * Situação de um cliente (assinatura).
 * <ul>
 * <li>SYSTEM: a reserva das TVs sem cliente (não é um cliente de verdade);</li>
 * <li>TRIAL: período de teste;</li>
 * <li>ACTIVE: em dia;</li>
 * <li>PAST_DUE: fatura vencida, tudo ainda funciona (carência);</li>
 * <li>SUSPENDED: painel bloqueado para o cliente; as TVs seguem exibindo o que já têm;</li>
 * <li>CANCELED: encerrado.</li>
 * </ul>
 */
public enum TenantStatus {
	SYSTEM,
	TRIAL,
	ACTIVE,
	PAST_DUE,
	SUSPENDED,
	CANCELED;

	/** O cliente pode usar o painel (inclusive para gravar)? */
	public boolean canUsePanel() {
		return this == TRIAL || this == ACTIVE || this == PAST_DUE;
	}
}
