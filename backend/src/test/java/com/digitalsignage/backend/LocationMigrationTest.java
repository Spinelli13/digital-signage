package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * A migração V3 troca o texto livre "local" de cada TV por uma tabela de locais. Em produção já existem TVs com
 * texto; este teste cria o banco no estado da V2, insere esses dados e aplica a V3 de verdade.
 */
class LocationMigrationTest {

	private static Flyway flyway(DataSource dataSource, String target) {
		return Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.target(target)
				.load();
	}

	@Test
	void existingFreeTextLocationsBecomeLocationRowsWithoutLosingAnyTv() throws Exception {
		try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
			DataSource dataSource = postgres.getPostgresDatabase();
			flyway(dataSource, "2").migrate();

			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			jdbc.update("""
					INSERT INTO devices (name, location, status) VALUES
					  ('TV A', 'Loja Centro', 'ACTIVE'),
					  ('TV B', '  loja centro ', 'ACTIVE'),
					  ('TV C', 'Filial', 'PENDING'),
					  ('TV D', NULL, 'ACTIVE'),
					  ('TV E', '   ', 'ACTIVE'),
					  ('TV F', 'LOJA CENTRO', 'DISABLED')
					""");

			flyway(dataSource, "latest").migrate();

			// nenhuma TV se perde
			assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM devices", Integer.class));

			// "Loja Centro" em três grafias vira UM local; "Filial" é outro
			List<Map<String, Object>> locations = jdbc.queryForList("SELECT name FROM locations ORDER BY lower(name)");
			assertEquals(2, locations.size());
			assertEquals("Filial", locations.get(0).get("name"));
			assertEquals("Loja Centro", locations.get(1).get("name"));

			Map<String, Long> locationOf = new java.util.HashMap<>();
			jdbc.query("SELECT name, location_id FROM devices", rs -> {
				locationOf.put(rs.getString("name"), (Long) rs.getObject("location_id"));
			});
			assertEquals(locationOf.get("TV A"), locationOf.get("TV B"));
			assertEquals(locationOf.get("TV A"), locationOf.get("TV F"));
			assertNotEquals(locationOf.get("TV A"), locationOf.get("TV C"));
			assertNull(locationOf.get("TV D"));
			assertNull(locationOf.get("TV E"), "texto só com espaços não vira local");

			// o texto antigo saiu do esquema
			assertFalse(jdbc.queryForObject(
					"SELECT count(*) FROM information_schema.columns WHERE table_name = 'devices' AND column_name = 'location'",
					Integer.class) > 0);
		}
	}

	@Test
	void aDatabaseWithoutDevicesMigratesCleanly() throws Exception {
		try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
			DataSource dataSource = postgres.getPostgresDatabase();

			flyway(dataSource, "latest").migrate();

			assertEquals(0, new JdbcTemplate(dataSource).queryForObject("SELECT count(*) FROM locations", Integer.class));
		}
	}

}
