package com.digitalsignage.backend.security;

import com.digitalsignage.backend.user.Role;

/**
 * Quem está usando o painel nesta requisição. {@code tenantId} é o cliente em que ele está ATUANDO: o do próprio
 * usuário ou, para o operador da plataforma que escolheu entrar em outro cliente, o escolhido.
 */
public record PanelUser(Long id, String email, String name, Role role, Long homeTenantId, Long tenantId) {

	public boolean isPlatformAdmin() {
		return role == Role.PLATFORM_ADMIN;
	}

}
