package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Modo offline inteligente: a TV leva a agenda dos próximos 7 dias, já resolvida pelo servidor (campanha vencedora em cada
 * intervalo), e a versão dessa agenda vai no heartbeat. 05/11/2026 é quinta-feira; 12:00 em São Paulo (UTC-3) = 15:00Z.
 */
class SmartOfflineTest extends AbstractApiTest {

	private static final String NOON = "2026-11-05T15:00:00Z";

	// ---------- montagem ----------

	private long playlist(String name) throws Exception {
		return createPlaylist(name, List.of(uploadPng("img-" + name)));
	}

	private long campaign(String name, long playlistId, Map<String, Object> extra) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("playlistId", playlistId);
		body.put("targets", List.of(Map.of("type", "ALL")));
		body.putAll(extra);
		return idOf(mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private record Tv(String token, long id) {
	}

	private Tv tv(String name, Long basePlaylist) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.9.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		if (basePlaylist != null) {
			assignPlaylist(id, name, basePlaylist);
		}
		return new Tv(token, id);
	}

	private JsonNode config(Tv tv) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/player/config").header("X-Device-Token", tv.token()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode heartbeat(Tv tv) throws Exception {
		return json.readTree(mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"playerVersion\":\"0.9.0\",\"configVersion\":0}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private static long ms(String iso) {
		return Instant.parse(iso).toEpochMilli();
	}

	private static boolean none(JsonNode node) {
		return node == null || node.isNull();
	}

	private static JsonNode segment(JsonNode plan, int index) {
		return plan.get("segments").get(index);
	}

	// ---------- quando há agenda ----------

	@Test
	void aTvWithoutCampaignsHasNoPlanAndPlanVersionZero() throws Exception {
		clock.freeze(NOON);
		Tv tv = tv("TV 1", playlist("Base"));

		assertTrue(none(config(tv).get("offlinePlan")));
		assertEquals(0, heartbeat(tv).get("planVersion").asLong());
	}

	@Test
	void aDailyWindowBecomesBaseThenCampaignThenBaseAgain() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze(NOON);
		long happy = campaign("Happy hour", promo, Map.of("startTime", "18:00", "endTime", "23:00"));
		Tv tv = tv("TV 1", base);

		JsonNode plan = config(tv).get("offlinePlan");

