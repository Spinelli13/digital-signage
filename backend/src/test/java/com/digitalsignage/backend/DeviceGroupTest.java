package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DeviceGroupTest extends AbstractApiTest {

	private long createGroup(String name, List<Long> deviceIds) throws Exception {
		Map<String, Object> body = deviceIds == null
				? Map.of("name", name)
				: Map.of("name", name, "deviceIds", deviceIds);
		return idOf(mvc.perform(asAdmin(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	void createListGetUpdateAndDelete() throws Exception {
		long id = createGroup("Recepções", null);

		mvc.perform(asAdmin(get("/api/v1/groups")))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Recepções"))
				.andExpect(jsonPath("$[0].deviceCount").value(0))
				.andExpect(jsonPath("$[0].deviceIds", empty()));

		mvc.perform(asAdmin(put("/api/v1/groups/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Recepções 2", "description", "Todas as recepções")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Recepções 2"))
				.andExpect(jsonPath("$.description").value("Todas as recepções"));

		mvc.perform(asAdmin(delete("/api/v1/groups/" + id))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/groups/" + id))).andExpect(status().isNotFound());
	}

	@Test
	void nameIsRequiredAndUniqueIgnoringCase() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")))
				.andExpect(status().isBadRequest());

		createGroup("Recepções", null);

		mvc.perform(asAdmin(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"RECEPÇÕES\"}")))
				.andExpect(status().isConflict());
	}

	@Test
	void membersCanBeSetAtCreationAndShowUpOnTheDevices() throws Exception {
		long tv1 = createDevice("TV 1");
		long tv2 = createDevice("TV 2");
		long tv3 = createDevice("TV 3");

		long group = createGroup("Salas", List.of(tv1, tv3));

		mvc.perform(asAdmin(get("/api/v1/groups/" + group)))
				.andExpect(jsonPath("$.deviceCount").value(2))
				.andExpect(jsonPath("$.deviceIds", contains((int) tv1, (int) tv3)));
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv1))).andExpect(jsonPath("$.groupIds", contains((int) group)));
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv2))).andExpect(jsonPath("$.groupIds", empty()));
	}

	@Test
	void settingTheMembersReplacesThemAndRemovedOnesLeave() throws Exception {
		long tv1 = createDevice("TV 1");
		long tv2 = createDevice("TV 2");
		long group = createGroup("Salas", List.of(tv1));

		mvc.perform(asAdmin(put("/api/v1/groups/" + group + "/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceIds", List.of(tv2))))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceIds", contains((int) tv2)));

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv1))).andExpect(jsonPath("$.groupIds", empty()));
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv2))).andExpect(jsonPath("$.groupIds", contains((int) group)));
	}

	@Test
	void updatingNameAndDescriptionWithoutDeviceIdsKeepsTheMembers() throws Exception {
		long tv = createDevice("TV");
		long group = createGroup("Salas", List.of(tv));

		mvc.perform(asAdmin(put("/api/v1/groups/" + group)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Salas novas")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceCount").value(1));
	}

	@Test
	void aDeviceCanBelongToSeveralGroups() throws Exception {
		long tv = createDevice("TV");
		long a = createGroup("A", List.of(tv));
		long b = createGroup("B", List.of(tv));

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv)))
				.andExpect(jsonPath("$.groupIds", contains((int) a, (int) b)));
	}

	@Test
	void unknownDevicesAreRejectedAndNothingIsChanged() throws Exception {
		long tv = createDevice("TV");
		long group = createGroup("Salas", List.of(tv));

		mvc.perform(asAdmin(put("/api/v1/groups/" + group + "/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceIds", List.of(tv, 999999))))))
				.andExpect(status().isNotFound());

		mvc.perform(asAdmin(get("/api/v1/groups/" + group))).andExpect(jsonPath("$.deviceCount").value(1));
	}

	/** Regressão de projeto: apagar um grupo nunca apaga as TVs, só o vínculo. */
	@Test
	void deletingAGroupKeepsItsDevices() throws Exception {
		long tv = createDevice("TV");
		long group = createGroup("Salas", List.of(tv));

		mvc.perform(asAdmin(delete("/api/v1/groups/" + group))).andExpect(status().isNoContent());

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.groupIds", empty()));
	}

	@Test
	void deletingADeviceRemovesItFromTheGroupCount() throws Exception {
		long tv1 = createDevice("TV 1");
		long tv2 = createDevice("TV 2");
		long group = createGroup("Salas", List.of(tv1, tv2));

		mvc.perform(asAdmin(delete("/api/v1/devices/" + tv1))).andExpect(status().isNoContent());

		mvc.perform(asAdmin(get("/api/v1/groups/" + group)))
				.andExpect(jsonPath("$.deviceCount").value(1))
				.andExpect(jsonPath("$.deviceIds", contains((int) tv2)));
	}

	@Test
	void devicesCanJoinGroupsWhenCreatedAndEdited() throws Exception {
		long group = createGroup("Salas", null);

		long created = idOf(mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV", "groupIds", List.of(group))))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.groupIds", contains((int) group)))
				.andReturn().getResponse().getContentAsString());

		// groupIds vazio tira de todos os grupos; ausente não mexe
		mvc.perform(asAdmin(put("/api/v1/devices/" + created)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV renomeada")))))
				.andExpect(jsonPath("$.groupIds", contains((int) group)));
		mvc.perform(asAdmin(put("/api/v1/devices/" + created)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV renomeada", "groupIds", List.of())))))
				.andExpect(jsonPath("$.groupIds", empty()));

		mvc.perform(asAdmin(post("/api/v1/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV 2", "groupIds", List.of(424242))))))
				.andExpect(status().isNotFound());
	}

}
