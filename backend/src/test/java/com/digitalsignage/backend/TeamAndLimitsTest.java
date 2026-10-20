package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** A equipe do cliente (usuários e papéis) e os limites do plano (TVs, espaço e usuários). */
class TeamAndLimitsTest extends AbstractApiTest {

	private record Client(long tenantId, String adminEmail, String admin) {
	}

	private Client client(String name, String planCode) throws Exception {
		long tenant = createTenantRow(name, planCode, "ACTIVE");
		String email = "admin-" + tenant + "@teste.com";
		createUserRow(tenant, email, "ADMIN", "senha-forte-1");
		return new Client(tenant, email, loginAs(email, "senha-forte-1"));
	}

	private long planWith(String code, Integer devices, Integer storageMb, Integer users) {
		return jdbc.queryForObject("INSERT INTO plans (code, name, price_cents, max_devices, max_storage_mb, max_users) VALUES (?, ?, 0, ?, ?, ?) RETURNING id",
				Long.class, code, "Plano " + code, devices, storageMb, users);
	}

	private ResultActions createUser(Client client, String email, String role) throws Exception {
		return mvc.perform(withToken(client.admin(), post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Fulano", "email", email, "password", "senha-forte-1", "role", role)))));
	}

	// ---------- equipe ----------

	@Test
	void theAdminCreatesListsAndEditsTheTeamOfTheirOwnClient() throws Exception {
		Client a = client("Cliente A", "interno");
		Client b = client("Cliente B", "interno");

		long editor = idOf(createUser(a, "editor@a.com", "EDITOR").andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("EDITOR")).andExpect(jsonPath("$.active").value(true)).andReturn().getResponse().getContentAsString());
		createUser(b, "editor@b.com", "VIEWER").andExpect(status().isCreated());

		// cada cliente vê só a própria equipe
		mvc.perform(withToken(a.admin(), get("/api/v1/users"))).andExpect(jsonPath("$", hasSize(2)));
		mvc.perform(withToken(b.admin(), get("/api/v1/users"))).andExpect(jsonPath("$", hasSize(2)));
		// o usuário novo entra e já vale o papel dele
		String token = loginAs("editor@a.com", "senha-forte-1");
		mvc.perform(withToken(token, get("/api/v1/users"))).andExpect(status().isForbidden());

		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + editor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Beltrano\",\"role\":\"VIEWER\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Beltrano")).andExpect(jsonPath("$.role").value("VIEWER"));
		// o papel novo vale na próxima requisição, com o mesmo token
		mvc.perform(withToken(token, post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"p\"}"))).andExpect(status().isForbidden());

		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + editor).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"nova-senha-2\"}"))).andExpect(status().isOk());
		loginAs("editor@a.com", "nova-senha-2");
		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + editor).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))).andExpect(status().isOk());
		mvc.perform(withToken(token, get("/api/v1/devices"))).andExpect(status().isUnauthorized());
		mvc.perform(withToken(a.admin(), delete("/api/v1/users/" + editor))).andExpect(status().isNoContent());
		mvc.perform(withToken(a.admin(), get("/api/v1/users"))).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void validatesTheNewUser() throws Exception {
		Client a = client("Cliente A", "interno");
		Client b = client("Cliente B", "interno");
		createUser(a, "dup@a.com", "EDITOR").andExpect(status().isCreated());

		// o e-mail é único em toda a plataforma (é o login), inclusive entre clientes
		createUser(a, "DUP@a.com", "EDITOR").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Já existe um usuário com o e-mail dup@a.com"));
		createUser(b, "dup@a.com", "EDITOR").andExpect(status().isConflict());
		mvc.perform(withToken(a.admin(), post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\",\"email\":\"x@a.com\",\"password\":\"curta\",\"role\":\"EDITOR\"}")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("A senha precisa ter pelo menos 8 caracteres"));
		mvc.perform(withToken(a.admin(), post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\",\"email\":\"nao-e-email\",\"password\":\"senha-forte-1\",\"role\":\"EDITOR\"}"))).andExpect(status().isBadRequest());
		// quem administra um cliente não dá o papel de operador da plataforma
		createUser(a, "op@a.com", "PLATFORM_ADMIN").andExpect(status().isForbidden());
	}

	@Test
	void everyClientKeepsAtLeastOneActiveAdministrator() throws Exception {
		Client a = client("Cliente A", "interno");
		long self = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, a.adminEmail());

		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + self).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Este é o único administrador ativo do cliente: promova outro usuário a Administrador antes"));
		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + self).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))).andExpect(status().isConflict());
		mvc.perform(withToken(a.admin(), delete("/api/v1/users/" + self))).andExpect(status().isConflict());

		// com um segundo administrador, o primeiro pode deixar o papel (mas nunca se desativar nem se excluir)
		createUser(a, "outro-admin@a.com", "ADMIN").andExpect(status().isCreated());
		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + self).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Você não pode desativar o seu próprio usuário"));
		mvc.perform(withToken(a.admin(), delete("/api/v1/users/" + self)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Você não pode excluir o seu próprio usuário"));
		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + self).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))).andExpect(status().isOk());
	}

	@Test
	void aClientCannotTouchAnotherClientsUsers() throws Exception {
		Client a = client("Cliente A", "interno");
		Client b = client("Cliente B", "interno");
		long bUser = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, b.adminEmail());

		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + bUser).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))).andExpect(status().isNotFound());
		mvc.perform(withToken(a.admin(), delete("/api/v1/users/" + bUser))).andExpect(status().isNotFound());
		assertEquals(true, jdbc.queryForObject("SELECT active FROM users WHERE id = ?", Boolean.class, bUser));
	}

	@Test
	void thePlatformOperatorIsNotEditableByAClientAdministrator() throws Exception {
		// o operador mora no cliente original; o administrador desse cliente não mexe nele
		createUserRow(DEFAULT_TENANT, "gerente@minha-empresa.com", "ADMIN", "senha-forte-1");
		String manager = loginAs("gerente@minha-empresa.com", "senha-forte-1");
		long operator = jdbc.queryForObject("SELECT id FROM users WHERE role = 'PLATFORM_ADMIN' LIMIT 1", Long.class);

		mvc.perform(withToken(manager, put("/api/v1/users/" + operator).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))).andExpect(status().isForbidden());
		mvc.perform(withToken(manager, delete("/api/v1/users/" + operator))).andExpect(status().isForbidden());
	}

	// ---------- limites do plano ----------

	@Test
	void theNumberOfUsersFollowsThePlan() throws Exception {
		planWith("dois-usuarios", null, null, 2);
		Client a = client("Cliente A", "dois-usuarios");

		createUser(a, "segundo@a.com", "EDITOR").andExpect(status().isCreated()); // o administrador + este = 2
		createUser(a, "terceiro@a.com", "VIEWER")
				.andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value("O plano Plano dois-usuarios permite até 2 usuário(s) e todos já estão em uso. Desative um usuário ou peça um plano maior."));
		// desativar um libera a vaga (e reativar exige que haja vaga)
		long second = jdbc.queryForObject("SELECT id FROM users WHERE email = 'segundo@a.com'", Long.class);
		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + second).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))).andExpect(status().isOk());
		createUser(a, "terceiro@a.com", "VIEWER").andExpect(status().isCreated());
		mvc.perform(withToken(a.admin(), put("/api/v1/users/" + second).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))).andExpect(status().isPaymentRequired());
	}

	private String pendingCode() throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		return json.readTree(mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.6.0")))).andReturn().getResponse().getContentAsString()).get("activationCode").asText();
	}

	private ResultActions activate(Client client, String code, String name) throws Exception {
		return mvc.perform(withToken(client.admin(), post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))));
	}

	@Test
	void theNumberOfTvsFollowsThePlanAndARefusedTvStaysWaiting() throws Exception {
		planWith("uma-tv", 1, null, null);
		Client a = client("Cliente A", "uma-tv");
		String first = pendingCode();
		String second = pendingCode();

		activate(a, first, "TV 1").andExpect(status().isOk());
		activate(a, second, "TV 2")
				.andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value("O plano Plano uma-tv permite até 1 TV(s) e todas já estão em uso. Exclua uma TV ou peça um plano maior."));
		// a TV recusada continua aguardando (na reserva), com o código válido
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM devices WHERE tenant_id = 1 AND status = 'PENDING'", Integer.class));

		// excluir a primeira abre a vaga
		long tv1 = jdbc.queryForObject("SELECT id FROM devices WHERE name = 'TV 1'", Long.class);
		mvc.perform(withToken(a.admin(), delete("/api/v1/devices/" + tv1))).andExpect(status().isNoContent());
		activate(a, second, "TV 2").andExpect(status().isOk());
	}

	@Test
	void theStorageFollowsThePlanAndWebPagesDoNotCountAsFiles() throws Exception {
		planWith("um-mega", null, 1, null);
		Client a = client("Cliente A", "um-mega");
		byte[] sixHundredKb = new byte[600 * 1024];

		mvc.perform(withToken(a.admin(), multipart("/api/v1/media").file(new MockMultipartFile("file", "a.png", "image/png", sixHundredKb)))).andExpect(status().isCreated());
		mvc.perform(withToken(a.admin(), multipart("/api/v1/media").file(new MockMultipartFile("file", "b.png", "image/png", sixHundredKb))))
				.andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value("O plano Plano um-mega permite até 1 MB de mídias e este arquivo passaria disso. Exclua mídias que não usa mais ou peça um plano maior."));
		mvc.perform(withToken(a.admin(), post("/api/v1/media/web").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://site.com\",\"durationSeconds\":30}"))).andExpect(status().isCreated());
		// e uma mídia de outro cliente não ocupa o espaço deste
		Client other = client("Cliente B", "interno");
		mvc.perform(withToken(other.admin(), multipart("/api/v1/media").file(new MockMultipartFile("file", "c.png", "image/png", sixHundredKb)))).andExpect(status().isCreated());
		mvc.perform(withToken(a.admin(), get("/api/v1/media"))).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void aPlanWithNoLimitsNeverRefuses() throws Exception {
		Client a = client("Cliente A", "interno");
		for (int i = 0; i < 3; i++) {
			activate(a, pendingCode(), "TV " + i).andExpect(status().isOk());
			createUser(a, "u" + i + "@a.com", "VIEWER").andExpect(status().isCreated());
		}
	}

	@Test
	void whoAmIShowsThePlanAndTheUsage() throws Exception {
		planWith("pequeno", 3, 100, 5);
		Client a = client("Cliente A", "pequeno");
		activate(a, pendingCode(), "TV 1").andExpect(status().isOk());
		mvc.perform(withToken(a.admin(), multipart("/api/v1/media").file(new MockMultipartFile("file", "a.png", "image/png", new byte[2048])))).andExpect(status().isCreated());

		mvc.perform(withToken(a.admin(), get("/api/v1/auth/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.platformAdmin").value(false))
				.andExpect(jsonPath("$.tenant.name").value("Cliente A"))
				.andExpect(jsonPath("$.tenant.status").value("ACTIVE"))
				.andExpect(jsonPath("$.tenant.plan.code").value("pequeno"))
				.andExpect(jsonPath("$.tenant.plan.maxDevices").value(3))
				.andExpect(jsonPath("$.tenant.plan.maxStorageMb").value(100))
				.andExpect(jsonPath("$.tenant.usage.devices").value(1))
				.andExpect(jsonPath("$.tenant.usage.storageBytes").value(2048))
				.andExpect(jsonPath("$.tenant.usage.users").value(1));
	}

	// ---------- assinatura suspensa ----------

	@Test
	void aSuspendedClientLosesThePanelButKeepsItsTvsRunning() throws Exception {
		Client a = client("Cliente A", "interno");
		String tvToken = UUID.randomUUID().toString().replace("-", "") + "00";
		String code = json.readTree(mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", tvToken, "playerVersion", "0.6.0")))).andReturn().getResponse().getContentAsString()).get("activationCode").asText();
		activate(a, code, "TV 1").andExpect(status().isOk());

		jdbc.update("UPDATE tenants SET status = 'SUSPENDED' WHERE id = ?", a.tenantId());

		mvc.perform(withToken(a.admin(), get("/api/v1/devices"))).andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("está suspensa")));
		mvc.perform(withToken(a.admin(), post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"p\"}"))).andExpect(status().isPaymentRequired());
		// o cliente ainda vê quem é (para entender o bloqueio); o operador da plataforma entra normalmente
		mvc.perform(withToken(a.admin(), get("/api/v1/auth/me"))).andExpect(status().isOk()).andExpect(jsonPath("$.tenant.status").value("SUSPENDED"));
		mvc.perform(asAdmin(get("/api/v1/devices")).header("X-Tenant-Id", String.valueOf(a.tenantId()))).andExpect(status().isOk());
		// as TVs seguem recebendo configuração e mandando heartbeat
		mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tvToken)).andExpect(status().isOk());
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tvToken).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.6.0\",\"configVersion\":0}")).andExpect(status().isOk());

		// já uma conta encerrada derruba também as TVs
		jdbc.update("UPDATE tenants SET status = 'CANCELED' WHERE id = ?", a.tenantId());
		mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tvToken)).andExpect(status().isUnauthorized());
		mvc.perform(withToken(a.admin(), get("/api/v1/devices"))).andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("foi encerrada")));
	}

}
