package com.digitalsignage.backend;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthTest extends AbstractApiTest {

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
	}

	@Test
	void protectedEndpointsRejectRequestsWithoutToken() throws Exception {
		mvc.perform(get("/api/v1/devices")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/media")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/playlists")).andExpect(status().isUnauthorized());
	}

	@Test
	void protectedEndpointsRejectGarbageToken() throws Exception {
		mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginReturnsTokenThatOpensProtectedEndpoints() throws Exception {
		mvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", "admin@digitalsignage.local", "password", "changeme"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", not(emptyString())));

		mvc.perform(asAdmin(get("/api/v1/devices"))).andExpect(status().isOk());
	}

	@Test
	void loginWithWrongPasswordIsUnauthorized() throws Exception {
		mvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", "admin@digitalsignage.local", "password", "wrong"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithUnknownEmailIsUnauthorized() throws Exception {
		mvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", "nobody@example.com", "password", "changeme"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithInvalidBodyIsBadRequest() throws Exception {
		mvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());
	}

}
