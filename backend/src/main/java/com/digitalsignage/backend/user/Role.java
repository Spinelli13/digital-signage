package com.digitalsignage.backend.user;

import java.util.List;

/**
 * Papéis de um usuário do painel. Cada papel tem tudo o que os de baixo têm:
 * <ul>
 * <li>VIEWER: só consulta;</li>
 * <li>EDITOR: cuida do conteúdo (mídias, playlists, campanhas, layouts) e escolhe o que cada TV exibe;</li>
 * <li>ADMIN: administra o cliente (TVs, locais, grupos, alertas, equipe, plano e cobrança);</li>
 * <li>PLATFORM_ADMIN: opera a plataforma (clientes, planos, faturas) e entra em qualquer cliente.</li>
 * </ul>
 */
public enum Role {
	VIEWER,
	EDITOR,
	ADMIN,
	PLATFORM_ADMIN;

	/** As autoridades do Spring Security: o papel e todos os que ele inclui (EDITOR = EDITOR + VIEWER). */
	public List<String> authorities() {
		return java.util.Arrays.stream(values()).filter(role -> role.ordinal() <= ordinal()).map(role -> "ROLE_" + role.name()).toList();
	}

	public boolean atLeast(Role other) {
		return ordinal() >= other.ordinal();
	}

	/** Papéis que um ADMIN de cliente pode dar à sua equipe (nunca o de operador da plataforma). */
	public boolean assignableByTenantAdmin() {
		return this != PLATFORM_ADMIN;
	}
}
