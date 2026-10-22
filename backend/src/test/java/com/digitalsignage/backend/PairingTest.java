package com.digitalsignage.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

/** Pareamento por QR Code: a TV mostra o endereço do painel com o código; o painel confere o código e ativa. */
class PairingTest extends AbstractApiTest {

	private record Pending(String token, String code) {
	}

	private String newToken() {
		return UUID.randomUUID().toString().replace("-", "") + "00";
	}

	private JsonNode register(String token) throws Exception {
		return json.readTree(mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.14.70"))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private Pending pending() throws Exception {
		String token = newToken();
		return new Pending(token, register(token).get("activationCode").asText());
	}

	@Test
	void aTvWaitingToBePairedGetsTheAddressTheQrCodeLeadsTo() throws Exception {
		String token = newToken();
		JsonNode first = register(token);
		String code = first.get("activationCode").asText();

		// o QR Code leva ao painel (a origem autorizada do painel nos testes) com o código na rota
		org.junit.jupiter.api.Assertions.assertEquals("http://localhost:4200/parear/" + code, first.get("pairingUrl").asText());
		// a TV que pergunta de novo recebe o mesmo endereço (o código não muda enquanto espera)
		org.junit.jupiter.api.Assertions.assertEquals(first.get("pairingUrl").asText(), register(token).get("pairingUrl").asText());
		mvc.perform(get("/api/v1/player/status").header("X-Device-Token", token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pairingUrl").value("http://localhost:4200/parear/" + code));
	}

	@Test
	void afterTheActivationThereIsNoAddressAndNoCodeLeft() throws Exception {
		Pending tv = pending();
		mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", tv.code(), "name", "TV Recepção")))))
				.andExpect(status().isOk());

		mvc.perform(get("/api/v1/player/status").header("X-Device-Token", tv.token()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.pairingUrl").doesNotExist());
	}

	@Test
	void thePanelConfirmsTheCodeBeforeAskingForTheNameAndRefusesWhatIsNotWaiting() throws Exception {
		Pending tv = pending();

		mvc.perform(asAdmin(get("/api/v1/devices/pairing/" + tv.code().toLowerCase()))) // o código vale em minúsculas também
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(tv.code()))
				.andExpect(jsonPath("$.playerVersion").value("0.14.70"))
				.andExpect(jsonPath("$.waitingSince").exists());
		mvc.perform(asAdmin(get("/api/v1/devices/pairing/ZZZZZZ"))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Código de ativação inválido ou expirado"));

		mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", tv.code(), "name", "TV A"))))).andExpect(status().isOk());
		mvc.perform(asAdmin(get("/api/v1/devices/pairing/" + tv.code()))).andExpect(status().isNotFound()); // já pareada
	}
}
