package com.digitalsignage.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Campanhas: playlist + período/dias/horário + prioridade + alvos. Horário padrão: America/Sao_Paulo (UTC-3).
 * 05/11/2026 é quinta-feira; 12:00 em SP = 15:00Z; 18:30 em SP = 21:30Z.
 */
class CampaignTest extends AbstractApiTest {

	private static final String NOON = "2026-11-05T15:00:00Z";
	private static final String EVENING = "2026-11-05T21:30:00Z";

	// ---------- montagem ----------

	private long playlist(String name) throws Exception {
		return createPlaylist(name, List.of(uploadPng("img-" + name)));
	}

	private Map<String, Object> body(String name, long playlistId, List<Map<String, Object>> targets) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("playlistId", playlistId);
		body.put("targets", targets);
		return body;
	}

	private static Map<String, Object> all() {
		return Map.of("type", "ALL");
	}

	private static Map<String, Object> target(String type, long id) {
		return Map.of("type", type, "id", id);
	}

	private ResultActions submit(Map<String, Object> body) throws Exception {
		return mvc.perform(asAdmin(post("/api/v1/campaigns")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body))));
	}

	private long create(Map<String, Object> body) throws Exception {
		return idOf(submit(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long location(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/locations")
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", name)))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long group(String name) throws Exception {
		return idOf(mvc.perform(asAdmin(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", name)))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Uma TV de verdade: registra pelo Player, ativa pelo painel e devolve o token do aparelho. */
	private String activeTv(String name, Long locationId, Long basePlaylist) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.4.0"))))
				.andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		Map<String, Object> activation = new HashMap<>(Map.of("activationCode", code, "name", name));
		if (locationId != null) {
			activation.put("locationId", locationId);
		}
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate")
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(activation))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		if (basePlaylist != null) {
			assignPlaylist(id, name, basePlaylist);
		}
		return token;
	}

	private MockHttpServletRequestBuilder asDevice(MockHttpServletRequestBuilder request, String token) {
		return request.header("X-Device-Token", token);
	}

	private ResultActions heartbeat(String token) throws Exception {
		return mvc.perform(asDevice(post("/api/v1/player/heartbeat"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.4.0\",\"configVersion\":0}"));
	}

	private long deviceId(String name) {
		return jdbc.queryForObject("SELECT id FROM devices WHERE name = ?", Long.class, name);
	}

	// ---------- CRUD ----------

	@Test
	void createsACampaignWithSensibleDefaultsAndExplainsItself() throws Exception {
		long promo = playlist("Promo");
		long centro = location("Loja Centro");
		clock.freeze(NOON);

		submit(body("Promoção de verão", promo, List.of(target("LOCATION", centro))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Promoção de verão"))
				.andExpect(jsonPath("$.playlistId").value(promo))
				.andExpect(jsonPath("$.playlistName").value("Promo"))
				.andExpect(jsonPath("$.priority").value(50))
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.daysOfWeek", contains(1, 2, 3, 4, 5, 6, 7)))
				.andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
				.andExpect(jsonPath("$.startDate").doesNotExist())
				.andExpect(jsonPath("$.state").value("ACTIVE"))
				.andExpect(jsonPath("$.deviceCount").value(0))
				.andExpect(jsonPath("$.targets[0].type").value("LOCATION"))
				.andExpect(jsonPath("$.targets[0].id").value(centro))
				.andExpect(jsonPath("$.targets[0].name").value("Loja Centro"));
	}

	@Test
	void savesPeriodDaysAndHours() throws Exception {
		long promo = playlist("Promo");
		Map<String, Object> request = body("Happy hour", promo, List.of(all()));
		request.put("startDate", "2026-11-01");
		request.put("endDate", "2026-11-30");
		request.put("startTime", "18:00");
		request.put("endTime", "23:00");
		request.put("daysOfWeek", List.of(5, 6));
		request.put("priority", 80);
		request.put("description", "  Sexta e sábado  ");
		clock.freeze(NOON);

		submit(request)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startDate").value("2026-11-01"))
				.andExpect(jsonPath("$.endDate").value("2026-11-30"))
				.andExpect(jsonPath("$.startTime").value("18:00:00"))
				.andExpect(jsonPath("$.endTime").value("23:00:00"))
				.andExpect(jsonPath("$.daysOfWeek", contains(5, 6)))
				.andExpect(jsonPath("$.priority").value(80))
				.andExpect(jsonPath("$.description").value("Sexta e sábado"))
				.andExpect(jsonPath("$.state").value("WAITING")); // quinta, 12:00: no período, mas fora do dia e do horário
	}

	@Test
	void updatesAndDeletesACampaign() throws Exception {
		long promo = playlist("Promo");
		long other = playlist("Outra");
		long id = create(body("A", promo, List.of(all())));

		Map<String, Object> update = body("B", other, List.of(all()));
		update.put("enabled", false);
		mvc.perform(asAdmin(put("/api/v1/campaigns/" + id)
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("B"))
				.andExpect(jsonPath("$.playlistId").value(other))
				.andExpect(jsonPath("$.state").value("PAUSED"));

		mvc.perform(asAdmin(delete("/api/v1/campaigns/" + id))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/campaigns/" + id))).andExpect(status().isNotFound());
		mvc.perform(asAdmin(put("/api/v1/campaigns/" + id)
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))))
				.andExpect(status().isNotFound());
	}

	@Test
	void theListShowsActiveCampaignsFirstThenByPriority() throws Exception {
		long promo = playlist("Promo");
		clock.freeze(NOON);
		Map<String, Object> paused = body("Pausada", promo, List.of(all()));
		paused.put("enabled", false);
		Map<String, Object> ended = body("Encerrada", promo, List.of(all()));
		ended.put("endDate", "2026-10-01");
		Map<String, Object> future = body("Futura", promo, List.of(all()));
		future.put("startDate", "2026-12-01");
		Map<String, Object> lowActive = body("Ativa baixa", promo, List.of(all()));
		lowActive.put("priority", 10);
		Map<String, Object> highActive = body("Ativa alta", promo, List.of(all()));
		highActive.put("priority", 90);
		for (Map<String, Object> request : List.of(paused, ended, future, lowActive, highActive)) {
			create(request);
		}

		mvc.perform(asAdmin(get("/api/v1/campaigns")))
				.andExpect(jsonPath("$[*].name", contains("Ativa alta", "Ativa baixa", "Futura", "Pausada", "Encerrada")))
				.andExpect(jsonPath("$[*].state", contains("ACTIVE", "ACTIVE", "SCHEDULED", "PAUSED", "ENDED")));
	}

	@Test
	void countsTheTvsACampaignReaches() throws Exception {
		long promo = playlist("Promo");
		long centro = location("Centro");
		activeTv("TV 1", centro, null);
		activeTv("TV 2", centro, null);
		activeTv("TV 3", null, null);
		long id = create(body("Só centro", promo, List.of(target("LOCATION", centro))));
		long everyone = create(body("Todas", promo, List.of(all())));

		mvc.perform(asAdmin(get("/api/v1/campaigns/" + id))).andExpect(jsonPath("$.deviceCount").value(2));
		mvc.perform(asAdmin(get("/api/v1/campaigns/" + everyone))).andExpect(jsonPath("$.deviceCount").value(3));
	}

	// ---------- validação ----------

	@Test
	void rejectsInvalidCampaigns() throws Exception {
		long promo = playlist("Promo");
		List<Map<String, Object>> everyone = List.of(all());

		submit(body("  ", promo, everyone)).andExpect(status().isBadRequest());
		submit(body("X", 999999, everyone)).andExpect(status().isNotFound());
		submit(body("X", promo, List.of())).andExpect(status().isBadRequest());
		submit(body("X", promo, null)).andExpect(status().isBadRequest());

		Map<String, Object> datesBackwards = body("X", promo, everyone);
		datesBackwards.put("startDate", "2026-12-01");
		datesBackwards.put("endDate", "2026-11-01");
		submit(datesBackwards).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("A data de início não pode ser depois da data de fim"));

		Map<String, Object> sameTimes = body("X", promo, everyone);
		sameTimes.put("startTime", "18:00");
		sameTimes.put("endTime", "18:00");
		submit(sameTimes).andExpect(status().isBadRequest());

		Map<String, Object> badPriority = body("X", promo, everyone);
		badPriority.put("priority", 5000);
		submit(badPriority).andExpect(status().isBadRequest());

		Map<String, Object> badZone = body("X", promo, everyone);
		badZone.put("timezone", "Marte/Olympus");
		submit(badZone).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Fuso horário inválido: Marte/Olympus"));

		Map<String, Object> badDay = body("X", promo, everyone);
		badDay.put("daysOfWeek", List.of(1, 8));
		submit(badDay).andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidTargets() throws Exception {
		long promo = playlist("Promo");

		submit(body("X", promo, List.of(Map.of("type", "ALL", "id", 1)))).andExpect(status().isBadRequest());
		submit(body("X", promo, List.of(Map.of("type", "DEVICE")))).andExpect(status().isBadRequest());
		submit(body("X", promo, List.of(target("DEVICE", 999999)))).andExpect(status().isNotFound());
		submit(body("X", promo, List.of(target("LOCATION", 999999)))).andExpect(status().isNotFound());
		submit(body("X", promo, List.of(target("GROUP", 999999)))).andExpect(status().isNotFound());
		submit(body("X", promo, List.of(Map.of("type", "QUALQUER")))).andExpect(status().isBadRequest());
	}

	@Test
	void repeatedTargetsAreStoredOnce() throws Exception {
		long promo = playlist("Promo");
		long centro = location("Centro");

		long id = create(body("X", promo, List.of(target("LOCATION", centro), target("LOCATION", centro), all(), all())));

		mvc.perform(asAdmin(get("/api/v1/campaigns/" + id))).andExpect(jsonPath("$.targets", hasSize(2)));
	}

	// ---------- o que cada TV exibe ----------

	/** O Player não muda: ele já troca de conteúdo quando o heartbeat devolve outra playlist. */
	@Test
	void theHeartbeatPointsTheTvToTheCampaignPlaylistWhileItIsActiveAndBackAfterwards() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		String token = activeTv("TV Recepção", null, base);
		Map<String, Object> request = body("Happy hour", promo, List.of(all()));
		request.put("startTime", "18:00");
		request.put("endTime", "23:00");
		create(request);

		clock.freeze(NOON);
		heartbeat(token).andExpect(jsonPath("$.playlistId").value(base));

		clock.freeze(EVENING);
		heartbeat(token).andExpect(jsonPath("$.playlistId").value(promo));

		clock.freeze("2026-11-06T02:30:00Z"); // 23:30 em SP: acabou
		heartbeat(token).andExpect(jsonPath("$.playlistId").value(base));
	}

	@Test
	void theTvDownloadsTheContentOfTheCampaignPlaylist() throws Exception {
		long base = playlist("Base");
		long promo = createPlaylist("Promo", List.of(uploadPng("a"), uploadPng("b")));
		String token = activeTv("TV", null, base);
		create(body("Sempre", promo, List.of(all())));

		mvc.perform(asDevice(get("/api/v1/player/config"), token))
				.andExpect(jsonPath("$.playlistId").value(promo))
				.andExpect(jsonPath("$.items", hasSize(2)));
	}

	@Test
	void aTvWithoutABasePlaylistStillReceivesACampaign() throws Exception {
		long promo = playlist("Promo");
		String token = activeTv("TV", null, null);
		create(body("Sempre", promo, List.of(all())));

		heartbeat(token).andExpect(jsonPath("$.playlistId").value(promo));
	}

	@Test
	void aCampaignOnlyReachesItsTargets() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		long centro = location("Centro");
		long vitrines = group("Vitrines");
		String inCentro = activeTv("No centro", centro, base);
		String outside = activeTv("Fora", null, base);
		String inGroup = activeTv("Da vitrine", null, base);
		mvc.perform(asAdmin(post("/api/v1/devices/bulk").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("action", "ADD_TO_GROUP", "deviceIds", List.of(deviceId("Da vitrine")), "groupId", vitrines)))))
				.andExpect(status().isOk());
		create(body("Centro e vitrines", promo, List.of(target("LOCATION", centro), target("GROUP", vitrines))));

		heartbeat(inCentro).andExpect(jsonPath("$.playlistId").value(promo));
		heartbeat(inGroup).andExpect(jsonPath("$.playlistId").value(promo));
		heartbeat(outside).andExpect(jsonPath("$.playlistId").value(base));
	}

	@Test
	void theHigherPriorityCampaignWinsOnTheTv() throws Exception {
		long base = playlist("Base");
		long low = playlist("Baixa");
		long high = playlist("Alta");
		String token = activeTv("TV", null, base);
		Map<String, Object> lowRequest = body("Baixa", low, List.of(all()));
		lowRequest.put("priority", 10);
		Map<String, Object> highRequest = body("Alta", high, List.of(all()));
		highRequest.put("priority", 90);
		create(lowRequest);
		create(highRequest);

		heartbeat(token).andExpect(jsonPath("$.playlistId").value(high));
	}

	@Test
	void aPausedCampaignDoesNotReachTheTv() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		String token = activeTv("TV", null, base);
		Map<String, Object> request = body("Pausada", promo, List.of(all()));
		request.put("enabled", false);
		create(request);

		heartbeat(token).andExpect(jsonPath("$.playlistId").value(base));
	}

	@Test
	void theDeviceListShowsWhatEachTvIsShowingAndWhy() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		activeTv("TV A", null, base);
		long campaign = create(body("Sempre", promo, List.of(target("DEVICE", deviceId("TV A")))));
		activeTv("TV B", null, base);

		mvc.perform(asAdmin(get("/api/v1/devices")))
				.andExpect(jsonPath("$[0].name").value("TV A"))
				.andExpect(jsonPath("$[0].currentPlaylistId").value(base))
				.andExpect(jsonPath("$[0].effectivePlaylistId").value(promo))
				.andExpect(jsonPath("$[0].campaignId").value(campaign))
				.andExpect(jsonPath("$[0].campaignName").value("Sempre"))
				.andExpect(jsonPath("$[1].effectivePlaylistId").value(base))
				.andExpect(jsonPath("$[1].campaignId").doesNotExist());
	}

	// ---------- simulação ----------

	@Test
	void previewTellsWhatATvShowsAtAGivenMoment() throws Exception {
		long base = playlist("Base");
		long promo = playlist("Promo");
		activeTv("TV", null, base);
		long tv = deviceId("TV");
		Map<String, Object> request = body("Happy hour", promo, List.of(all()));
		request.put("startTime", "18:00");
		request.put("endTime", "23:00");
		long campaign = create(request);

		mvc.perform(asAdmin(get("/api/v1/campaigns/preview?deviceId=" + tv + "&at=" + EVENING)))
				.andExpect(jsonPath("$.source").value("CAMPAIGN"))
				.andExpect(jsonPath("$.playlistId").value(promo))
				.andExpect(jsonPath("$.playlistName").value("Promo"))
				.andExpect(jsonPath("$.campaignId").value(campaign))
				.andExpect(jsonPath("$.campaignName").value("Happy hour"));
		mvc.perform(asAdmin(get("/api/v1/campaigns/preview?deviceId=" + tv + "&at=" + NOON)))
				.andExpect(jsonPath("$.source").value("BASE"))
				.andExpect(jsonPath("$.playlistId").value(base))
				.andExpect(jsonPath("$.campaignId").doesNotExist());
	}

	@Test
	void previewSaysWhenTheTvHasNothingToShow() throws Exception {
		activeTv("TV", null, null);

		mvc.perform(asAdmin(get("/api/v1/campaigns/preview?deviceId=" + deviceId("TV"))))
				.andExpect(jsonPath("$.source").value("NONE"))
				.andExpect(jsonPath("$.playlistId").doesNotExist());
		mvc.perform(asAdmin(get("/api/v1/campaigns/preview?deviceId=999999"))).andExpect(status().isNotFound());
	}

	// ---------- integridade ----------

	/** Uma campanha que ainda usa a playlist não pode ficar sem ela; a mensagem diz o que fazer. */
	@Test
	void aPlaylistUsedByACampaignCannotBeDeleted() throws Exception {
		long promo = playlist("Promo");
		long campaign = create(body("Happy hour", promo, List.of(all())));

		mvc.perform(asAdmin(delete("/api/v1/playlists/" + promo)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value(
						"A playlist \"Promo\" está em uso pela campanha \"Happy hour\". Troque a playlist da campanha ou exclua a campanha antes."));

		mvc.perform(asAdmin(delete("/api/v1/campaigns/" + campaign))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(delete("/api/v1/playlists/" + promo))).andExpect(status().isNoContent());
	}

	@Test
	void deletingATargetRemovesItFromTheCampaignWithoutDeletingTheCampaign() throws Exception {
		long promo = playlist("Promo");
		long centro = location("Centro");
		long vitrines = group("Vitrines");
		activeTv("TV", null, null);
		long tv = deviceId("TV");
		long id = create(body("X", promo, List.of(target("LOCATION", centro), target("GROUP", vitrines), target("DEVICE", tv))));

		mvc.perform(asAdmin(delete("/api/v1/locations/" + centro))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(delete("/api/v1/groups/" + vitrines))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/campaigns/" + id))).andExpect(jsonPath("$.targets", hasSize(1)));

		mvc.perform(asAdmin(delete("/api/v1/devices/" + tv))).andExpect(status().isNoContent());
		mvc.perform(asAdmin(get("/api/v1/campaigns/" + id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targets", hasSize(0)))
				.andExpect(jsonPath("$.deviceCount").value(0));
	}

	@Test
	void updatingTheTargetsReplacesThem() throws Exception {
		long promo = playlist("Promo");
		long centro = location("Centro");
		long id = create(body("X", promo, List.of(all())));

		mvc.perform(asAdmin(put("/api/v1/campaigns/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body("X", promo, List.of(target("LOCATION", centro)))))))
				.andExpect(jsonPath("$.targets", hasSize(1)))
				.andExpect(jsonPath("$.targets[0].type").value("LOCATION"));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM campaign_targets", Integer.class));
	}

	// ---------- dashboard e acesso ----------

	@Test
	void theSummaryCountsCampaignsAndTheOnesActiveNow() throws Exception {
		long promo = playlist("Promo");
		clock.freeze(NOON);
		create(body("Ativa", promo, List.of(all())));
		Map<String, Object> night = body("Noturna", promo, List.of(all()));
		night.put("startTime", "22:00");
		night.put("endTime", "23:00");
		create(night);
		Map<String, Object> paused = body("Pausada", promo, List.of(all()));
		paused.put("enabled", false);
		create(paused);

		mvc.perform(asAdmin(get("/api/v1/summary")))
				.andExpect(jsonPath("$.campaigns.total").value(3))
				.andExpect(jsonPath("$.campaigns.activeNow").value(1));
	}

	@Test
	void campaignsNeedAnAdmin() throws Exception {
		mvc.perform(get("/api/v1/campaigns")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/campaigns/preview?deviceId=1")).andExpect(status().isUnauthorized());
	}

}
