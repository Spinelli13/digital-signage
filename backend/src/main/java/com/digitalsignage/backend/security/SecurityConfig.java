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
						// Player ainda não ativado só pode consultar o próprio estado (para exibir o código).
						.requestMatchers("/api/v1/player/status").hasAnyRole("DEVICE", "DEVICE_PENDING")
						.requestMatchers("/api/v1/player/**").hasRole("DEVICE")
						// Player ativado baixa a mídia; o painel também precisa para pré-visualizar.
						.requestMatchers(HttpMethod.GET, "/api/v1/media/*/file").hasAnyRole("VIEWER", "DEVICE")
						// Operação da plataforma (clientes, planos, faturas).
						.requestMatchers("/api/v1/platform/**").hasRole("PLATFORM_ADMIN")
						// Quem sou eu: qualquer usuário do painel.
						.requestMatchers("/api/v1/auth/me").hasRole("VIEWER")
						// Equipe e cobrança: só quem administra o cliente (nem para consultar).
						.requestMatchers("/api/v1/users/**", "/api/v1/billing/**").hasRole("ADMIN")
						// Consultar: qualquer papel. Gravar: o ADMIN (as exceções do EDITOR vêm na próxima regra).
						.requestMatchers(HttpMethod.GET, "/api/v1/**").hasRole("VIEWER")
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
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Token", "X-Tenant-Id"));
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

}
