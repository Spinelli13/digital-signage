package com.digitalsignage.backend;

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

class LocationTest extends AbstractApiTest {

	private long createLocation(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/locations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "description", "Descrição de " + name)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private long createDeviceAt(String name, Long locationId) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "locationId", locationId)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	void createListGetUpdateAndDelete() throws Exception {
		long id = createLocation("Loja Centro");

		mvc.perform(asAdmin(get("/api/v1/locations")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Loja Centro"))
				.andExpect(jsonPath("$[0].deviceCount").value(0));

		mvc.perform(asAdmin(put("/api/v1/locations/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Loja Centro 2", "description", "")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Loja Centro 2"))
				.andExpect(jsonPath("$.description").doesNotExist());

		mvc.perform(asAdmin(get("/api/v1/locations/" + id))).andExpect(jsonPath("$.name").value("Loja Centro 2"));
		mvc.perform(asAdmin(delete("/api/v1/locations/" + id))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/locations/" + id))).andExpect(status().isNotFound());
	}

	@Test
	void nameIsRequiredAndMustBeUniqueIgnoringCase() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/locations")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}")))
				.andExpect(status().isBadRequest());

		createLocation("Loja Centro");

		mvc.perform(asAdmin(post("/api/v1/locations")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  loja centro \"}")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Já existe um local chamado \"loja centro\""));
	}

	@Test
	void renamingToTheNameOfAnotherLocationIsRefusedButKeepingYourOwnIsFine() throws Exception {
		long first = createLocation("Matriz");
		createLocation("Filial");

		mvc.perform(asAdmin(put("/api/v1/locations/" + first)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"filial\"}")))
				.andExpect(status().isConflict());
		mvc.perform(asAdmin(put("/api/v1/locations/" + first)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"MATRIZ\"}")))
				.andExpect(status().isOk());
	}

	@Test
	void listCountsTheDevicesOfEachLocation() throws Exception {
		long centro = createLocation("Centro");
		createLocation("Vazio");
		createDeviceAt("TV 1", centro);
		createDeviceAt("TV 2", centro);

		mvc.perform(asAdmin(get("/api/v1/locations")))
				.andExpect(jsonPath("$[0].name").value("Centro"))
				.andExpect(jsonPath("$[0].deviceCount").value(2))
				.andExpect(jsonPath("$[1].name").value("Vazio"))
				.andExpect(jsonPath("$[1].deviceCount").value(0));
	}

	/** Regressão de projeto: apagar o local nunca pode apagar nem derrubar as TVs. */
	@Test
	void deletingALocationKeepsItsDevicesWithoutLocation() throws Exception {
		long centro = createLocation("Centro");
		long device = createDeviceAt("TV", centro);

		mvc.perform(asAdmin(delete("/api/v1/locations/" + centro))).andExpect(status().isNoContent());

		mvc.perform(asAdmin(get("/api/v1/devices/" + device)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("TV"))
				.andExpect(jsonPath("$.locationId").doesNotExist())
				.andExpect(jsonPath("$.location").doesNotExist());
	}

	@Test
	void unknownLocationIs404AndAnonymousIs401() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/locations/999"))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/locations")).andExpect(status().isUnauthorized());
	}

}
