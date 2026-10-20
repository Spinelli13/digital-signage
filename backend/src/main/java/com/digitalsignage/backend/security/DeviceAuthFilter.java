package com.digitalsignage.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.digitalsignage.backend.device.DeviceStatus;
import com.digitalsignage.backend.tenant.TenantContext;
import com.digitalsignage.backend.tenant.TenantStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica o Player pelo header {@code X-Device-Token}. O principal é o id do dispositivo e o cliente da requisição
 * passa a ser o dono da TV. Dispositivo ativo ganha ROLE_DEVICE, pendente ganha ROLE_DEVICE_PENDING e desativado não
 * autentica (o endpoint protegido responde 401 e o Player para de tentar). TVs de um cliente cancelado também não.
 *
 * <p>Cliente com assinatura suspensa só perde o painel: as TVs continuam funcionando (o conteúdo de uma loja não
 * some por causa de uma fatura em atraso).
 */
@Component
public class DeviceAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Device-Token";

	private final DeviceLookup lookup;

	public DeviceAuthFilter(DeviceLookup lookup) {
		this.lookup = lookup;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		boolean contextSet = false;
		String token = request.getHeader(HEADER);
		if (token != null && !token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
			var found = lookup.byTokenHash(TokenHasher.sha256Hex(token));
			if (found.isPresent() && authenticate(found.get())) {
				TenantContext.set(found.get().tenantId());
				contextSet = true;
			}
		}

		try {
			filterChain.doFilter(request, response);
		} finally {
			if (contextSet) {
				TenantContext.clear();
			}
		}
	}

	private boolean authenticate(DeviceLookup.DeviceRef device) {
		if (TenantStatus.CANCELED.name().equals(device.tenantStatus())) {
			return false;
		}
		String role = switch (DeviceStatus.valueOf(device.status())) {
			case ACTIVE -> "ROLE_DEVICE";
			case PENDING -> "ROLE_DEVICE_PENDING";
			case DISABLED -> null;
		};
		if (role == null) {
			return false;
		}
		var authentication = new UsernamePasswordAuthenticationToken(device.id(), null, List.of(new SimpleGrantedAuthority(role)));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return true;
	}

}
