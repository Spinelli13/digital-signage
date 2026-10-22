package com.digitalsignage.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.digitalsignage.backend.promo.PublicPromotionService;
import com.digitalsignage.backend.promo.ScanOrigin;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Resultados do QR Code e dos cupons: escaneamentos por dia e por origem (a TV), cupons pegos e usados, e quantas vezes o conteúdo
 * rodou, num painel simples e num CSV. 05/11/2026 12:00 em São Paulo = 15:00Z.
 */
class ResultsTest extends AbstractApiTest {

	private static final String NOON = "2026-11-05T15:00:00Z";

	@Autowired
	private PublicPromotionService publicPromotions;

	@BeforeEach
	void reset() {
		publicPromotions.forgetClients();
		clock.freeze(NOON);
	}

	private record Shop(long tenantId, String admin, String viewer) {
	}

	private Shop shop(String name, String plan) throws Exception {
		long tenant = createTenantRow(name, plan, "ACTIVE");
		createUserRow(tenant, "admin" + tenant + "@loja.com", "ADMIN", "senha-forte-1");
		createUserRow(tenant, "viewer" + tenant + "@loja.com", "VIEWER", "senha-forte-1");
		return new Shop(tenant, loginAs("admin" + tenant + "@loja.com", "senha-forte-1"), loginAs("viewer" + tenant + "@loja.com", "senha-forte-1"));
	}

