package com.digitalsignage.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.digitalsignage.backend.proof.PlayProofHash;
import com.digitalsignage.backend.proof.PlayProofService;

/** Comprovação de exibição: recebimento em lotes, corrente de hashes, relatório, conferência, CSV e prazo de guarda. */
class PlayProofTest extends AbstractApiTest {

	private static final String NOW = "2026-11-05T15:00:00Z"; // 12:00 em São Paulo
	private static final long NOW_MS = Instant.parse(NOW).toEpochMilli();

	@Autowired
	private PlayProofService proof;

	/** O mesmo vetor que o Player confere em PlayLogTest: a conta tem de dar igual nos dois lados. */
	@Test
	void theHashMatchesThePlayersCalculation() {
		String first = PlayProofHash.compute(PlayProofHash.GENESIS, 1, 42, 5L, "Promoção de verão", "IMAGE", 7L, null, "Principal", 1_762_354_800_123L, 10_012, 10_000L, true);
		String second = PlayProofHash.compute(first, 2, 42, 6L, "Cardápio", "VIDEO", 7L, 55L, "Lateral", 1_762_354_810_135L, 8_000, null, false);

		assertEquals("9d538e07afa7754a5276f62c82025327f52d320a9aa0b9f498dc0be7b89807ec", first);
		assertEquals("1a93acc86cac654c535ae7f001c01d4ea206e984b78507c216de3bb3611e6b37", second);
	}

	// ---------- montagem ----------

	private record Tv(String token, long id) {
	}

