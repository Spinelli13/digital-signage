package com.digitalsignage.backend;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Sobe o backend completo com um Postgres embutido que guarda os dados em backend/data/dev-pg,
 * sem precisar de Docker nem de Postgres instalado. Apenas para desenvolvimento local:
 *
 * <pre>mvn -q test-compile exec:java -Dexec.mainClass=com.digitalsignage.backend.DevApplication -Dexec.classpathScope=test</pre>
 */
public final class DevApplication {

	private static final int POSTGRES_PORT = 54329;

	private DevApplication() {
	}

	public static void main(String[] args) throws IOException {
		EmbeddedPostgres postgres = EmbeddedPostgres.builder()
				.setPort(POSTGRES_PORT)
				.setDataDirectory(Path.of("data", "dev-pg"))
				.setCleanDataDirectory(false)
				.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				postgres.close();
			} catch (IOException ignored) {
				// encerrando
			}
		}));

		System.setProperty("DB_URL", postgres.getJdbcUrl("postgres", "postgres"));
		System.setProperty("POSTGRES_USER", "postgres");
		System.setProperty("POSTGRES_PASSWORD", "postgres");

		SpringApplication.run(DigitalSignageApplication.class, args);
	}

}
