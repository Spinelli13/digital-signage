package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Inventário básico: o Player conta o equipamento; o Administrador anota número de série, instalação e comodato. */
class InventoryTest extends AbstractApiTest {

	private record Tv(String token, long id) {
	}

	private Tv activeTv(String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.14.70")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		return new Tv(token, id);
	}

	private void heartbeat(Tv tv, Map<String, Object> extra) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("playerVersion", "0.14.70", "configVersion", 0));
		body.putAll(extra);
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body))).andExpect(status().isOk());
	}

	private void update(Tv tv, String name, Map<String, Object> extra, String token, int expected) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("name", name));
		body.putAll(extra);
		mvc.perform(withToken(token, put("/api/v1/devices/" + tv.id()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().is(expected));
	}

	@Test
	void thePlayerTellsItsEquipmentAndTheOldOnesDoNotEraseIt() throws Exception {
		Tv tv = activeTv("TV A");
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(jsonPath("$.hardwareModel").doesNotExist()).andExpect(jsonPath("$.ramMb").doesNotExist());

		heartbeat(tv, Map.of("model", "TCL 55S546", "androidVersion", "11", "ramMb", 1800));
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(status().isOk())
				.andExpect(jsonPath("$.hardwareModel").value("TCL 55S546"))
				.andExpect(jsonPath("$.androidVersion").value("11"))
				.andExpect(jsonPath("$.ramMb").value(1800));

		heartbeat(tv, Map.of()); // Player antigo: sem os campos, e o que já se sabia fica
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(jsonPath("$.hardwareModel").value("TCL 55S546")).andExpect(jsonPath("$.ramMb").value(1800));

		heartbeat(tv, Map.of("model", "Amazon AFTMM", "androidVersion", "9", "ramMb", 1024)); // trocou de aparelho: passa a valer o novo
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(jsonPath("$.hardwareModel").value("Amazon AFTMM")).andExpect(jsonPath("$.ramMb").value(1024));
	}

	@Test
	void anAbsurdHeartbeatEquipmentIsRefusedNotStored() throws Exception {
		Tv tv = activeTv("TV A");

		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playerVersion", "0.14.70", "configVersion", 0, "ramMb", -5)))).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playerVersion", "0.14.70", "configVersion", 0, "model", "x".repeat(81))))).andExpect(status().isBadRequest());
	}

	@Test
	void theAdministratorNotesSerialInstallDateAndComodatoAndOthersNeitherSeeNorChangeIt() throws Exception {
		Tv tv = activeTv("TV Recepção");
		long tenant = jdbc.queryForObject("SELECT tenant_id FROM devices WHERE id = ?", Long.class, tv.id());
		createUserRow(tenant, "editor-inv@teste.com", "EDITOR", "senha-forte-1");
		createUserRow(tenant, "viewer-inv@teste.com", "VIEWER", "senha-forte-1");
		String editor = loginAs("editor-inv@teste.com", "senha-forte-1");
		String viewer = loginAs("viewer-inv@teste.com", "senha-forte-1");

		update(tv, "TV Recepção", Map.of("serialNumber", "  SN-2026-0042 ", "installedOn", "2026-10-20", "comodatoNote", "Comodato Completo: TV 32 pol e stick, do contrato 0007"), adminToken(), 200);

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(jsonPath("$.serialNumber").value("SN-2026-0042"))
				.andExpect(jsonPath("$.installedOn").value("2026-10-20"))
				.andExpect(jsonPath("$.comodatoNote").value("Comodato Completo: TV 32 pol e stick, do contrato 0007"));
		// editor e visualizador não veem o que é interno
		mvc.perform(withToken(editor, get("/api/v1/devices/" + tv.id()))).andExpect(status().isOk()).andExpect(jsonPath("$.serialNumber").doesNotExist())
				.andExpect(jsonPath("$.comodatoNote").doesNotExist());
		mvc.perform(withToken(viewer, get("/api/v1/devices"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].serialNumber").doesNotExist());
		// o editor não muda o inventário, mas pode salvar a TV sem tocar nele
		update(tv, "TV Recepção", Map.of("serialNumber", "OUTRO"), editor, 403);
		update(tv, "TV Recepção renomeada?", Map.of(), editor, 403); // o nome é do Administrador (regra que já existia)
		// ausente não muda; vazio apaga
		update(tv, "TV Recepção", Map.of("comodatoNote", ""), adminToken(), 200);
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(jsonPath("$.comodatoNote").doesNotExist())
				.andExpect(jsonPath("$.serialNumber").value("SN-2026-0042"));
	}

	@Test
	void theInventoryReportCarriesTheEquipmentForEveryoneAndTheInternalNotesOnlyForTheAdministrator() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		Tv a = activeTv("TV B");
		activeTv("TV A");
		heartbeat(a, Map.of("model", "Amazon AFTMM", "androidVersion", "9", "ramMb", 1024));
		update(a, "TV B", Map.of("serialNumber", "SN-1", "installedOn", "2026-10-20", "comodatoNote", "Comodato; \"Básico\""), adminToken(), 200);
		long tenant = jdbc.queryForObject("SELECT tenant_id FROM devices WHERE id = ?", Long.class, a.id());
		createUserRow(tenant, "viewer-csv@teste.com", "VIEWER", "senha-forte-1");
		String viewer = loginAs("viewer-csv@teste.com", "senha-forte-1");

		mvc.perform(asAdmin(get("/api/v1/reports/inventory"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'TV B')].hardwareModel").value("Amazon AFTMM"))
				.andExpect(jsonPath("$[?(@.name == 'TV B')].ramMb").value(1024))
				.andExpect(jsonPath("$[?(@.name == 'TV B')].serialNumber").value("SN-1"))
				.andExpect(jsonPath("$[?(@.name == 'TV B')].installedOn").value("2026-10-20"));
		// quem não administra vê o equipamento, mas não o número de série nem o comodato
		mvc.perform(withToken(viewer, get("/api/v1/reports/inventory"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'TV B')].hardwareModel").value("Amazon AFTMM"))
				.andExpect(jsonPath("$[?(@.name == 'TV B')].serialNumber").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
				.andExpect(jsonPath("$[?(@.name == 'TV B')].comodatoNote").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));

		String csv = mvc.perform(asAdmin(get("/api/v1/reports/inventory?format=csv"))).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse().getContentAsString();
		assertTrue(csv.contains(";Modelo;Android;Memória (MB);Nº de série;Instalada em;Comodato / observação"), csv);
		assertTrue(csv.contains(";Amazon AFTMM;9;1024;SN-1;2026-10-20;\"Comodato; \"\"Básico\"\"\""), csv);
		String viewerCsv = mvc.perform(withToken(viewer, get("/api/v1/reports/inventory?format=csv"))).andReturn().getResponse().getContentAsString();
		assertFalse(viewerCsv.contains("SN-1"), "o número de série não sai para quem não administra");
		assertFalse(viewerCsv.contains("Comodato;"), "nem a observação de comodato");
		assertTrue(viewerCsv.contains(";Amazon AFTMM;9;1024;"), viewerCsv);
	}
}
