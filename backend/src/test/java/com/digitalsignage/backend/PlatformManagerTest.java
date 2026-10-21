package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Os gestores da plataforma (o sócio entra com o próprio login). */
class PlatformManagerTest extends AbstractApiTest {

	private ResultActions create(String name, String email, String password) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/platform/managers").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "email", email, "password", password)))));
	}

	@Test
	void aManagerCreatesAnotherManagerWhoCanLogInAndOperateThePlatform() throws Exception {
		create("Sócio", "Socio@Exemplo.com", "senha-forte-1").andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("socio@exemplo.com")).andExpect(jsonPath("$.active").value(true));

		String partner = loginAs("socio@exemplo.com", "senha-forte-1");
		// a senha que o gestor definiu é provisória: o sócio troca antes de operar
		mvc.perform(withToken(partner, put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"senha-forte-1\",\"newPassword\":\"senha-do-socio-1\"}"))).andExpect(status().isNoContent());
		mvc.perform(withToken(partner, get("/api/v1/auth/me"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("PLATFORM_ADMIN")).andExpect(jsonPath("$.platformAdmin").value(true));
		mvc.perform(withToken(partner, get("/api/v1/platform/plans"))).andExpect(status().isOk());
		mvc.perform(withToken(partner, get("/api/v1/platform/managers"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void listsOnlyManagers() throws Exception {
		createUserRow(DEFAULT_TENANT, "gerente@cliente.com", "ADMIN", "senha-forte-1");
		mvc.perform(asAdmin(get("/api/v1/platform/managers"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].email", hasItem("admin@digitalsignage.local")))
				.andExpect(jsonPath("$[?(@.email=='gerente@cliente.com')]").isEmpty());
	}

	@Test
	void aClientAdministratorCannotReachTheManagers() throws Exception {
		long tenant = createTenantRow("Cliente", "interno", "ACTIVE");
		createUserRow(tenant, "dono@cliente.com", "ADMIN", "senha-forte-1");
		String owner = loginAs("dono@cliente.com", "senha-forte-1");

		mvc.perform(withToken(owner, get("/api/v1/platform/managers"))).andExpect(status().isForbidden());
		mvc.perform(withToken(owner, post("/api/v1/platform/managers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\",\"email\":\"x@x.com\",\"password\":\"senha-forte-1\"}"))).andExpect(status().isForbidden());
	}

	@Test
	void validatesTheNewManager() throws Exception {
		create("Sócio", "socio@exemplo.com", "senha-forte-1").andExpect(status().isCreated());
		create("Outro", "SOCIO@exemplo.com", "senha-forte-1").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Já existe um usuário com o e-mail socio@exemplo.com"));
		create("Fraco", "fraco@exemplo.com", "12345").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("A senha provisória precisa ter pelo menos 6 caracteres"));
		create("Sem email", "nao-e-email", "senha-forte-1").andExpect(status().isBadRequest());
	}

	@Test
	void aTemporaryPasswordWorksOnlyToSetANewOne() throws Exception {
		create("Sócio", "socio@exemplo.com", "123456").andExpect(status().isCreated()).andExpect(jsonPath("$.mustChangePassword").value(true));
		String partner = loginAs("socio@exemplo.com", "123456");

		// vê quem é (o painel mostra a tela de troca), mas nada mais funciona até trocar a senha
		mvc.perform(withToken(partner, get("/api/v1/auth/me"))).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
		mvc.perform(withToken(partner, get("/api/v1/platform/plans"))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Defina uma senha nova para continuar usando o painel."));
		mvc.perform(withToken(partner, get("/api/v1/devices"))).andExpect(status().isForbidden());

		// a senha nova segue a regra normal (8+ caracteres)
		mvc.perform(withToken(partner, put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"123456\",\"newPassword\":\"1234567\"}"))).andExpect(status().isBadRequest());
		mvc.perform(withToken(partner, put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"123456\",\"newPassword\":\"minha-senha-forte\"}"))).andExpect(status().isNoContent());

		mvc.perform(withToken(partner, get("/api/v1/auth/me"))).andExpect(jsonPath("$.mustChangePassword").value(false));
		mvc.perform(withToken(partner, get("/api/v1/platform/plans"))).andExpect(status().isOk());
		loginAs("socio@exemplo.com", "minha-senha-forte");
	}

	@Test
	void resettingAnotherManagersPasswordForcesAChangeButChangingYourOwnDoesNot() throws Exception {
		long partnerId = idOf(create("Sócio", "socio@exemplo.com", "senha-forte-1").andReturn().getResponse().getContentAsString());
		String partner = loginAs("socio@exemplo.com", "senha-forte-1");
		mvc.perform(withToken(partner, put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"senha-forte-1\",\"newPassword\":\"outra-senha-forte\"}"))).andExpect(status().isNoContent());
		mvc.perform(withToken(partner, get("/api/v1/platform/plans"))).andExpect(status().isOk());

		// esqueceu a senha: o gestor define uma provisória e a pessoa troca ao entrar
		mvc.perform(asAdmin(put("/api/v1/platform/managers/" + partnerId).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"654321\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
		mvc.perform(withToken(partner, get("/api/v1/platform/plans"))).andExpect(status().isForbidden());

		// a própria senha segue a regra normal e não vira provisória
		long self = jdbc.queryForObject("SELECT id FROM users WHERE email = 'admin@digitalsignage.local'", Long.class);
		mvc.perform(asAdmin(put("/api/v1/platform/managers/" + self).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"123456\"}")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("A senha precisa ter pelo menos 8 caracteres"));
		mvc.perform(asAdmin(get("/api/v1/auth/me"))).andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void deactivatingAManagerTakesAwayTheAccessRightAway() throws Exception {
		long partnerId = idOf(create("Sócio", "socio@exemplo.com", "senha-forte-1").andReturn().getResponse().getContentAsString());
		String partner = loginAs("socio@exemplo.com", "senha-forte-1");

		mvc.perform(asAdmin(put("/api/v1/platform/managers/" + partnerId).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
		mvc.perform(withToken(partner, get("/api/v1/platform/plans"))).andExpect(status().isUnauthorized());

		mvc.perform(asAdmin(put("/api/v1/platform/managers/" + partnerId).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true,\"password\":\"outra-senha-2\"}")))
				.andExpect(status().isOk());
		loginAs("socio@exemplo.com", "outra-senha-2");
	}

	@Test
	void aManagerCannotDeactivateOrDeleteThemselves() throws Exception {
		long self = jdbc.queryForObject("SELECT id FROM users WHERE email = 'admin@digitalsignage.local'", Long.class);
		mvc.perform(asAdmin(put("/api/v1/platform/managers/" + self).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Você não pode desativar o seu próprio usuário"));
		mvc.perform(asAdmin(delete("/api/v1/platform/managers/" + self))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Você não pode excluir o seu próprio usuário"));
	}

	@Test
	void deletesAManagerAndRefusesToTouchOrdinaryUsers() throws Exception {
		long partnerId = idOf(create("Sócio", "socio@exemplo.com", "senha-forte-1").andReturn().getResponse().getContentAsString());
		createUserRow(DEFAULT_TENANT, "editor@cliente.com", "EDITOR", "senha-forte-1");
		long editor = jdbc.queryForObject("SELECT id FROM users WHERE email = 'editor@cliente.com'", Long.class);

		mvc.perform(asAdmin(put("/api/v1/platform/managers/" + editor).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))).andExpect(status().isNotFound());
		mvc.perform(asAdmin(delete("/api/v1/platform/managers/" + editor))).andExpect(status().isNotFound());

		mvc.perform(asAdmin(delete("/api/v1/platform/managers/" + partnerId))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/platform/managers"))).andExpect(jsonPath("$", hasSize(1)));
	}

}
