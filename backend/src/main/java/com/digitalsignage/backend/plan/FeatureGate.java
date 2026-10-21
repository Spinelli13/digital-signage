package com.digitalsignage.backend.plan;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.digitalsignage.backend.security.PanelUser;
import com.digitalsignage.backend.tenant.PlanLimits;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Barra, no servidor, o uso pelo painel de um recurso que o plano do cliente não inclui (resposta 402 com a explicação).
 * O painel também esconde o que o plano não tem, mas isso é conforto: quem decide é esta conferência.
 *
 * <p>Só vale para usuários do painel. O operador da plataforma nunca é barrado (precisa ver tudo para dar suporte) e as
 * TVs não passam por aqui: o que já está em uso continua sendo exibido.
 */
@Component
public class FeatureGate implements HandlerInterceptor {

	private final PlanLimits limits;

	public FeatureGate(PlanLimits limits) {
		this.limits = limits;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof PanelUser user) || user.isPlatformAdmin()) {
			return true;
		}
		Feature feature = Feature.forPath(request.getRequestURI());
		if (feature == null || (feature.readsStayOpen() && "GET".equals(request.getMethod()))) {
			return true;
		}
		limits.requireFeature(user.tenantId(), feature);
		return true;
	}

	@Configuration
	static class Registration implements WebMvcConfigurer {

		private final FeatureGate gate;

		Registration(FeatureGate gate) {
			this.gate = gate;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(gate).addPathPatterns("/api/v1/**");
		}
	}

}
