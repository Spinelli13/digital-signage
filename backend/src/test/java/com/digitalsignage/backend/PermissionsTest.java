package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Quem pode o quê: Visualizador, Editor, Administrador do cliente e operador da plataforma. */
class PermissionsTest extends AbstractApiTest {

	private record Team(long tenantId, String viewer, String editor, String admin) {
	}

	private Team team() throws Exception {
		long tenant = createTenantRow("Cliente Permissões", "interno", "ACTIVE");
		for (String role : List.of("viewer", "editor", "admin")) {
			createUserRow(tenant, role + "-" + tenant + "@teste.com", role.toUpperCase(), "senha-forte-1");
		}
		return new Team(tenant,
				loginAs("viewer-" + tenant + "@teste.com", "senha-forte-1"),
				loginAs("editor-" + tenant + "@teste.com", "senha-forte-1"),
				loginAs("admin-" + tenant + "@teste.com", "senha-forte-1"));
	}

	private MockHttpServletRequestBuilder request(HttpMethod method, String path, String body) {
		MockHttpServletRequestBuilder builder = switch (method.name()) {
			case "POST" -> post("/api/v1" + path);
			case "PUT" -> put("/api/v1" + path);
			case "DELETE" -> delete("/api/v1" + path);
			default -> get("/api/v1" + path);
		};
		return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	/** A requisição foi recusada por PERMISSÃO (403)? Qualquer outro resultado (200, 201, 400, 404...) significa "o papel pode tentar". */
	private boolean refused(String token, HttpMethod method, String path, String body) throws Exception {
		int code = mvc.perform(withToken(token, request(method, path, body))).andReturn().getResponse().getStatus();
		assertNotEquals(401, code, method + " " + path + ": o token deveria valer");
		return code == 403;
	}

	private record Case(HttpMethod method, String path, String body, boolean viewer, boolean editor) {
	}

	@Test
	void theViewerOnlyReadsTheEditorHandlesContentAndTheAdminHandlesEverythingInTheClient() throws Exception {
		Team team = team();
		List<Case> cases = List.of(
				// consultar: todos os papéis
				new Case(HttpMethod.GET, "/devices", null, true, true),
				new Case(HttpMethod.GET, "/media", null, true, true),
				new Case(HttpMethod.GET, "/playlists", null, true, true),
				new Case(HttpMethod.GET, "/campaigns", null, true, true),
				new Case(HttpMethod.GET, "/layouts", null, true, true),
				new Case(HttpMethod.GET, "/locations", null, true, true),
				new Case(HttpMethod.GET, "/groups", null, true, true),
				new Case(HttpMethod.GET, "/alerts", null, true, true),
				new Case(HttpMethod.GET, "/reports/uptime", null, true, true),
				new Case(HttpMethod.GET, "/summary", null, true, true),
				new Case(HttpMethod.GET, "/auth/me", null, true, true),
				// conteúdo: do Editor para cima
				new Case(HttpMethod.POST, "/playlists", "{\"name\":\"p\"}", false, true),
				new Case(HttpMethod.POST, "/media/web", "{\"url\":\"https://site.com\",\"durationSeconds\":30}", false, true),
				new Case(HttpMethod.POST, "/layouts/from-template", "{\"templateId\":\"tela-cheia\"}", false, true),
				new Case(HttpMethod.POST, "/campaigns", "{}", false, true),
				new Case(HttpMethod.DELETE, "/playlists/999", null, false, true),
				new Case(HttpMethod.DELETE, "/media/999", null, false, true),
				// administração do cliente: só o Administrador
				new Case(HttpMethod.POST, "/locations", "{\"name\":\"L\"}", false, false),
				new Case(HttpMethod.POST, "/groups", "{\"name\":\"G\"}", false, false),
				new Case(HttpMethod.PUT, "/alerts/settings", "{}", false, false),
				new Case(HttpMethod.POST, "/alerts/settings/test", null, false, false),
				new Case(HttpMethod.POST, "/devices/activate", "{\"activationCode\":\"ABCDEF\",\"name\":\"x\"}", false, false),
				new Case(HttpMethod.DELETE, "/devices/999", null, false, false),
				new Case(HttpMethod.POST, "/devices/update-check", null, false, false),
				new Case(HttpMethod.GET, "/users", null, false, false),
				new Case(HttpMethod.POST, "/users", "{}", false, false));

		for (Case c : cases) {
			assertEquals(!c.viewer(), refused(team.viewer(), c.method(), c.path(), c.body()), "Visualizador: " + c.method() + " " + c.path());
			assertEquals(!c.editor(), refused(team.editor(), c.method(), c.path(), c.body()), "Editor: " + c.method() + " " + c.path());
			assertEquals(false, refused(team.admin(), c.method(), c.path(), c.body()), "Administrador: " + c.method() + " " + c.path());
		}
	}

	@Test
	void nobodyButThePlatformOperatorReachesThePlatformArea() throws Exception {
		Team team = team();
		for (String token : List.of(team.viewer(), team.editor(), team.admin())) {
			mvc.perform(withToken(token, get("/api/v1/platform/tenants"))).andExpect(status().isForbidden());
		}
	}

	@Test
	void everyoneChangesTheirOwnPasswordWithTheCurrentOne() throws Exception {
		Team team = team();
		String email = "viewer-" + team.tenantId() + "@teste.com";

		mvc.perform(withToken(team.viewer(), put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"errada\",\"newPassword\":\"outra-senha-1\"}")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("A senha atual não confere"));
		mvc.perform(withToken(team.viewer(), put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"senha-forte-1\",\"newPassword\":\"curta\"}")))
				.andExpect(status().isBadRequest());
		mvc.perform(withToken(team.viewer(), put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"senha-forte-1\",\"newPassword\":\"outra-senha-1\"}")))
				.andExpect(status().isNoContent());

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", email, "password", "senha-forte-1")))).andExpect(status().isUnauthorized());
		loginAs(email, "outra-senha-1");
	}

	// ---------- o Editor e as TVs ----------

	private String activeTv(Team team, String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.6.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		mvc.perform(withToken(team.admin(), post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name))))).andExpect(status().isOk());
		return token;
	}

	@Test
	void anEditorChoosesWhatATvShowsButCannotRenameMoveOrDisableIt() throws Exception {
		Team team = team();
		activeTv(team, "TV 1");
		long tv = jdbc.queryForObject("SELECT id FROM devices WHERE name = 'TV 1'", Long.class);
		long media = idOf(mvc.perform(withToken(team.editor(), org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/media")
				.file(new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", "png".getBytes())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		long playlist = idOf(mvc.perform(withToken(team.editor(), post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Base", "items", List.of(Map.of("mediaId", media))))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		// escolher a playlist e o layout: pode
		mvc.perform(withToken(team.editor(), put("/api/v1/devices/" + tv).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV 1", "currentPlaylistId", playlist))))).andExpect(status().isOk());
		mvc.perform(withToken(team.editor(), org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/devices/" + tv + "/playlist"))).andExpect(status().isOk());
		mvc.perform(withToken(team.editor(), post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "ASSIGN_PLAYLIST", "deviceIds", List.of(tv), "playlistId", playlist))))).andExpect(status().isOk());

		// mudar o resto: não
		for (Map<String, Object> change : List.of(
				Map.<String, Object>of("name", "Outro nome"),
				Map.<String, Object>of("name", "TV 1", "status", "disabled"),
				Map.<String, Object>of("name", "TV 1", "location", "Loja nova"),
				Map.<String, Object>of("name", "TV 1", "groupIds", List.of()))) {
			mvc.perform(withToken(team.editor(), put("/api/v1/devices/" + tv).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(change))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("O papel Editor só pode escolher a playlist e o layout das TVs. Nome, local, grupos e situação são do Administrador."));
		}
		mvc.perform(withToken(team.editor(), post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "DISABLE", "deviceIds", List.of(tv))))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("O papel Editor só pode aplicar playlist e layout a várias TVs. As outras ações são do Administrador."));
		assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM devices WHERE id = ?", String.class, tv));
		assertEquals("TV 1", jdbc.queryForObject("SELECT name FROM devices WHERE id = ?", String.class, tv));
	}

}
