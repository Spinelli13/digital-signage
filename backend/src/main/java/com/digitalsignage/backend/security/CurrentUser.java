package com.digitalsignage.backend.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.digitalsignage.backend.user.Role;

/** Quem está usando o painel nesta requisição (para as regras que dependem do papel dentro de um serviço). */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Optional<PanelUser> get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof PanelUser user ? Optional.of(user) : Optional.empty();
	}

	/** O papel de quem chama; sem usuário do painel (chamada interna) vale o mais alto: a regra só restringe quem é restringível. */
	public static Role role() {
		return get().map(PanelUser::role).orElse(Role.PLATFORM_ADMIN);
	}

	public static boolean isAtLeast(Role role) {
		return role().atLeast(role);
	}

}
