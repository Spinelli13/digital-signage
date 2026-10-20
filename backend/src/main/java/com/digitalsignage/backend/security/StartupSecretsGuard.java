package com.digitalsignage.backend.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Na nuvem, recusa iniciar se algum segredo ainda estiver no valor padrão de desenvolvimento.
 *
 * Variáveis de ambiente esquecidas caem em silêncio nos padrões do application.yml (por exemplo, o
 * administrador nasce com a senha "changeme" numa URL pública). Com REQUIRE_STRONG_SECRETS=true isso vira
 * um erro claro na subida. Só os NOMES das variáveis aparecem na mensagem, nunca os valores.
 */
@Component
@ConditionalOnProperty(name = "app.security.require-strong-secrets", havingValue = "true")
public class StartupSecretsGuard {

	public static final String DEV_JWT_SECRET = "dev-only-secret-change-me-before-any-real-deploy-0123456789";
	public static final String DEV_PASSWORD = "changeme";
	public static final String DEV_ADMIN_EMAIL = "admin@digitalsignage.local";
	public static final int MIN_JWT_SECRET_LENGTH = 32;

	public StartupSecretsGuard(
			@Value("${app.jwt.secret}") String jwtSecret,
			@Value("${app.admin.email}") String adminEmail,
			@Value("${app.admin.password}") String adminPassword,
			@Value("${spring.datasource.password}") String databasePassword) {

		List<String> problems = problems(jwtSecret, adminEmail, adminPassword, databasePassword);
		if (!problems.isEmpty()) {
			throw new IllegalStateException(
					"Configuração insegura para produção (REQUIRE_STRONG_SECRETS=true). Corrija estas variáveis: "
							+ String.join("; ", problems));
		}
	}

	static List<String> problems(String jwtSecret, String adminEmail, String adminPassword, String databasePassword) {
		List<String> problems = new ArrayList<>();

		if (isBlank(jwtSecret) || DEV_JWT_SECRET.equals(jwtSecret) || jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
			problems.add("JWT_SECRET ausente, com o valor padrão do repositório ou com menos de "
					+ MIN_JWT_SECRET_LENGTH + " caracteres");
		}
		if (isBlank(adminEmail) || DEV_ADMIN_EMAIL.equalsIgnoreCase(adminEmail.trim())) {
			problems.add("ADMIN_EMAIL ausente ou com o valor padrão (" + DEV_ADMIN_EMAIL + ")");
		}
		if (isBlank(adminPassword) || DEV_PASSWORD.equals(adminPassword)) {
			problems.add("ADMIN_PASSWORD ausente ou com o valor padrão");
		}
		if (isBlank(databasePassword) || DEV_PASSWORD.equals(databasePassword)) {
			problems.add("POSTGRES_PASSWORD ausente ou com o valor padrão");
		}

		return problems;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