	private JsonNode promotion(Shop shop, String name) throws Exception {
		return json.readTree(mvc.perform(withToken(shop.admin(), post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name, "claimDays", 7))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long tv(Shop shop, String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.9.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		return idOf(mvc.perform(withToken(shop.admin(), post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	/** Quem escaneia o QR Code: sem login, com o código do aparelho. */
	private ResultActions scan(String token, String origin, String device) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/public/promotions/" + token);
		if (origin != null) {
			request.param("o", origin);
		}
		if (device != null) {
			request.header("X-Client-Id", device);
		}
		return mvc.perform(request);
	}

	private ResultActions claim(String token, String origin, String device) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("origin", origin);
		MockHttpServletRequestBuilder request = post("/api/v1/public/promotions/" + token + "/coupons").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body));
		if (device != null) {
			request.header("X-Client-Id", device);
		}
		return mvc.perform(request);
	}

	private static String device(int n) {
		return "aparelho-" + String.format("%04d", n) + "-abcdef";
	}

	private void plays(Shop shop, long deviceId, String day, long plays, String media, long mediaId) {
		jdbc.update("INSERT INTO play_log_daily (tenant_id, device_id, day, plays, completed, total_ms, first_seq, last_seq, last_hash) VALUES (?, ?, ?, ?, ?, 0, 1, 1, repeat('0', 64))",
				shop.tenantId(), deviceId, Date.valueOf(day), plays, plays);
		jdbc.update("INSERT INTO play_log_daily_media (tenant_id, device_id, day, media_id, campaign_id, media_name, media_type, plays, completed, total_ms) VALUES (?, ?, ?, ?, 0, ?, 'IMAGE', ?, ?, 0)",
				shop.tenantId(), deviceId, Date.valueOf(day), mediaId, media, plays, plays);
	}

	private JsonNode results(String token, String query) throws Exception {
		return json.readTree(mvc.perform(withToken(token, get("/api/v1/results" + query))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	// ---------- a origem e a contagem ----------

	@Test
	void theOriginIsOnlyAShortSimpleLabel() {
		assertEquals("tv-12", ScanOrigin.clean("tv-12"));
		assertEquals("balcao", ScanOrigin.clean("  Balcao "));
		assertEquals("", ScanOrigin.clean(null));
		assertEquals("", ScanOrigin.clean("tv 12"));
		assertEquals("", ScanOrigin.clean("<script>"));
		assertEquals("", ScanOrigin.clean("a".repeat(25)));
		assertEquals("a".repeat(24), ScanOrigin.clean("a".repeat(24)));
		assertEquals(12L, ScanOrigin.deviceOf("tv-12"));
		assertNull(ScanOrigin.deviceOf("balcao"));
		assertNull(ScanOrigin.deviceOf("tv-"));
	}

	@Test
	void everyOpeningOfThePageCountsOncePerDeviceAndOriginAndDay() throws Exception {
		Shop s = shop("Loja", "interno");
		long tv = tv(s, "TV Entrada");
		String token = promotion(s, "10% off").get("publicToken").asText();

		scan(token, "tv-" + tv, device(1)).andExpect(status().isOk());
		scan(token, "tv-" + tv, device(1)).andExpect(status().isOk()); // recarregou a página: não conta de novo
		scan(token, "tv-" + tv, device(2)).andExpect(status().isOk()); // outro aparelho: conta
		scan(token, "balcao", device(1)).andExpect(status().isOk()); // o mesmo aparelho em outro QR Code: conta
		scan(token, null, device(3)).andExpect(status().isOk()); // QR geral, sem origem
		scan(token, "Isso não é uma origem!", device(4)).andExpect(status().isOk()); // origem inválida vira "sem origem"

		clock.freeze("2026-11-06T15:00:00Z");
		scan(token, "tv-" + tv, device(1)).andExpect(status().isOk()); // no dia seguinte conta de novo (e passou da janela)

		Map<String, Integer> byOrigin = new HashMap<>();
		Map<String, Integer> byDay = new HashMap<>();
		jdbc.queryForList("SELECT day, origin, scans FROM promotion_scans").forEach(row -> {
			byOrigin.merge((String) row.get("origin"), ((Number) row.get("scans")).intValue(), Integer::sum);
			byDay.merge(row.get("day").toString(), ((Number) row.get("scans")).intValue(), Integer::sum);
		});
		assertEquals(3, byOrigin.get("tv-" + tv)); // 2 no dia 5 + 1 no dia 6
		assertEquals(1, byOrigin.get("balcao"));
		assertEquals(2, byOrigin.get("")); // o QR geral e a origem inválida
		assertEquals(5, byDay.get("2026-11-05"));
		assertEquals(1, byDay.get("2026-11-06"));
	}

	@Test
	void anUnknownQrCodeCountsNothingAndAFailedLookupIsA404() throws Exception {
		scan("0".repeat(32), "tv-1", device(1)).andExpect(status().isNotFound());
		scan("não-é-token", null, null).andExpect(status().isNotFound());

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM promotion_scans", Integer.class));
	}

	@Test
	void aCouponRemembersWhereItsQrCodeCameFrom() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Frete").get("publicToken").asText();

		claim(token, "tv-7", device(1)).andExpect(status().isCreated());
		claim(token, "Panfleto Inválido!", device(2)).andExpect(status().isCreated());
		claim(token, null, device(3)).andExpect(status().isCreated());

		assertEquals(java.util.List.of("tv-7"), jdbc.queryForList("SELECT origin FROM promotion_coupons WHERE origin IS NOT NULL", String.class));
		assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM promotion_coupons", Integer.class));
	}

	// ---------- o painel de resultados ----------

	@Test
	void theReportPutsPlaysScansAndCouponsTogetherPerDayAndPerTv() throws Exception {
		Shop s = shop("Loja", "interno");
		long entrada = tv(s, "TV Entrada");
		long balcao = tv(s, "TV Balcão");
		JsonNode promo = promotion(s, "Corte grátis");
		String token = promo.get("publicToken").asText();
		plays(s, entrada, "2026-11-04", 100, "Promo", 1);
		plays(s, entrada, "2026-11-05", 150, "Promo", 1);
		plays(s, balcao, "2026-11-05", 50, "Cardápio", 2);

		// dia 5: três escaneamentos na TV Entrada, um no balcão (QR de papel) e um QR geral; dois cupons pegos pela TV, um usado
		scan(token, "tv-" + entrada, device(1));
		scan(token, "tv-" + entrada, device(2));
		scan(token, "tv-" + entrada, device(3));
		scan(token, "balcao", device(4));
		scan(token, null, device(5));
		JsonNode first = json.readTree(claim(token, "tv-" + entrada, device(1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		claim(token, "tv-" + entrada, device(2)).andExpect(status().isCreated());
		mvc.perform(withToken(s.admin(), post("/api/v1/promotions/coupons/redeem").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("code", first.get("code").asText()))))).andExpect(status().isOk());

		JsonNode report = results(s.viewer(), "?from=2026-11-03&to=2026-11-05").get("report");

		assertEquals(300, report.get("plays").asLong());
		assertEquals(2, report.get("devicesWithPlays").asInt());
		assertEquals(5, report.get("scans").asLong());
		assertEquals(2, report.get("claimed").asLong());
		assertEquals(1, report.get("redeemed").asLong());
		assertEquals("America/Sao_Paulo", report.get("timezone").asText());
		// os três dias do período, o primeiro zerado
		assertEquals(3, report.get("days").size());
		assertEquals("2026-11-03", report.get("days").get(0).get("day").asText());
		assertEquals(0, report.get("days").get(0).get("plays").asLong());
		assertEquals(100, report.get("days").get(1).get("plays").asLong());
		JsonNode today = report.get("days").get(2);
		assertEquals(200, today.get("plays").asLong());
		assertEquals(5, today.get("scans").asLong());
		assertEquals(2, today.get("claimed").asLong());
		assertEquals(1, today.get("redeemed").asLong());
		// por TV: a de maior exibição primeiro; a TV Entrada leva os escaneamentos e cupons do QR dela
		JsonNode tvs = report.get("tvs");
		assertEquals("TV Entrada", tvs.get(0).get("name").asText());
		assertEquals(250, tvs.get(0).get("plays").asLong());
		assertEquals(3, tvs.get(0).get("scans").asLong());
		assertEquals(2, tvs.get(0).get("claimed").asLong());
		assertEquals(1, tvs.get(0).get("redeemed").asLong());
		assertEquals("TV Balcão", tvs.get(1).get("name").asText());
		assertEquals(0, tvs.get(1).get("scans").asLong());
		// outras origens: o QR de papel e o geral
		JsonNode origins = report.get("origins");
		assertEquals(2, origins.size());
		assertTrue(origins.toString().contains("\"origin\":\"balcao\"") && origins.toString().contains("\"origin\":\"\""));
		// por promoção e as mídias que mais rodaram
		assertEquals("Corte grátis", report.get("promotions").get(0).get("name").asText());
		assertEquals(5, report.get("promotions").get(0).get("scans").asLong());
		assertEquals("Promo", report.get("topMedia").get(0).get("name").asText());
		assertEquals(250, report.get("topMedia").get(0).get("plays").asLong());
		assertEquals(true, results(s.viewer(), "").get("couponsEnabled").asBoolean());
	}

	@Test
	void thePeriodAndThePromotionFilterTheNumbers() throws Exception {
		Shop s = shop("Loja", "interno");
		long tv = tv(s, "TV");
		JsonNode a = promotion(s, "Promo A");
		JsonNode b = promotion(s, "Promo B");
		scan(a.get("publicToken").asText(), "tv-" + tv, device(1));
		scan(b.get("publicToken").asText(), "tv-" + tv, device(1));
		scan(b.get("publicToken").asText(), "tv-" + tv, device(2));
		clock.freeze("2026-11-20T15:00:00Z");
		scan(b.get("publicToken").asText(), "tv-" + tv, device(3));

		assertEquals(4, results(s.admin(), "?from=2026-11-01&to=2026-11-30").get("report").get("scans").asLong());
		assertEquals(3, results(s.admin(), "?from=2026-11-01&to=2026-11-10").get("report").get("scans").asLong());
		assertEquals(1, results(s.admin(), "?from=2026-11-01&to=2026-11-30&promotionId=" + a.get("id").asLong()).get("report").get("scans").asLong());
		// sem datas: os últimos 30 dias até hoje (20/11)
		assertEquals(30, results(s.admin(), "").get("report").get("days").size());
	}

	@Test
	void aDeletedTvKeepsItsScansWithoutAName() throws Exception {
		Shop s = shop("Loja", "interno");
		long tv = tv(s, "TV Velha");
		String token = promotion(s, "Promo").get("publicToken").asText();
		scan(token, "tv-" + tv, device(1));
		mvc.perform(withToken(s.admin(), org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/devices/" + tv))).andExpect(status().isNoContent());

		JsonNode tvs = results(s.admin(), "?from=2026-11-05&to=2026-11-05").get("report").get("tvs");

		assertEquals(1, tvs.size());
		assertTrue(tvs.get(0).get("name").isNull());
		assertEquals(1, tvs.get(0).get("scans").asLong());
	}

	@Test
	void csvPerDayPerTvAndPerPromotionOpensInExcel() throws Exception {
		Shop s = shop("Loja", "interno");
		long tv = tv(s, "TV Entrada");
		String token = promotion(s, "Promo").get("publicToken").asText();
		plays(s, tv, "2026-11-05", 10, "Promo", 1);
		scan(token, "tv-" + tv, device(1));
		scan(token, "panfleto", device(2));

		mvc.perform(withToken(s.viewer(), get("/api/v1/results").param("from", "2026-11-05").param("to", "2026-11-05").param("format", "csv")))
				.andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("resultados-por-dia-2026-11-05-a-2026-11-05.csv")))
				.andExpect(content().string(containsString("Dia;Exibições do conteúdo;QR Codes escaneados;Cupons pegos;Cupons usados")))
				.andExpect(content().string(containsString("2026-11-05;10;2;0;0")));
		mvc.perform(withToken(s.viewer(), get("/api/v1/results").param("from", "2026-11-05").param("to", "2026-11-05").param("format", "csv").param("by", "tv")))
				.andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("resultados-por-tv-")))
				.andExpect(content().string(containsString("TV Entrada;10;1;0;0"))).andExpect(content().string(containsString("QR Code: panfleto;;1;0;0")));
		mvc.perform(withToken(s.viewer(), get("/api/v1/results").param("from", "2026-11-05").param("to", "2026-11-05").param("format", "csv").param("by", "promotion")))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Promo;2;0;0")));
	}

	@Test
	void validatesThePeriodAndNeedsALoginButAnyRole() throws Exception {
		Shop s = shop("Loja", "interno");

		mvc.perform(withToken(s.viewer(), get("/api/v1/results").param("from", "2026-11-10").param("to", "2026-11-01"))).andExpect(status().isBadRequest());
		mvc.perform(withToken(s.viewer(), get("/api/v1/results").param("from", "2024-01-01").param("to", "2026-11-01"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", startsWith("O período pode ter no máximo")));
		mvc.perform(get("/api/v1/results")).andExpect(status().isUnauthorized());
		results(s.viewer(), ""); // o Visualizador também vê
	}

	@Test
	void aPlanWithoutCouponsSaysSoAndEachClientOnlySeesItsOwnNumbers() throws Exception {
		Shop withCoupons = shop("Com cupons", "interno");
		Shop without = shop("Sem cupons", "start");
		String token = promotion(withCoupons, "Promo").get("publicToken").asText();
		scan(token, null, device(1));

		assertEquals(false, results(without.admin(), "").get("couponsEnabled").asBoolean());
		assertEquals(0, results(without.admin(), "").get("report").get("scans").asLong());
		assertEquals(1, results(withCoupons.admin(), "").get("report").get("scans").asLong());
	}

	@Test
	void theScanDayFollowsSaoPauloNotUtc() throws Exception {
		Shop s = shop("Loja", "interno");
		String token = promotion(s, "Promo").get("publicToken").asText();
		clock.freeze("2026-11-06T01:30:00Z"); // 22:30 do dia 5 em São Paulo

		scan(token, null, device(1));

		assertEquals(LocalDate.parse("2026-11-05").toString(), jdbc.queryForObject("SELECT day::text FROM promotion_scans", String.class));
	}
}
