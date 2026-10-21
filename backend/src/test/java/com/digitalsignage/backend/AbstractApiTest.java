package com.digitalsignage.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.function.Supplier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Base dos testes de API: Postgres real embutido (mesmas migrations Flyway de produção, sem Docker),
 * banco limpo antes de cada teste e helpers para montar cenários.
 * O contexto Spring e o Postgres são compartilhados por todas as subclasses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestClockConfig.class, TestWidgetHttpConfig.class })
public abstract class AbstractApiTest {

	private static final EmbeddedPostgres POSTGRES = startPostgres();
	private static final Path STORAGE_DIR = createStorageDir();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		configure(registry);
	}

	/** Compartilhado com os testes que sobem o servidor HTTP real (ver RealServerTest). */
	static void configure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
		registry.add("spring.datasource.username", () -> "postgres");
		registry.add("spring.datasource.password", () -> "postgres");
		registry.add("app.storage.local-path", STORAGE_DIR::toString);
		// Os testes chamam a avaliação do monitoramento na hora que querem (relógio controlado); sem o ciclo automático.
		registry.add("app.monitoring.enabled", () -> "false");
		// A cobrança também: os testes rodam o ciclo na hora que querem, com o relógio controlado.
		registry.add("app.billing.enabled", () -> "false");
		// ...e o webhook de teste é um servidor local (em produção só https e só endereços públicos).
		registry.add("app.monitoring.allow-insecure-webhooks", () -> "true");
	}

	/** O cliente original da instalação (migration V7): onde o administrador dos testes atua. */
	protected static final long DEFAULT_TENANT = 2L;

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected ObjectMapper json;

	@Autowired
	protected JdbcTemplate jdbc;

	/** Relógio do servidor, que os testes podem congelar (campanhas dependem da hora). */
	@Autowired
	protected MutableClock clock;

	/** Os provedores de clima, cotações e notícias: ninguém fala com a internet nos testes. */
	@Autowired
	protected TestWidgetHttpConfig.StubWidgetHttp widgetHttp;

	@Autowired
	protected com.digitalsignage.backend.widget.WidgetDataService widgetData;

	@BeforeEach
	void cleanDatabase() {
		// O administrador (tabela users) é semeado na subida do contexto e precisa sobreviver.
		clock.reset();
		widgetHttp.reset();
		widgetData.clearCache();
		jdbc.update("UPDATE monitoring_settings SET offline_after_minutes = 10, never_connected_after_hours = 24, error_burst_count = 5, webhook_url = NULL, notify_on_resolve = TRUE, events_retention_days = 90, play_log_retention_days = 14");
		jdbc.execute("TRUNCATE playlist_items, device_playlists, devices, playlists, media, locations, device_groups, campaigns, device_events, alerts, media_plays, layouts, price_lists, tickets, service_counters, service_queues, promotion_coupons, promotions RESTART IDENTITY CASCADE");
		// Clientes, usuários e planos criados pelos testes (o cliente 1 = reserva e o 2 = original, com o administrador, ficam).
		jdbc.update("DELETE FROM invoices");
		// cupons de assinatura criados pelos testes (os resgates saem junto)
		jdbc.update("DELETE FROM coupons");
		jdbc.update("UPDATE tenants SET bonus_plan_id = NULL, bonus_plan_until = NULL");
		jdbc.update("UPDATE platform_settings SET company_name = 'Smartplay', payment_instructions = 'Pague por PIX ou boleto e envie o comprovante para o suporte.', support_email = NULL, trial_days = 14, grace_days = 7, invoice_lead_days = 5");
		jdbc.update("DELETE FROM monitoring_settings WHERE tenant_id > 2");
		jdbc.update("DELETE FROM users WHERE tenant_id > 2");
		// gestores criados por testes (o da instalação, semeado na subida, fica)
		jdbc.update("DELETE FROM users WHERE role = 'PLATFORM_ADMIN' AND email <> 'admin@digitalsignage.local'");
		jdbc.update("DELETE FROM tenants WHERE id > 2");
		jdbc.update("UPDATE tenants SET plan_id = (SELECT id FROM plans WHERE code = 'interno'), status = 'ACTIVE', current_period_end = NULL, trial_ends_at = NULL WHERE id = 2");
		jdbc.update("DELETE FROM plans WHERE code NOT IN ('interno', 'gratis', 'basico', 'profissional')");
	}

	/** Chama um serviço direto (sem passar pelo filtro de login) "como" o cliente indicado. */
	protected <T> T inTenant(long tenantId, Supplier<T> action) {
		return TenantContext.callAs(tenantId, action);
	}

	protected void inTenant(long tenantId, Runnable action) {
		TenantContext.runAs(tenantId, action);
	}

	protected String adminToken() throws Exception {
		String body = mvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of(
						"email", "admin@digitalsignage.local",
						"password", "changeme"))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("token").asText();
	}

	@Autowired
	protected org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	/** Cria um cliente direto no banco (a operação da plataforma tem a própria API, testada à parte). */
	protected long createTenantRow(String name, String planCode, String status) {
		return jdbc.queryForObject(
				"INSERT INTO tenants (name, slug, plan_id, status) VALUES (?, ?, (SELECT id FROM plans WHERE code = ?), ?) RETURNING id",
				Long.class, name, name.toLowerCase().replace(' ', '-') + "-" + java.util.UUID.randomUUID().toString().substring(0, 6), planCode, status);
	}

	protected void createUserRow(long tenantId, String email, String role, String password) {
		jdbc.update("INSERT INTO users (name, email, password_hash, tenant_id, role, active) VALUES (?, ?, ?, ?, ?, true)",
				email, email, passwordEncoder.encode(password), tenantId, role);
	}

	protected String loginAs(String email, String password) throws Exception {
		String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("token").asText();
	}

	protected MockHttpServletRequestBuilder withToken(String token, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + token);
	}

	protected MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) throws Exception {
		return request.header("Authorization", "Bearer " + adminToken());
	}

	protected long createDevice(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	protected long uploadMedia(String name, String contentType, byte[] content) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", name, contentType, content);
		return idOf(mvc.perform(asAdmin(multipart("/api/v1/media").file(file)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	protected long uploadPng(String name) throws Exception {
		return uploadMedia(name, "image/png", ("png-bytes-" + name).getBytes());
	}

	protected long createPlaylist(String name, List<Long> mediaIds) throws Exception {
		List<Map<String, Object>> items = mediaIds.stream()
				.map(id -> Map.<String, Object>of("mediaId", id))
				.toList();
		return idOf(mvc.perform(asAdmin(post("/api/v1/playlists")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "items", items)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	protected void replacePlaylistItems(long playlistId, String name, List<Long> mediaIds) throws Exception {
		List<Map<String, Object>> items = mediaIds.stream()
				.map(id -> Map.<String, Object>of("mediaId", id))
				.toList();
		mvc.perform(asAdmin(put("/api/v1/playlists/" + playlistId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "items", items)))))
				.andExpect(status().isOk());
	}

	protected void assignPlaylist(long deviceId, String deviceName, long playlistId) throws Exception {
		mvc.perform(asAdmin(put("/api/v1/devices/" + deviceId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of(
						"name", deviceName,
						"currentPlaylistId", playlistId)))))
				.andExpect(status().isOk());
	}

	protected long idOf(String responseBody) throws IOException {
		JsonNode node = json.readTree(responseBody);
		return node.get("id").asLong();
	}

	private static EmbeddedPostgres startPostgres() {
		try {
			EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					postgres.close();
				} catch (IOException ignored) {
					// encerrando a JVM; nada a fazer
				}
			}));
			return postgres;
		} catch (IOException e) {
			throw new IllegalStateException("Não foi possível iniciar o Postgres embutido dos testes", e);
		}
	}

	private static Path createStorageDir() {
		try {
			Path dir = Files.createTempDirectory("signage-test-media");
			dir.toFile().deleteOnExit();
			return dir;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
