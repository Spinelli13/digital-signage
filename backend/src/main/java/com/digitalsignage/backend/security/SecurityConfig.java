package com.digitalsignage.backend.security;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthFilter jwtAuthFilter;
	private final DeviceAuthFilter deviceAuthFilter;
	private final List<String> allowedOrigins;

	public SecurityConfig(
			JwtAuthFilter jwtAuthFilter,
			DeviceAuthFilter deviceAuthFilter,
			@Value("${app.cors.allowed-origins}") String allowedOrigins) {
		this.jwtAuthFilter = jwtAuthFilter;
		this.deviceAuthFilter = deviceAuthFilter;
		this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Sem isso o Spring Security responde 403 a requisições anônimas; painel e Player precisam de 401.
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeHttpRequests(auth -> auth
						// Em servidor real, 4xx/5xx são reenviados internamente para /error. Sem liberar esse
						// despacho, o status verdadeiro (400, 403, 500...) é mascarado como 401 anônimo.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/api/v1/health", "/api/v1/auth/login", "/api/v1/player/register").permitAll()
						// Senha pelo celular: quem escaneia o QR Code não tem login (o segredo está no endereço).
						.requestMatchers("/api/v1/public/**").permitAll()
						// Player ainda não ativado só pode consultar o próprio estado (para exibir o código).
						.requestMatchers("/api/v1/player/status").hasAnyRole("DEVICE", "DEVICE_PENDING")
						.requestMatchers("/api/v1/player/**").hasRole("DEVICE")
						// Player ativado baixa a mídia; o painel também precisa para pré-visualizar.
						.requestMatchers(HttpMethod.GET, "/api/v1/media/*/file").hasAnyRole("VIEWER", "DEVICE")
						// Operação da plataforma (clientes, planos, faturas).
						.requestMatchers("/api/v1/platform/**").hasRole("PLATFORM_ADMIN")
						// Quem sou eu e trocar a própria senha: qualquer usuário do painel.
						.requestMatchers("/api/v1/auth/**").hasRole("VIEWER")
						// Equipe e cobrança: só quem administra o cliente (nem para consultar).
						.requestMatchers("/api/v1/users/**", "/api/v1/billing/**").hasRole("ADMIN")
						// Montar widgets (busca de cidades, pré-visualização dos dados): quem edita conteúdo.
						.requestMatchers("/api/v1/widgets/**").hasRole("EDITOR")
						// Consultar: qualquer papel.
						.requestMatchers(HttpMethod.GET, "/api/v1/**").hasRole("VIEWER")
						// Gravar conteúdo (mídias, playlists, campanhas, layouts) e escolher o que cada TV exibe: o EDITOR.
						.requestMatchers("/api/v1/media/**", "/api/v1/playlists/**", "/api/v1/campaigns/**", "/api/v1/layouts/**", "/api/v1/price-lists/**", "/api/v1/promotions/**").hasRole("EDITOR")
						// Atendimento (emitir, chamar e concluir senhas): quem edita; cadastrar filas e guichês cai no ADMIN, mais abaixo.
						.requestMatchers("/api/v1/attendance/**").hasRole("EDITOR")
						.requestMatchers(HttpMethod.PUT, "/api/v1/devices/*").hasRole("EDITOR")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/devices/*/playlist", "/api/v1/devices/*/layout").hasRole("EDITOR")
						.requestMatchers(HttpMethod.POST, "/api/v1/devices/bulk").hasRole("EDITOR")
						// O resto (ativar, excluir e agrupar TVs, locais, grupos, alertas, equipe, cobrança): o ADMIN.
						.anyRequest().hasRole("ADMIN"))
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(deviceAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Token", "X-Tenant-Id", "X-Client-Id"));
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

}
