package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validade por item da playlist (datas e dias da semana), sem campanha. O servidor só manda à TV os itens que valem hoje (no fuso
 * de São Paulo, UTC-3) e a versão do conteúdo muda sozinha na virada do dia em que algum item entra ou sai do ar.
 * 03/11/2026 é terça-feira; 12:00 em São Paulo = 15:00Z.
 */
class ItemValidityTest extends AbstractApiTest {

	private static final String TUESDAY = "2026-11-03T15:00:00Z";

	private record Tv(String token, long id) {
	}

	private Tv tv(String name, long playlistId) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.9.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assignPlaylist(id, name, playlistId);
		return new Tv(token, id);
	}

	private JsonNode config(Tv tv) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tv.token()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private long heartbeatVersion(Tv tv) throws Exception {
		return json.readTree(mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"playerVersion\":\"0.9.0\",\"configVersion\":0}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("configVersion").asLong();
	}

	private static List<String> names(JsonNode config) {
		List<String> names = new ArrayList<>();
		config.get("items").forEach(item -> names.add(item.get("name").asText()));
		return names;
	}

	private Map<String, Object> item(long mediaId, String from, String until, List<Integer> days) {
		Map<String, Object> item = new HashMap<>();
		item.put("mediaId", mediaId);
		item.put("durationSeconds", 10);
		item.put("validFrom", from);
		item.put("validUntil", until);
		item.put("days", days);
		return item;
	}

	private ResultActions savePlaylist(String name, List<Map<String, Object>> items) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "items", items)))));
	}

	@Test
	void anItemWithoutRulesBehavesExactlyAsBefore() throws Exception {
		clock.freeze(TUESDAY);
		long a = uploadPng("a");
		long b = uploadPng("b");
		long playlist = createPlaylist("Sempre", List.of(a, b));
		Tv tv = tv("TV", playlist);

		JsonNode config = config(tv);

		assertEquals(List.of("a", "b"), names(config));
		// sem validade em nenhum item, a versão é a da playlist (nenhuma TV existente baixa de novo por causa disto)
		JsonNode stored = json.readTree(mvc.perform(asAdmin(get("/api/v1/playlists/" + playlist))).andReturn().getResponse().getContentAsString());
		assertEquals(stored.get("configVersion").asLong(), config.get("configVersion").asLong());
		assertEquals(config.get("configVersion").asLong(), heartbeatVersion(tv));
		assertTrue(stored.get("items").get(0).get("validToday").asBoolean());
		assertTrue(stored.get("items").get(0).get("days").isNull());
	}

	@Test
	void anItemOnlyEntersOnItsFirstDayAndLeavesAfterTheLastOneAndTheVersionFollows() throws Exception {
		clock.freeze(TUESDAY);
		long always = uploadPng("fixo");
		long promo = uploadPng("promo");
		long playlist = idOf(savePlaylist("Promo", List.of(item(always, null, null, null), item(promo, "2026-11-05", "2026-11-06", null)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		Tv tv = tv("TV", playlist);

		// terça: a promoção só começa na quinta
		assertEquals(List.of("fixo"), names(config(tv)));
		long before = heartbeatVersion(tv);
		assertEquals(config(tv).get("configVersion").asLong(), before);

		// quinta (primeiro dia, inclusivo): entra, e a versão mudou para a TV baixar de novo
		clock.freeze("2026-11-05T15:00:00Z");
		assertEquals(List.of("fixo", "promo"), names(config(tv)));
		long during = heartbeatVersion(tv);
		assertNotEquals(before, during);
		assertEquals(config(tv).get("configVersion").asLong(), during);

		// sexta (último dia, inclusivo): continua, com a mesma versão
		clock.freeze("2026-11-06T15:00:00Z");
		assertEquals(List.of("fixo", "promo"), names(config(tv)));
		assertEquals(during, heartbeatVersion(tv));

		// sábado: saiu; a versão muda de novo
		clock.freeze("2026-11-07T15:00:00Z");
		assertEquals(List.of("fixo"), names(config(tv)));
		assertNotEquals(during, heartbeatVersion(tv));
	}

	@Test
	void aWeekdayItemShowsOnlyOnItsDays() throws Exception {
		clock.freeze(TUESDAY);
		long always = uploadPng("fixo");
		long tuesdays = uploadPng("terca");
		long weekend = uploadPng("fim-de-semana");
		long playlist = idOf(savePlaylist("Dias", List.of(item(always, null, null, null), item(tuesdays, null, null, List.of(2)),
				item(weekend, null, null, List.of(7, 6)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		Tv tv = tv("TV", playlist);

		assertEquals(List.of("fixo", "terca"), names(config(tv))); // terça
		long tuesdayVersion = heartbeatVersion(tv);

		clock.freeze("2026-11-04T15:00:00Z"); // quarta
		assertEquals(List.of("fixo"), names(config(tv)));
		assertNotEquals(tuesdayVersion, heartbeatVersion(tv));

		clock.freeze("2026-11-07T15:00:00Z"); // sábado
		assertEquals(List.of("fixo", "fim-de-semana"), names(config(tv)));
		clock.freeze("2026-11-08T15:00:00Z"); // domingo
		assertEquals(List.of("fixo", "fim-de-semana"), names(config(tv)));

		clock.freeze("2026-11-10T15:00:00Z"); // outra terça: a mesma versão da primeira (a marca só depende do que vale)
		assertEquals(List.of("fixo", "terca"), names(config(tv)));
		assertEquals(tuesdayVersion, heartbeatVersion(tv));
	}

	@Test
	void theDayTurnsAtMidnightInSaoPauloNotInUtc() throws Exception {
		long always = uploadPng("fixo");
		long tuesdays = uploadPng("terca");
		long playlist = idOf(savePlaylist("Virada", List.of(item(always, null, null, null), item(tuesdays, null, null, List.of(2))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		clock.freeze(TUESDAY);
		Tv tv = tv("TV", playlist);

		clock.freeze("2026-11-04T02:59:59Z"); // 23:59:59 de terça em São Paulo (em UTC já é quarta)
		assertEquals(List.of("fixo", "terca"), names(config(tv)));
		clock.freeze("2026-11-04T03:00:00Z"); // 00:00 de quarta em São Paulo
		assertEquals(List.of("fixo"), names(config(tv)));
		clock.freeze("2026-11-03T02:59:59Z"); // 23:59:59 de segunda em São Paulo (já é terça em UTC, mas lá ainda não)
		assertEquals(List.of("fixo"), names(config(tv)));
	}

	@Test
	void whenNothingIsValidTodayTheTvGetsAnEmptyConfigAndTheItemsComeBackLater() throws Exception {
		clock.freeze(TUESDAY);
		long only = uploadPng("so-sexta");
		long playlist = idOf(savePlaylist("So sexta", List.of(item(only, null, null, List.of(5)))).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString());
		Tv tv = tv("TV", playlist);

		assertTrue(names(config(tv)).isEmpty());
		clock.freeze("2026-11-06T15:00:00Z"); // sexta
		assertEquals(List.of("so-sexta"), names(config(tv)));
	}

	@Test
	void thePlaylistApiReturnsTheRulesAndWhetherEachItemValidToday() throws Exception {
		clock.freeze(TUESDAY);
		long a = uploadPng("a");
		long b = uploadPng("b");
		long playlist = idOf(savePlaylist("API", List.of(item(a, "2026-11-01", "2026-11-30", List.of(4, 2, 2)), item(b, "2026-12-01", null, null)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		mvc.perform(asAdmin(get("/api/v1/playlists/" + playlist))).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].validFrom").value("2026-11-01")).andExpect(jsonPath("$.items[0].validUntil").value("2026-11-30"))
				.andExpect(jsonPath("$.items[0].days[0]").value(2)).andExpect(jsonPath("$.items[0].days[1]").value(4))
				.andExpect(jsonPath("$.items[0].days.length()").value(2)).andExpect(jsonPath("$.items[0].validToday").value(true))
				.andExpect(jsonPath("$.items[1].validToday").value(false)).andExpect(jsonPath("$.items[1].days").doesNotExist());
		// a lista de playlists também marca o que vale hoje
		mvc.perform(asAdmin(get("/api/v1/playlists"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].items[1].validToday").value(false));
	}

	@Test
	void sevenDaysOrNoDaysMeanEveryDayAndBadInputIsRefused() throws Exception {
		clock.freeze(TUESDAY);
		long a = uploadPng("a");

		long all = idOf(savePlaylist("Todos", List.of(item(a, null, null, List.of(1, 2, 3, 4, 5, 6, 7)))).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString());
		mvc.perform(asAdmin(get("/api/v1/playlists/" + all))).andExpect(jsonPath("$.items[0].days").doesNotExist());
		savePlaylist("Vazio", List.of(item(a, null, null, List.of()))).andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].days").doesNotExist());

		savePlaylist("Zero", List.of(item(a, null, null, List.of(0)))).andExpect(status().isBadRequest());
		savePlaylist("Oito", List.of(item(a, null, null, List.of(8)))).andExpect(status().isBadRequest());
		savePlaylist("Invertido", List.of(item(a, "2026-12-31", "2026-12-01", null))).andExpect(status().isBadRequest());
		// o mesmo dia no começo e no fim vale um dia só
		savePlaylist("Um dia", List.of(item(a, "2026-11-03", "2026-11-03", null))).andExpect(status().isCreated());
	}

	@Test
	void changingTheRulesOfAnItemChangesTheVersionEvenIfTodayLooksTheSame() throws Exception {
		clock.freeze(TUESDAY);
		long a = uploadPng("a");
		long playlist = idOf(savePlaylist("Edita", List.of(item(a, null, "2026-12-31", null))).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString());
		Tv tv = tv("TV", playlist);
		long first = heartbeatVersion(tv);

		mvc.perform(asAdmin(put("/api/v1/playlists/" + playlist).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Edita", "items", List.of(item(a, null, "2027-06-30", null)))))))
				.andExpect(status().isOk());

		assertNotEquals(first, heartbeatVersion(tv));
		assertEquals(List.of("a"), names(config(tv)));
	}

	@Test
	void theLayoutZonesAndTheOfflinePlanFollowTheSameRule() throws Exception {
		clock.freeze(TUESDAY);
		long always = uploadPng("fixo");
		long tuesdays = uploadPng("terca");
		long base = createPlaylist("Base", List.of(always));
		long campaignList = idOf(savePlaylist("Campanha", List.of(item(always, null, null, null), item(tuesdays, null, null, List.of(2))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		Tv tv = tv("TV", base);
		// uma campanha o dia todo, todos os dias: a TV passa a ter agenda offline com a playlist da campanha
		mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "C", "playlistId", campaignList, "targets", List.of(Map.of("type", "ALL")))))))
				.andExpect(status().isCreated());

		JsonNode onTuesday = config(tv);
		assertEquals(List.of("fixo", "terca"), names(onTuesday));
		JsonNode planItems = onTuesday.get("offlinePlan").get("playlists").get(0).get("items");
		assertEquals(2, planItems.size());
		long planVersionTuesday = onTuesday.get("offlinePlan").get("version").asLong();

		clock.freeze("2026-11-04T15:00:00Z"); // quarta
		JsonNode onWednesday = config(tv);
		assertEquals(List.of("fixo"), names(onWednesday));
		assertEquals(1, onWednesday.get("offlinePlan").get("playlists").get(0).get("items").size());
		// a versão da agenda também mudou: a TV renova a agenda guardada ao perceber
		assertNotEquals(planVersionTuesday, onWednesday.get("offlinePlan").get("version").asLong());
		assertFalse(onWednesday.get("configVersion").asLong() == onTuesday.get("configVersion").asLong());
	}
}
