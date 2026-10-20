package com.digitalsignage.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantContext;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.user.Role;
import com.digitalsignage.backend.user.User;
import com.digitalsignage.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica o usuário do painel pelo token JWT e decide EM QUE CLIENTE a requisição atua.
 *
 * <p>O papel e o cliente vêm sempre do banco (não do token): mudar o papel de alguém, desativá-lo ou suspender um
 * cliente vale na próxima requisição, sem esperar o token vencer. O operador da plataforma pode escolher outro
 * cliente com o header {@code X-Tenant-Id}; para qualquer outro usuário o header é ignorado.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	public static final String TENANT_HEADER = "X-Tenant-Id";

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final ObjectMapper json;

	public JwtAuthFilter(JwtService jwtService, UserRepository userRepository, TenantRepository tenantRepository, ObjectMapper json) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.json = json;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		boolean contextSet = false;
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			try {
				Optional<User> found = userRepository.findByEmail(jwtService.extractEmail(header.substring(7))).filter(User::isActive);
				if (found.isPresent()) {
					User user = found.get();
					Optional<Tenant> acting = actingTenant(user, request);
					if (acting.isPresent()) {
						Tenant tenant = acting.get();
						if (!user.getRole().equals(Role.PLATFORM_ADMIN)
								&& !tenant.getStatus().canUsePanel() && !allowedWhileSuspended(request)) {
							refuse(response, tenant);
							return;
						}
						var principal = new PanelUser(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getTenantId(), tenant.getId());
						var authorities = user.getRole().authorities().stream().map(SimpleGrantedAuthority::new).toList();
						SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
						TenantContext.set(tenant.getId());
						contextSet = true;
					}
				}
			} catch (JwtException ignored) {
				// token inválido ou expirado: segue sem autenticar, endpoint protegido responde 401
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

	/** O cliente do usuário (ou o escolhido pelo operador da plataforma). Sem cliente válido, o usuário não entra. */
	private Optional<Tenant> actingTenant(User user, HttpServletRequest request) {
		String chosen = request.getHeader(TENANT_HEADER);
		if (chosen != null && !chosen.isBlank() && user.getRole().equals(Role.PLATFORM_ADMIN)) {
			try {
				long id = Long.parseLong(chosen.trim());
				if (id != TenantContext.POOL_ID) {
					Optional<Tenant> other = tenantRepository.findById(id);
					if (other.isPresent()) {
						return other;
					}
				}
			} catch (NumberFormatException ignored) {
				// header inválido: usa o cliente do próprio usuário
			}
		}
		return user.getTenantId() == null ? Optional.empty() : tenantRepository.findById(user.getTenantId());
	}

	/** Com a assinatura suspensa o cliente ainda precisa ver quem ele é e a cobrança (para regularizar). */
	private static boolean allowedWhileSuspended(HttpServletRequest request) {
		String path = request.getRequestURI();
		boolean read = "GET".equals(request.getMethod());
		return path.equals("/api/v1/auth/me") || (read && path.startsWith("/api/v1/billing"));
	}

	private void refuse(HttpServletResponse response, Tenant tenant) throws IOException {
		response.setStatus(402);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String reason = tenant.getStatus().name().equals("CANCELED")
				? "A conta \"" + tenant.getName() + "\" foi encerrada."
				: "A assinatura de \"" + tenant.getName() + "\" está suspensa. Regularize a cobrança para voltar a usar o painel (as TVs continuam exibindo o que já têm).";
		response.getWriter().write(json.writeValueAsString(Map.of("error", reason)));
	}

}
