package com.digitalsignage.backend.tenant;

import java.util.function.Supplier;

/**
 * Qual cliente (tenant) está sendo atendido NESTA linha de execução. É daqui que o Hibernate tira o cliente de
 * cada consulta (ver {@link TenantIdResolver}): tudo que é lido ou gravado fica restrito a ele.
 *
 * <p>Sem nada definido vale a <b>reserva</b> (cliente 1), onde só existem TVs que ainda aguardam ativação: uma
 * requisição que por esquecimento não definiu o cliente enxerga (quase) nada em vez de enxergar tudo.
 *
 * <p>Quem define: o filtro de login do painel (cliente do usuário), o filtro do Player (cliente da TV) e as tarefas
 * agendadas (que passam por todos os clientes, um de cada vez, com {@link #runAs}).
 */
public final class TenantContext {

	/** O cliente "reserva": TVs registradas e ainda não ativadas por ninguém. Nunca é um cliente de verdade. */
	public static final long POOL_ID = 1L;

	private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static long current() {
		Long id = CURRENT.get();
		return id == null ? POOL_ID : id;
	}

	public static boolean isExplicit() {
		return CURRENT.get() != null;
	}

	public static void set(long tenantId) {
		CURRENT.set(tenantId);
	}

	public static void clear() {
		CURRENT.remove();
	}

	/** Executa {@code action} como o cliente indicado e devolve ao que era antes (mesmo se der erro). */
	public static void runAs(long tenantId, Runnable action) {
		callAs(tenantId, () -> {
			action.run();
			return null;
		});
	}

	public static <T> T callAs(long tenantId, Supplier<T> action) {
		Long previous = CURRENT.get();
		CURRENT.set(tenantId);
		try {
			return action.get();
		} finally {
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}

}
