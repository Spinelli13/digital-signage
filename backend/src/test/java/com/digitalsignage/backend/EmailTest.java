package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.digitalsignage.backend.mail.Email;
import com.digitalsignage.backend.monitoring.AlertNotifier;
import com.digitalsignage.backend.monitoring.MonitoringService;

/** E-mail do sistema: recuperar senha, boas-vindas, aviso de fatura e alerta de TV offline (sem provedor, nada quebra). */
class EmailTest extends AbstractApiTest {

	private static final Pattern RESET_LINK = Pattern.compile("http://localhost:4200/redefinir-senha/([0-9a-f]{64})");

	@Autowired
	private AlertNotifier notifier;

	@Autowired
	private MonitoringService monitoring;

	private long clientWithAdmin(String name, String plan, String email) throws Exception {
		long tenant = createTenantRow(name, plan, "ACTIVE");
		createUserRow(tenant, email, "ADMIN", "senha-antiga-1");
		return tenant;
	}

	private void forgot(String address, int expected) throws Exception {
		mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", address)))).andExpect(status().is(expected));
	}

	private String tokenIn(Email email) {
		Matcher matcher = RESET_LINK.matcher(email.body());
		assertTrue(matcher.find(), email.body());
		return matcher.group(1);
	}

	private void reset(String token, String password, int expected) throws Exception {
		mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("token", token, "newPassword", password)))).andExpect(status().is(expected));
	}

	private void loginExpecting(String address, String password, int expected) throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", address, "password", password)))).andExpect(status().is(expected));
	}

	// ---------- recuperar a senha ----------

	@Test
	void forgettingThePasswordSendsALinkThatSetsANewOneOnlyOnce() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		clientWithAdmin("Cliente Esquecido", "interno", "dono@esquecido.com");

		forgot("dono@esquecido.com", 204);

		Email email = mail.lastTo("dono@esquecido.com");
		assertNotNull(email);
		assertTrue(email.subject().startsWith("Redefinir a sua senha"), email.subject());
		assertTrue(email.body().contains("vale por 1 hora"), email.body());
		assertFalse(email.body().contains("senha-antiga-1"), "o e-mail nunca leva senha");
		String token = tokenIn(email);

		reset(token, "senha-nova-123", 204);
		loginExpecting("dono@esquecido.com", "senha-nova-123", 200);
		loginExpecting("dono@esquecido.com", "senha-antiga-1", 401);
		reset(token, "outra-senha-456", 400); // o link só vale uma vez
		loginExpecting("dono@esquecido.com", "senha-nova-123", 200);
	}

	@Test
	void theLinkExpiresAfterAnHourAndAWeakPasswordIsRefusedWithoutBurningIt() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		clientWithAdmin("Cliente Lento", "interno", "dono@lento.com");
		forgot("dono@lento.com", 204);
		String token = tokenIn(mail.lastTo("dono@lento.com"));

		reset(token, "curta", 400); // fraca demais: o link continua valendo
		clock.freeze("2026-11-05T15:59:00Z");
		reset(token, "senha-nova-123", 204);

		forgot("dono@lento.com", 204);
		String second = tokenIn(mail.lastTo("dono@lento.com"));
		clock.freeze("2026-11-05T17:00:01Z"); // mais de uma hora depois do pedido (16:00)
		reset(second, "senha-que-nao-vale", 400);
		loginExpecting("dono@lento.com", "senha-nova-123", 200);
	}

	@Test
	void whoeverAsksNeverLearnsIfTheEmailHasAnAccount() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		clientWithAdmin("Cliente Real", "interno", "real@cliente.com");

		forgot("real@cliente.com", 204);
		forgot("ninguem@lugar-nenhum.com", 204);
		forgot("isto nao e um e-mail", 204);

		assertEquals(1, mail.sent().size(), "só o endereço que tem conta recebe");
		assertNull(mail.lastTo("ninguem@lugar-nenhum.com"));
		mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
	}

	@Test
	void requestsAreSpacedOutSoNobodyFloodsAnotherPersonsInbox() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		clientWithAdmin("Cliente Alvo", "interno", "alvo@cliente.com");

		forgot("alvo@cliente.com", 204);
		forgot("alvo@cliente.com", 204); // 2 minutos de intervalo
		assertEquals(1, mail.sent().size());
		clock.freeze("2026-11-05T15:03:00Z");
		forgot("alvo@cliente.com", 204);
		assertEquals(2, mail.sent().size());

		int minute = 6;
		for (int i = 0; i < 6; i++) { // no máximo 5 por hora
			clock.freeze(String.format("2026-11-05T15:%02d:00Z", minute));
			forgot("alvo@cliente.com", 204);
			minute += 3;
		}
		assertEquals(5, mail.sent().size());
	}

	@Test
	void withoutAnEmailProviderTheRequestDoesNothingAndNothingBreaks() throws Exception {
		clientWithAdmin("Cliente Sem Provedor", "interno", "dono@semprovedor.com");
		mail.enabled(false);

		forgot("dono@semprovedor.com", 204);

		assertTrue(mail.sent().isEmpty());
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM password_resets", Integer.class));
	}

	@Test
	void aBrokenProviderNeverBreaksThePasswordRequest() throws Exception {
		clientWithAdmin("Cliente Provedor Ruim", "interno", "dono@ruim.com");
		mail.failing(true);

		forgot("dono@ruim.com", 204);

		assertTrue(mail.sent().isEmpty());
	}

	@Test
	void theLinkIsStoredOnlyAsAHashAndAUnknownTokenIsRefused() throws Exception {
		clientWithAdmin("Cliente Hash", "interno", "dono@hash.com");
		forgot("dono@hash.com", 204);
		String token = tokenIn(mail.lastTo("dono@hash.com"));

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM password_resets WHERE token_hash = ?", Integer.class, token));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM password_resets WHERE length(token_hash) = 64", Integer.class));
		reset("0".repeat(64), "senha-nova-123", 400);
		reset("   ", "senha-nova-123", 400);
	}

	// ---------- boas-vindas ----------

	@Test
	void aNewTeamMemberGetsAWelcomeWithALinkToChooseThePassword() throws Exception {
		clock.freeze("2026-11-05T15:00:00Z");
		mvc.perform(asAdmin(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
				Map.of("name", "Maria", "email", "maria@cliente.com", "password", "provisoria-1", "role", "EDITOR")))))
				.andExpect(status().isCreated());

		Email email = mail.lastTo("maria@cliente.com");
		assertNotNull(email);
		assertTrue(email.subject().startsWith("Bem-vindo"), email.subject());
		assertTrue(email.body().contains("Olá, Maria."), email.body());
		assertTrue(email.body().contains("vale por 3 dias"), email.body());
		assertFalse(email.body().contains("provisoria-1"), "a senha provisória nunca vai por e-mail");

		clock.freeze("2026-11-08T14:00:00Z"); // 2 dias e 23 h depois
		reset(tokenIn(email), "minha-senha-nova", 204);
		loginExpecting("maria@cliente.com", "minha-senha-nova", 200);
	}

	@Test
	void aNewClientCreatedByThePlatformWelcomesItsAdministrator() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"name", "Padaria Nova", "planId", planId("basico"), "adminName", "Joana", "adminEmail", "joana@padaria.com", "adminPassword", "provisoria-1")))))
				.andExpect(status().isCreated());

		Email email = mail.lastTo("joana@padaria.com");
		assertNotNull(email);
		assertTrue(email.body().contains("para Padaria Nova"), email.body());
	}

	private long planId(String code) {
		return jdbc.queryForObject("SELECT id FROM plans WHERE code = ?", Long.class, code);
	}

	// ---------- fatura ----------

	@Test
	void anInvoiceIsAnnouncedToTheBillingEmailWithValueDueDateAndHowToPay() throws Exception {
		long tenant = clientWithAdmin("Cliente Pagante", "basico", "dono@pagante.com");
		jdbc.update("UPDATE tenants SET billing_email = 'financeiro@pagante.com' WHERE id = ?", tenant);
		jdbc.update("UPDATE platform_settings SET payment_instructions = 'Pix: pagamento@vyzzu.com', support_email = 'suporte@vyzzu.com'");

		mvc.perform(asAdmin(post("/api/v1/platform/tenants/" + tenant + "/invoices").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"description", "Instalação e configuração", "amountCents", 15050, "dueDate", "2026-11-20")))))
				.andExpect(status().isCreated());

		Email email = mail.lastTo("financeiro@pagante.com");
		assertNotNull(email);
		assertNull(mail.lastTo("dono@pagante.com"), "com e-mail de cobrança, só ele recebe");
		assertTrue(email.subject().contains("20/11/2026"), email.subject());
		assertTrue(email.body().contains("Valor: R$ 150,50"), email.body());
		assertTrue(email.body().contains("Descrição: Instalação e configuração"), email.body());
		assertTrue(email.body().contains("Pix: pagamento@vyzzu.com"), email.body());
		assertTrue(email.body().contains("suporte@vyzzu.com"), email.body());
	}

	@Test
	void withoutABillingEmailTheClientAdministratorsReceiveTheInvoice() throws Exception {
		long tenant = clientWithAdmin("Cliente Sem Financeiro", "basico", "dono@semfin.com");
		createUserRow(tenant, "editor@semfin.com", "EDITOR", "senha-antiga-1");

		mvc.perform(asAdmin(post("/api/v1/platform/tenants/" + tenant + "/invoices").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"description", "Acerto", "amountCents", 4900, "dueDate", "2026-11-20"))))).andExpect(status().isCreated());

		assertNotNull(mail.lastTo("dono@semfin.com"));
		assertNull(mail.lastTo("editor@semfin.com"), "quem não administra não recebe a fatura");
	}

	@Test
	void aFreeInvoiceSendsNothingAndAProviderFailureNeverBlocksTheInvoice() throws Exception {
		long tenant = clientWithAdmin("Cliente Falha", "basico", "dono@falha.com");
		mail.failing(true);

		mvc.perform(asAdmin(post("/api/v1/platform/tenants/" + tenant + "/invoices").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
				"description", "Acerto", "amountCents", 4900, "dueDate", "2026-11-20"))))).andExpect(status().isCreated());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM invoices WHERE tenant_id = ?", Integer.class, tenant));
		assertTrue(mail.sent().isEmpty());
	}

	// ---------- TV offline ----------

	private String activeTv(String name) throws Exception {
		String token = UUID.randomUUID().toString().replace("-", "") + "00";
		String registered = mvc.perform(post("/api/v1/player/register").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("deviceToken", token, "playerVersion", "0.4.0")))).andReturn().getResponse().getContentAsString();
		String code = json.readTree(registered).get("activationCode").asText();
		mvc.perform(asAdmin(post("/api/v1/devices/activate").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("activationCode", code, "name", name))))).andExpect(status().isOk());
		return token;
	}

	private void heartbeat(String token) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("playerVersion", "0.4.0", "configVersion", 0));
		mvc.perform(post("/api/v1/player/heartbeat").header("X-Device-Token", token).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body))).andExpect(status().isOk());
	}

	private void setAlertEmail(String addresses) throws Exception {
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("alertEmail", addresses)))))
				.andExpect(status().isOk());
	}

	private String goOffline(String name) throws Exception {
		clock.freeze("2026-11-05T12:00:00Z");
		String token = activeTv(name);
		heartbeat(token);
		clock.freeze("2026-11-05T12:30:00Z");
		inTenant(DEFAULT_TENANT, () -> monitoring.evaluate(clock.instant()));
		return token;
	}

	@Test
	void aTvGoingOfflineEmailsTheConfiguredAddressesEvenWithoutAWebhook() throws Exception {
		setAlertEmail("dono@loja.com, gerente@loja.com");
		goOffline("TV Vitrine");

		int sent = inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));

		assertEquals(1, sent);
		Email email = mail.lastTo("gerente@loja.com");
		assertNotNull(email);
		assertNotNull(mail.lastTo("dono@loja.com"));
		assertTrue(email.subject().startsWith("Alerta: "), email.subject());
		assertTrue(email.body().contains("TV Vitrine"), email.body());
		// o mesmo alerta não é avisado duas vezes
		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));
		assertEquals(2, mail.sent().size());
	}

	@Test
	void theResolutionEmailFollowsTheNotifyOnResolveSetting() throws Exception {
		setAlertEmail("dono@loja.com");
		String token = goOffline("TV");
		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));
		clock.freeze("2026-11-05T12:35:00Z");
		heartbeat(token); // a TV voltou
		inTenant(DEFAULT_TENANT, () -> monitoring.evaluate(clock.instant()));
		mail.sent().clear();

		inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()));

		assertEquals(1, mail.sent().size());
		assertTrue(mail.sent().get(0).subject().startsWith("Resolvido: "), mail.sent().get(0).subject());
	}

	@Test
	void theAlertAddressesAreValidatedAndOnlyShownBackAsTheyWereSaved() throws Exception {
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"alertEmail\":\"nao-e-email\"}")))
				.andExpect(status().isBadRequest());
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"alertEmail\":\"a@x.com,b@x.com,c@x.com,d@x.com\"}")))
				.andExpect(status().isBadRequest());

		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"alertEmail\":\"a@x.com ; B@x.com a@x.com\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.alertEmail").value("a@x.com, B@x.com")).andExpect(jsonPath("$.emailAvailable").value(true));
		mvc.perform(asAdmin(put("/api/v1/alerts/settings").contentType(MediaType.APPLICATION_JSON).content("{\"alertEmail\":\"\"}")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.alertEmail").doesNotExist());
		mail.enabled(false);
		mvc.perform(asAdmin(get("/api/v1/alerts/settings"))).andExpect(jsonPath("$.emailAvailable").value(false));
	}

	@Test
	void theTestEmailReachesTheAddressesOrExplainsWhatIsMissing() throws Exception {
		mvc.perform(asAdmin(post("/api/v1/alerts/settings/test-email"))).andExpect(status().isBadRequest()); // sem endereço
		setAlertEmail("dono@loja.com");
		mvc.perform(asAdmin(post("/api/v1/alerts/settings/test-email"))).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
		assertNotNull(mail.lastTo("dono@loja.com"));
		assertTrue(mail.lastTo("dono@loja.com").subject().startsWith("Teste de e-mail"));

		mail.enabled(false);
		mvc.perform(asAdmin(post("/api/v1/alerts/settings/test-email"))).andExpect(status().isBadRequest());
	}

	@Test
	void withoutAProviderTheAlertsStayOpenForNotificationInsteadOfBeingLost() throws Exception {
		setAlertEmail("dono@loja.com");
		mail.enabled(false);
		goOffline("TV Vitrine");

		assertEquals(0, inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant())));

		mail.enabled(true);
		assertEquals(1, inTenant(DEFAULT_TENANT, () -> notifier.notifyPending(clock.instant()))); // configurou depois: avisa o que ainda está aberto
	}
}