		assertEquals(ms(NOON), plan.get("generatedAtMs").asLong());
		assertEquals(Instant.parse(NOON).plus(Duration.ofDays(7)).toEpochMilli(), plan.get("untilMs").asLong());
		// 12:00 SP: a playlist da TV até as 18:00 (21:00Z)
		assertEquals(ms(NOON), segment(plan, 0).get("fromMs").asLong());
		assertEquals(ms("2026-11-05T21:00:00Z"), segment(plan, 0).get("toMs").asLong());
		assertTrue(segment(plan, 0).get("campaignId").isNull());
		assertEquals(base, segment(plan, 0).get("playlistId").asLong());
		// 18:00 às 23:00 SP: a campanha
		assertEquals(ms("2026-11-05T21:00:00Z"), segment(plan, 1).get("fromMs").asLong());
		assertEquals(ms("2026-11-06T02:00:00Z"), segment(plan, 1).get("toMs").asLong());
		assertEquals(happy, segment(plan, 1).get("campaignId").asLong());
		assertEquals("Happy hour", segment(plan, 1).get("campaignName").asText());
		assertEquals(promo, segment(plan, 1).get("playlistId").asLong());
		// depois das 23:00 SP, a playlist da TV de novo, até as 18:00 do dia seguinte
		assertEquals(ms("2026-11-06T02:00:00Z"), segment(plan, 2).get("fromMs").asLong());
		assertEquals(ms("2026-11-06T21:00:00Z"), segment(plan, 2).get("toMs").asLong());
		assertTrue(segment(plan, 2).get("campaignId").isNull());
		// o conteúdo das duas playlists vai junto, uma vez cada, com as mídias
		assertEquals(2, plan.get("playlists").size());
		assertEquals("IMAGE", plan.get("playlists").get(0).get("items").get(0).get("type").asText());
		// os intervalos se emendam sem buraco e cobrem os 7 dias
		for (int i = 1; i < plan.get("segments").size(); i++) {
			assertEquals(segment(plan, i - 1).get("toMs").asLong(), segment(plan, i).get("fromMs").asLong());
		}
		assertEquals(plan.get("untilMs").asLong(), segment(plan, plan.get("segments").size() - 1).get("toMs").asLong());
	}

	@Test
	void theFirstIntervalAlwaysMatchesWhatTheHeartbeatSaysNow() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze("2026-11-05T22:00:00Z"); // 19:00 SP, dentro da janela
		long happy = campaign("Happy hour", promo, Map.of("startTime", "18:00", "endTime", "23:00"));
		Tv tv = tv("TV 1", base);

		JsonNode heartbeat = heartbeat(tv);
		JsonNode first = segment(config(tv).get("offlinePlan"), 0);

		assertEquals(happy, heartbeat.get("campaignId").asLong());
		assertEquals(heartbeat.get("campaignId").asLong(), first.get("campaignId").asLong());
		assertEquals(heartbeat.get("playlistId").asLong(), first.get("playlistId").asLong());
		assertEquals(heartbeat.get("configVersion").asLong(), first.get("configVersion").asLong());
		assertEquals(ms("2026-11-06T02:00:00Z"), first.get("toMs").asLong());
	}

	@Test
	void aWindowThatCrossesMidnightIsOneInterval() throws Exception {
		long base = playlist("Base");
		long night = playlist("Noite");
		clock.freeze(NOON);
		campaign("Madrugada", night, Map.of("startTime", "22:00", "endTime", "06:00"));
		Tv tv = tv("TV 1", base);

		JsonNode plan = config(tv).get("offlinePlan");

		// 06:00 SP (09:00Z) do dia 5 já passou; a janela do dia 5 começa às 22:00 SP (01:00Z do dia 6) e vai até 06:00 SP do dia 6 (09:00Z)
		assertTrue(segment(plan, 0).get("campaignId").isNull());
		assertEquals(ms("2026-11-06T01:00:00Z"), segment(plan, 0).get("toMs").asLong());
		assertEquals(ms("2026-11-06T01:00:00Z"), segment(plan, 1).get("fromMs").asLong());
		assertEquals(ms("2026-11-06T09:00:00Z"), segment(plan, 1).get("toMs").asLong());
		assertEquals(night, segment(plan, 1).get("playlistId").asLong());
	}

	@Test
	void theHigherPriorityWinsInsideTheOverlapAndTheLowerOneHoldsTheRest() throws Exception {
		long base = playlist("Base");
		long low = playlist("Baixa");
		long high = playlist("Alta");
		clock.freeze("2026-11-05T09:00:00Z"); // 06:00 SP
		long lowId = campaign("Manhã", low, Map.of("startTime", "08:00", "endTime", "12:00", "priority", 10));
		long highId = campaign("Anúncio", high, Map.of("startTime", "10:00", "endTime", "11:00", "priority", 90));
		Tv tv = tv("TV 1", base);

		JsonNode plan = config(tv).get("offlinePlan");

		assertTrue(segment(plan, 0).get("campaignId").isNull());                             // 06:00-08:00 base
		assertEquals(lowId, segment(plan, 1).get("campaignId").asLong());                    // 08:00-10:00 baixa
		assertEquals(highId, segment(plan, 2).get("campaignId").asLong());                   // 10:00-11:00 alta vence
		assertEquals(ms("2026-11-05T13:00:00Z"), segment(plan, 2).get("fromMs").asLong());
		assertEquals(ms("2026-11-05T14:00:00Z"), segment(plan, 2).get("toMs").asLong());
		assertEquals(lowId, segment(plan, 3).get("campaignId").asLong());                    // 11:00-12:00 baixa de novo
		assertTrue(segment(plan, 4).get("campaignId").isNull());                             // depois, base
	}

	@Test
	void aCampaignThatEndedOrStartsAfterTheHorizonIsNotInThePlan() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze(NOON);
		campaign("Acabou", promo, Map.of("startDate", "2026-10-01", "endDate", "2026-10-31"));
		campaign("Só em dezembro", promo, Map.of("startDate", "2026-12-01"));
		Tv tv = tv("TV 1", base);

		assertTrue(none(config(tv).get("offlinePlan")));
		assertEquals(0, heartbeat(tv).get("planVersion").asLong());
	}

	@Test
	void aCampaignThatStartsLaterInTheWeekAppearsAtTheRightMoment() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze(NOON);
		long id = campaign("Fim de semana", promo, Map.of("startDate", "2026-11-07", "endDate", "2026-11-08"));
		Tv tv = tv("TV 1", base);

		JsonNode plan = config(tv).get("offlinePlan");

		assertTrue(segment(plan, 0).get("campaignId").isNull());
		assertEquals(ms("2026-11-07T03:00:00Z"), segment(plan, 0).get("toMs").asLong()); // sábado 00:00 SP
		assertEquals(id, segment(plan, 1).get("campaignId").asLong());
		assertEquals(ms("2026-11-09T03:00:00Z"), segment(plan, 1).get("toMs").asLong()); // segunda 00:00 SP
		assertTrue(segment(plan, 2).get("campaignId").isNull());
	}

	@Test
	void onlyCampaignsThatReachTheTvCount() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze(NOON);
		Tv mine = tv("Minha", base);
		Tv other = tv("Outra", base);
		Map<String, Object> body = new HashMap<>(Map.of("name", "Só da outra", "playlistId", promo, "targets", List.of(Map.of("type", "DEVICE", "id", other.id()))));
		mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))))
				.andExpect(status().isCreated());

		assertTrue(none(config(mine).get("offlinePlan")));
		assertEquals(1, config(other).get("offlinePlan").get("playlists").size());
	}

	@Test
	void aPausedCampaignIsNotInThePlan() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze(NOON);
		long id = campaign("Pausada", promo, Map.of("startTime", "18:00", "endTime", "23:00"));
		Tv tv = tv("TV 1", base);
		mvc.perform(asAdmin(put("/api/v1/campaigns/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
				Map.of("name", "Pausada", "playlistId", promo, "targets", List.of(Map.of("type", "ALL")), "enabled", false)))))
				.andExpect(status().isOk());

		assertTrue(none(config(tv).get("offlinePlan")));
	}

	// ---------- versão da agenda ----------

	@Test
	void theVersionIsStableWithTimeButChangesWhenTheScheduleChanges() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		clock.freeze(NOON);
		long id = campaign("Happy hour", promo, Map.of("startTime", "18:00", "endTime", "23:00"));
		Tv tv = tv("TV 1", base);

		long first = heartbeat(tv).get("planVersion").asLong();
		assertNotEquals(0, first);
		assertEquals(first, config(tv).get("offlinePlan").get("version").asLong());

		clock.freeze("2026-11-05T17:00:00Z"); // passaram horas: a agenda é a mesma
		assertEquals(first, heartbeat(tv).get("planVersion").asLong());

		mvc.perform(asAdmin(put("/api/v1/campaigns/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
				Map.of("name", "Happy hour", "playlistId", promo, "targets", List.of(Map.of("type", "ALL")), "startTime", "19:00", "endTime", "23:00")))))
				.andExpect(status().isOk());
		long afterEdit = heartbeat(tv).get("planVersion").asLong();
		assertNotEquals(first, afterEdit);

		replacePlaylistItems(promo, "Promo", List.of(uploadPng("outra"), uploadPng("mais uma")));
		assertNotEquals(afterEdit, heartbeat(tv).get("planVersion").asLong());
	}

	@Test
	void changingTheTvPlaylistOrPriorityChangesTheVersion() throws Exception {
		long base = playlist("Base");
		long other = playlist("Outra base");
		long promo = playlist("Promo");
		clock.freeze(NOON);
		long id = campaign("Promo", promo, Map.of("startTime", "18:00", "endTime", "23:00", "priority", 50));
		Tv tv = tv("TV 1", base);
		long first = heartbeat(tv).get("planVersion").asLong();

		assignPlaylist(tv.id(), "TV 1", other);
		long afterBase = heartbeat(tv).get("planVersion").asLong();
		assertNotEquals(first, afterBase);

		mvc.perform(asAdmin(put("/api/v1/campaigns/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
				Map.of("name", "Promo", "playlistId", promo, "targets", List.of(Map.of("type", "ALL")), "startTime", "18:00", "endTime", "23:00", "priority", 70)))))
				.andExpect(status().isOk());
		assertNotEquals(afterBase, heartbeat(tv).get("planVersion").asLong());
	}

	// ---------- o que o painel vê ----------

	@Test
	void thePanelSeesUntilWhenTheScheduleStoredOnTheTvIsValid() throws Exception {
		clock.freeze(NOON);
		Tv tv = tv("TV 1", playlist("Base"));

		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.offlinePlanUntil").doesNotExist());

		long until = ms("2026-11-12T15:00:00Z");
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.9.0\",\"configVersion\":0,\"planUntilMs\":" + until + "}")).andExpect(status().isOk());
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.offlinePlanUntil").value("2026-11-12T15:00:00Z"));

		// a TV passou a não ter agenda (ou o Player é antigo): some do painel
		heartbeat(tv);
		mvc.perform(asAdmin(get("/api/v1/devices/" + tv.id()))).andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.offlinePlanUntil").doesNotExist());
	}

	// ---------- com layout ----------

	@Test
	void withALayoutTheIntervalVersionIsTheSameTheHeartbeatWouldSend() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		long side = playlist("Lateral");
		Map<String, Object> main = new HashMap<>(Map.of("name", "Principal", "x", 0, "y", 0, "width", 75, "height", 100, "main", true));
		Map<String, Object> lateral = new HashMap<>(Map.of("name", "Lateral", "x", 75, "y", 0, "width", 25, "height", 100, "main", false, "playlistId", side));
		long layout = idOf(mvc.perform(asAdmin(post("/api/v1/layouts").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Principal + lateral", "zones", List.of(main, lateral))))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		clock.freeze(NOON);
		campaign("Happy hour", promo, Map.of("startTime", "18:00", "endTime", "23:00"));
		Tv tv = tv("TV 1", base);
		mvc.perform(asAdmin(put("/api/v1/devices/" + tv.id()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "TV 1", "layoutId", layout))))).andExpect(status().isOk());

		JsonNode plan = config(tv).get("offlinePlan");
		long baseVersionInPlan = segment(plan, 0).get("configVersion").asLong();
		assertEquals(heartbeat(tv).get("configVersion").asLong(), baseVersionInPlan);

		clock.freeze("2026-11-05T22:00:00Z"); // dentro da campanha
		assertEquals(heartbeat(tv).get("configVersion").asLong(), segment(plan, 1).get("configVersion").asLong());
		assertNotEquals(baseVersionInPlan, segment(plan, 1).get("configVersion").asLong());
	}

}
