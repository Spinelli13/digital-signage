package com.digitalsignage.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.digitalsignage.backend.security.StartupSecretsGuard;

/**
 * Sem Spring completo nem banco: só o guarda, com as mesmas propriedades que o application.yml usa.
 *
 * Regressão: na primeira subida em nuvem as variáveis dos segredos ficaram em branco e o backend usaria
 * "changeme" como senha do administrador numa URL pública, sem aviso nenhum.
 */
class StartupSecretsGuardTest {

	private static final String STRONG_JWT = "x".repeat(48);

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(StartupSecretsGuard.class);

	private ApplicationContextRunner withDefaults() {
		return runner.withPropertyValues(
				"app.security.require-strong-secrets=true",
				"app.jwt.secret=" + StartupSecretsGuard.DEV_JWT_SECRET,
				"app.admin.email=" + StartupSecretsGuard.DEV_ADMIN_EMAIL,
				"app.admin.password=changeme",
				"spring.datasource.password=changeme");
	}

	@Test
	void refusesToStartWithEveryDevelopmentDefaultAndNamesAllOfThem() {
		withDefaults().run(context -> {
			assertThat(context).hasFailed();
			String message = rootMessage(context.getStartupFailure());
			assertThat(message).contains("JWT_SECRET", "ADMIN_EMAIL", "ADMIN_PASSWORD", "POSTGRES_PASSWORD");
		});
	}

	@Test
	void neverPrintsTheSecretValues() {
		withDefaults().withPropertyValues("app.admin.password=changeme").run(context -> {
			String message = rootMessage(context.getStartupFailure());
			assertThat(message).doesNotContain(StartupSecretsGuard.DEV_JWT_SECRET);
		});
	}

	@Test
	void startsWhenAllSecretsAreCustom() {
		withDefaults().withPropertyValues(
				"app.jwt.secret=" + STRONG_JWT,
				"app.admin.email=dono@empresa.com.br",
				"app.admin.password=uma-senha-de-verdade",
				"spring.datasource.password=outra-senha-forte")
				.run(context -> assertThat(context).hasNotFailed().hasSingleBean(StartupSecretsGuard.class));
	}

	@Test
	void rejectsAShortJwtSecretEvenIfItIsNotTheDefault() {
		withDefaults().withPropertyValues(
				"app.jwt.secret=curto",
				"app.admin.email=dono@empresa.com.br",
				"app.admin.password=uma-senha-de-verdade",
				"spring.datasource.password=outra-senha-forte")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(rootMessage(context.getStartupFailure())).contains("JWT_SECRET").doesNotContain("ADMIN_PASSWORD");
				});
	}

	@Test
	void rejectsABlankAdminPassword() {
		withDefaults().withPropertyValues(
				"app.jwt.secret=" + STRONG_JWT,
				"app.admin.email=dono@empresa.com.br",
				"app.admin.password=",
				"spring.datasource.password=outra-senha-forte")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(rootMessage(context.getStartupFailure())).contains("ADMIN_PASSWORD");
				});
	}

	/** Em desenvolvimento local (Docker, testes) os padrões continuam valendo: o guarda fica desligado. */
	@Test
	void isOffByDefaultSoLocalDevelopmentStillWorks() {
		runner.withPropertyValues(
				"app.jwt.secret=" + StartupSecretsGuard.DEV_JWT_SECRET,
				"app.admin.email=" + StartupSecretsGuard.DEV_ADMIN_EMAIL,
				"app.admin.password=changeme",
				"spring.datasource.password=changeme")
				.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(StartupSecretsGuard.class));
	}

	private static String rootMessage(Throwable failure) {
		Throwable t = failure;
		while (t.getCause() != null) {
			t = t.getCause();
		}
		return t.getMessage();
	}

}