	/** Uma TV de verdade: registra pelo Player e ativa pelo painel. */
	private Tv tv(String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.8.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		long id = idOf(mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name)))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		return new Tv(token, id);
	}

	/** Os campos de um registro, antes de ganhar o hash. */
	private record Play(long seq, Long mediaId, String name, long startedAtMs, long durationMs, Long plannedMs, boolean completed, Long campaignId, String zone) {
		static Play of(long seq, long mediaId, String name, long startedAtMs, long durationMs) {
			return new Play(seq, mediaId, name, startedAtMs, durationMs, 10_000L, true, null, "Principal");
		}
	}

	/** Encadeia os registros como o Player faz (cada um leva o hash do anterior). */
	private List<Map<String, Object>> chain(Tv tv, String firstPrevHash, List<Play> plays) {
		List<Map<String, Object>> entries = new ArrayList<>();
		String prev = firstPrevHash;
		for (Play p : plays) {
			String hash = PlayProofHash.compute(prev, p.seq(), tv.id(), p.mediaId(), p.name(), "IMAGE", 7L, p.campaignId(), p.zone(),
					p.startedAtMs(), p.durationMs(), p.plannedMs(), p.completed());
			Map<String, Object> entry = new HashMap<>();
			entry.put("seq", p.seq());
			entry.put("mediaId", p.mediaId());
			entry.put("mediaName", p.name());
			entry.put("mediaType", "IMAGE");
			entry.put("playlistId", 7L);
			entry.put("campaignId", p.campaignId());
			entry.put("campaignName", p.campaignId() == null ? null : "Promoção");
			entry.put("zoneName", p.zone());
			entry.put("startedAtMs", p.startedAtMs());
			entry.put("durationMs", p.durationMs());
			entry.put("plannedMs", p.plannedMs());
			entry.put("completed", p.completed());
			entry.put("prevHash", prev);
			entry.put("hash", hash);
			entries.add(entry);
			prev = hash;
		}
		return entries;
	}

	private ResultActions send(Tv tv, long deviceTimeMs, List<Map<String, Object>> entries) throws Exception {
		return mvc.perform(post("/api/v1/player/plays").header("X-Device-Token", tv.token()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceTimeMs", deviceTimeMs, "entries", entries))));
	}

	private List<Play> sample(long from, int count) {
		List<Play> plays = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			plays.add(Play.of(from + i, 1 + (i % 2), i % 2 == 0 ? "Promoção" : "Cardápio", NOW_MS + i * 10_000L + 123, 10_000 + i));
		}
		return plays;
	}

	private String lastHash(Tv tv) {
		return jdbc.queryForObject("SELECT encode(hash, 'hex') FROM play_logs WHERE device_id = ? ORDER BY seq DESC LIMIT 1", String.class, tv.id());
	}

	// ---------- recebimento ----------

	@Test
	void storesTheChainAndReportsEveryPlayWithMilliseconds() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");

		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 4)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acknowledgedSeq").value(4))
				.andExpect(jsonPath("$.stored").value(4))
				.andExpect(jsonPath("$.duplicates").value(0))
				.andExpect(jsonPath("$.rejected").value(0));

		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(4))
				.andExpect(jsonPath("$.completed").value(4))
				.andExpect(jsonPath("$.totalDurationMs").value(10_000 + 10_001 + 10_002 + 10_003))
				.andExpect(jsonPath("$.devices").value(1))
				.andExpect(jsonPath("$.rows", hasSize(4)))
				// do mais recente para o mais antigo; o horário guarda os milissegundos da TV
				.andExpect(jsonPath("$.rows[0].seq").value(4))
				.andExpect(jsonPath("$.rows[0].deviceName").value("TV A"))
				.andExpect(jsonPath("$.rows[3].startedAt").value("2026-11-05T15:00:00.123Z"))
				.andExpect(jsonPath("$.rows[3].durationMs").value(10_000))
				.andExpect(jsonPath("$.rows[3].plannedMs").value(10_000))
				.andExpect(jsonPath("$.rows[3].completed").value(true))
				.andExpect(jsonPath("$.rows[3].hashValid").value(true))
				.andExpect(jsonPath("$.rows[3].continuous").value(true));
	}

	@Test
	void sendingTheSameBatchAgainChangesNothing() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		List<Map<String, Object>> batch = chain(a, PlayProofHash.GENESIS, sample(1, 3));
		send(a, NOW_MS, batch).andExpect(jsonPath("$.stored").value(3));

		send(a, NOW_MS, batch)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stored").value(0))
				.andExpect(jsonPath("$.duplicates").value(3))
				.andExpect(jsonPath("$.acknowledgedSeq").value(3));

		assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM play_logs", Integer.class));
	}

	@Test
	void aLaterBatchContinuesTheSameChain() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 2))).andExpect(status().isOk());

		send(a, NOW_MS, chain(a, lastHash(a), sample(3, 2))).andExpect(jsonPath("$.stored").value(2)).andExpect(jsonPath("$.acknowledgedSeq").value(4));

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM play_logs WHERE NOT continuous OR NOT hash_valid", Integer.class));
		mvc.perform(asAdmin(get("/api/v1/reports/proof/verify?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.devices[0].records").value(4))
				.andExpect(jsonPath("$.devices[0].status").value("OK"))
				.andExpect(jsonPath("$.devices[0].digest").exists());
	}

	@Test
	void aMissingRecordIsNoticedAsAGap() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 2))).andExpect(status().isOk());
		// o Player perdeu os registros 3 e 4 (o aparelho foi reiniciado sem salvar): o 5 chega apontando para o 4, que o servidor não tem
		List<Map<String, Object>> later = chain(a, "f".repeat(64), sample(5, 1));

		send(a, NOW_MS, later).andExpect(jsonPath("$.stored").value(1));

		assertEquals(false, jdbc.queryForObject("SELECT continuous FROM play_logs WHERE seq = 5", Boolean.class));
		mvc.perform(asAdmin(get("/api/v1/reports/proof/verify?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.devices[0].missing").value(2))
				.andExpect(jsonPath("$.devices[0].status").value("ATENCAO"));
	}

	@Test
	void aRecordWhoseHashDoesNotMatchIsStoredButFlagged() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		List<Map<String, Object>> batch = chain(a, PlayProofHash.GENESIS, sample(1, 2));
		batch.get(1).put("durationMs", 99_999L); // alterado depois de calculado o hash

		send(a, NOW_MS, batch).andExpect(jsonPath("$.stored").value(2));

		assertEquals(false, jdbc.queryForObject("SELECT hash_valid FROM play_logs WHERE seq = 2", Boolean.class));
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.rows[0].hashValid").value(false));
	}

	@Test
	void editingARowAfterwardsIsCaughtByTheVerification() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 3))).andExpect(status().isOk());

		jdbc.update("UPDATE play_logs SET duration_ms = duration_ms + 5000 WHERE seq = 2");

		mvc.perform(asAdmin(get("/api/v1/reports/proof/verify?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.devices[0].hashMismatches").value(1));
	}

	@Test
	void invalidEntriesAreRejectedWithoutBlockingTheGoodOnes() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		List<Map<String, Object>> batch = chain(a, PlayProofHash.GENESIS, sample(1, 3));
		batch.get(1).put("durationMs", -5L);
		batch.get(2).put("hash", "não-é-um-hash");

		// o hash fora de formato é recusado já na validação do pedido (400); o resto do lote vale por si
		send(a, NOW_MS, batch).andExpect(status().isBadRequest());
		batch.get(2).put("hash", "a".repeat(64));
		send(a, NOW_MS, batch).andExpect(status().isOk()).andExpect(jsonPath("$.rejected").value(1)).andExpect(jsonPath("$.stored").value(2));
	}

	// ---------- relógio ----------

	@Test
	void aTvClockThatIsWrongIsCorrectedByTheServer() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		long tenMinutesBehind = NOW_MS - 600_000;
		// a TV acha que são 11:50 e registrou uma exibição às 11:49:50.000 (no relógio dela)
		List<Play> plays = List.of(Play.of(1, 1, "Promoção", tenMinutesBehind - 10_000, 10_000));

		send(a, tenMinutesBehind, chain(a, PlayProofHash.GENESIS, plays)).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.rows[0].startedAt").value("2026-11-05T14:59:50Z"))
				.andExpect(jsonPath("$.rows[0].clockOffsetMs").value(600_000));
		assertEquals(tenMinutesBehind - 10_000, jdbc.queryForObject("SELECT device_started_ms FROM play_logs", Long.class));
	}

	@Test
	void aSmallDifferenceIsJustNetworkTimeAndIsNotApplied() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");

		send(a, NOW_MS - 400, chain(a, PlayProofHash.GENESIS, sample(1, 1))).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$.rows[0].clockOffsetMs").value(0))
				.andExpect(jsonPath("$.rows[0].startedAt").value("2026-11-05T15:00:00.123Z"));
	}

	// ---------- filtros e período ----------

	@Test
	void filtersByDeviceMediaAndCampaignAndPeriod() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		Tv b = tv("TV B");
		List<Play> plays = List.of(
				Play.of(1, 1, "Promoção", NOW_MS, 10_000),
				new Play(2L, 2L, "Cardápio", NOW_MS + 20_000, 8_000, 10_000L, false, 55L, "Lateral"),
				Play.of(3, 1, "Promoção", NOW_MS + 40_000, 10_000));
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, plays)).andExpect(status().isOk());
		send(b, NOW_MS, chain(b, PlayProofHash.GENESIS, List.of(Play.of(1, 1, "Promoção", NOW_MS + 5_000, 10_000)))).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05"))).andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.devices").value(2));
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05&deviceId=" + b.id()))).andExpect(jsonPath("$.total").value(1));
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05&mediaId=2")))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.completed").value(0))
				.andExpect(jsonPath("$.rows[0].campaignName").value("Promoção"))
				.andExpect(jsonPath("$.rows[0].zoneName").value("Lateral"))
				.andExpect(jsonPath("$.rows[0].completed").value(false));
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05&campaignId=55"))).andExpect(jsonPath("$.total").value(1));
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-06&to=2026-11-06"))).andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void pagesThroughTheRows() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 5))).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05&limit=2&offset=2")))
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.rows", hasSize(2)))
				.andExpect(jsonPath("$.rows[0].seq").value(3));
	}

	@Test
	void theDayFollowsTheReportTimezoneNotUtc() throws Exception {
		clock.freeze("2026-11-06T01:00:00Z"); // 22:00 do dia 5 em São Paulo
		Tv a = tv("TV A");
		long at = Instant.parse("2026-11-06T01:00:00Z").toEpochMilli();
		send(a, at, chain(a, PlayProofHash.GENESIS, List.of(Play.of(1, 1, "Promoção", at - 30_000, 10_000)))).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05"))).andExpect(jsonPath("$.total").value(1));
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-06&to=2026-11-06"))).andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void anInvertedOrTooLongPeriodIsRefused() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-06&to=2026-11-05"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-01-01&to=2026-11-05")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(containsString("93")));
	}

	// ---------- CSV ----------

	@Test
	void csvHasEveryColumnAndTheMillisecondsOfTheStartTime() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, List.of(new Play(1L, 1L, "Promoção; verão", NOW_MS + 7, 9_876, 10_000L, false, null, "Principal"))))
				.andExpect(status().isOk());

		String csv = mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05&format=csv")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("comprovacao-2026-11-05-a-2026-11-05.csv")))
				.andExpect(content().contentTypeCompatibleWith("text/csv"))
				.andReturn().getResponse().getContentAsString();

		assertTrue(csv.startsWith("﻿Início (com milissegundos);TV;Mídia"));
		assertTrue(csv.contains("2026-11-05T12:00:00.007-03:00"), csv);
		assertTrue(csv.contains("\"Promoção; verão\""));
		assertTrue(csv.contains(";9876;10000;Interrompida;1;"));
		assertTrue(csv.contains(";sim;0"));
	}

	// ---------- resumo diário e prazo de guarda ----------

	@Test
	void theDailySummaryGrowsWithEachBatchAndKeepsTheLastHash() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 3))).andExpect(status().isOk());
		String hash3 = lastHash(a);
		send(a, NOW_MS, chain(a, hash3, sample(4, 2))).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof/daily?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].plays").value(5))
				.andExpect(jsonPath("$[0].firstSeq").value(1))
				.andExpect(jsonPath("$[0].lastSeq").value(5))
				.andExpect(jsonPath("$[0].lastHash").value(lastHash(a)))
				.andExpect(jsonPath("$[0].totalMs").value(10_000 + 10_001 + 10_002 + 10_000 + 10_001));
	}

	@Test
	void purgeRemovesTheDetailAfterTheRetentionButKeepsTheDailySummary() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 3))).andExpect(status().isOk());

		assertEquals(0, inTenant(DEFAULT_TENANT, () -> proof.purge(Instant.parse(NOW).plusSeconds(13 * 86_400L), 14)));
		assertEquals(3, inTenant(DEFAULT_TENANT, () -> proof.purge(Instant.parse(NOW).plusSeconds(15 * 86_400L), 14)));

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM play_logs", Integer.class));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM play_log_daily", Integer.class));
	}

	@Test
	void everyHashIsStoredAs32BytesAndComesBackAsTheSameText() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 2))).andExpect(status().isOk());

		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM play_logs WHERE octet_length(hash) = 32 AND octet_length(prev_hash) = 32", Integer.class));
		String csv = mvc.perform(asAdmin(get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05&format=csv"))).andReturn().getResponse().getContentAsString();
		assertTrue(csv.contains(lastHash(a)), "o relatório mostra o hash em texto, igual ao que o Player mandou");
		mvc.perform(asAdmin(get("/api/v1/reports/proof/verify?from=2026-11-05&to=2026-11-05"))).andExpect(jsonPath("$.ok").value(true));
	}

	@Test
	void theDailySummaryByMediaCountsEachMediaAndCampaignAndSurvivesThePurge() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		List<Play> plays = new ArrayList<>(sample(1, 3)); // mídia 1 ("Promoção"): seq 1 e 3; mídia 2 ("Cardápio"): seq 2
		plays.add(new Play(4, 1L, "Promoção", NOW_MS + 40_000L, 10_000, 10_000L, false, 9L, "Principal")); // a mesma mídia, agora numa campanha
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, plays)).andExpect(status().isOk());
		send(a, NOW_MS, chain(a, lastHash(a), List.of(Play.of(5, 2, "Cardápio", NOW_MS + 50_000L, 7_000)))).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof/daily-media?from=2026-11-05&to=2026-11-05")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[?(@.mediaName == 'Promoção' && @.campaignId == null)].plays").value(2))
				.andExpect(jsonPath("$[?(@.mediaName == 'Promoção' && @.campaignId == 9)].plays").value(1))
				.andExpect(jsonPath("$[?(@.mediaName == 'Promoção' && @.campaignId == 9)].completed").value(0))
				.andExpect(jsonPath("$[?(@.mediaName == 'Cardápio')].plays").value(2))
				.andExpect(jsonPath("$[?(@.mediaName == 'Cardápio')].totalMs").value(10_001 + 7_000));

		// sem o detalhe, o resumo por mídia continua lá
		inTenant(DEFAULT_TENANT, () -> proof.purge(Instant.parse(NOW).plusSeconds(15 * 86_400L), 14));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM play_logs", Integer.class));
		mvc.perform(asAdmin(get("/api/v1/reports/proof/daily-media?from=2026-11-05&to=2026-11-05"))).andExpect(jsonPath("$", hasSize(3)));

		String csv = mvc.perform(asAdmin(get("/api/v1/reports/proof/daily-media?from=2026-11-05&to=2026-11-05&format=csv")))
				.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse().getContentAsString();
		assertTrue(csv.startsWith("\uFEFFDia;TV;Mídia;Tipo;Campanha;Exibições;Completas;Tempo no ar (s)"), csv);
		assertTrue(csv.contains("2026-11-05;TV A;Cardápio;IMAGE;;2;2;17"), csv);
	}

	@Test
	void aTvResendingTheSameBatchDoesNotCountTwiceInTheMediaSummary() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		List<Map<String, Object>> batch = chain(a, PlayProofHash.GENESIS, sample(1, 3));
		send(a, NOW_MS, batch).andExpect(status().isOk());
		send(a, NOW_MS, batch).andExpect(status().isOk());

		mvc.perform(asAdmin(get("/api/v1/reports/proof/daily-media?from=2026-11-05&to=2026-11-05")))
				.andExpect(jsonPath("$[?(@.mediaName == 'Promoção')].plays").value(2))
				.andExpect(jsonPath("$[?(@.mediaName == 'Cardápio')].plays").value(1));
	}

	@Test
	void theRetentionIsASettingWithLimits() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/alerts/settings"))).andExpect(jsonPath("$.playLogRetentionDays").value(7)); // novo cliente: 7 dias (era 14)
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playLogRetentionDays", 30)))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.playLogRetentionDays").value(30));
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playLogRetentionDays", 3)))))
				.andExpect(status().isBadRequest());
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("playLogRetentionDays", 400)))))
				.andExpect(status().isBadRequest());
	}

	// ---------- acesso ----------

	@Test
	void aTvCannotReadTheReportAndAnonymousCannotSend() throws Exception {
		Tv a = tv("TV A");

		mvc.perform(get("/api/v1/reports/proof").header("X-Device-Token", a.token())).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/player/plays").contentType(MediaType.APPLICATION_JSON).content("{\"deviceTimeMs\":1,\"entries\":[]}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aViewerCanReadTheReport() throws Exception {
		createUserRow(DEFAULT_TENANT, "visualizador@teste.com", "VIEWER", "senha-forte-1");
		String viewer = loginAs("visualizador@teste.com", "senha-forte-1");

		mvc.perform(withToken(viewer, get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05"))).andExpect(status().isOk());
		mvc.perform(withToken(viewer, get("/api/v1/reports/proof/verify"))).andExpect(status().isOk());
	}

	@Test
	void anotherClientNeverSeesTheProofOfMyTvs() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		send(a, NOW_MS, chain(a, PlayProofHash.GENESIS, sample(1, 2))).andExpect(status().isOk());
		long other = createTenantRow("Cliente B", "interno", "ACTIVE");
		createUserRow(other, "b@teste.com", "ADMIN", "senha-forte-1");
		String tokenB = loginAs("b@teste.com", "senha-forte-1");

		mvc.perform(withToken(tokenB, get("/api/v1/reports/proof?from=2026-11-05&to=2026-11-05")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0));
		mvc.perform(withToken(tokenB, get("/api/v1/reports/proof/verify?from=2026-11-05&to=2026-11-05"))).andExpect(jsonPath("$.devices", hasSize(0)));
		mvc.perform(withToken(tokenB, get("/api/v1/reports/proof/daily?from=2026-11-05&to=2026-11-05"))).andExpect(jsonPath("$", hasSize(0)));
		assertFalse(jdbc.queryForObject("SELECT count(*) FROM play_logs WHERE tenant_id = ?", Integer.class, other) > 0);
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM play_logs WHERE tenant_id = ?", Integer.class, DEFAULT_TENANT));
	}

	// ---------- campanha na configuração ----------

	@Test
	void theConfigAndTheHeartbeatSayWhichCampaignIsOnAir() throws Exception {
		clock.freeze(NOW);
		Tv a = tv("TV A");
		long media = uploadPng("promo");
		long base = createPlaylist("Base", List.of(media));
		long promoPlaylist = createPlaylist("Promo", List.of(media));
		assignPlaylist(a.id(), "TV A", base);
		long campaign = idOf(mvc.perform(asAdmin(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Verão", "playlistId", promoPlaylist, "targets", List.of(Map.of("type", "ALL")))))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		mvc.perform(get("/api/v1/player/config").header("X-Device-Token", a.token()))
				.andExpect(jsonPath("$.campaignId").value(campaign))
				.andExpect(jsonPath("$.campaignName").value("Verão"));
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", a.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"playerVersion\":\"0.8.1\",\"configVersion\":0}"))
				.andExpect(jsonPath("$.campaignId").value(campaign));

		mvc.perform(put("/api/v1/campaigns/" + campaign).header("Authorization", "Bearer " + adminToken()).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Verão", "playlistId", promoPlaylist, "enabled", false, "targets", List.of(Map.of("type", "ALL"))))))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/player/config").header("X-Device-Token", a.token())).andExpect(jsonPath("$.campaignId").doesNotExist());
	}

}
