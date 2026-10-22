package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.digitalsignage.backend.mail.ApiEmailSender;
import com.digitalsignage.backend.mail.ApiEmailSender.Provider;
import com.digitalsignage.backend.mail.Email;
import com.digitalsignage.backend.mail.EmailSender;
import com.digitalsignage.backend.mail.MailConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/** O envio pela API HTTPS dos provedores, contra um servidor local de mentira (nada sai de verdade). */
class ApiEmailSenderTest {

	private final ObjectMapper json = new ObjectMapper();
	private HttpServer server;
	private final AtomicReference<String> body = new AtomicReference<>();
	private final AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();
	private volatile int status = 200;
	private volatile String answer = "{\"id\":\"1\"}";

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/send", exchange -> {
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			headers.set(exchange.getRequestHeaders());
			byte[] reply = answer.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, reply.length);
			exchange.getResponseBody().write(reply);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/send";
	}

	@Test
	void resendGetsTheBearerKeyAndTheFromAsWritten() throws IOException {
		ApiEmailSender sender = new ApiEmailSender(Provider.RESEND, url(), "re_chave-secreta", "Vyzzu <avisos@empresa.com>");

		sender.send(new Email("ana@loja.com", "Nova senha", "Olá, Ana.\nLink: https://painel/x"));

		JsonNode sent = json.readTree(body.get());
		assertEquals("Vyzzu <avisos@empresa.com>", sent.get("from").asText());
		assertEquals("ana@loja.com", sent.get("to").get(0).asText());
		assertEquals("Nova senha", sent.get("subject").asText());
		assertEquals("Olá, Ana.\nLink: https://painel/x", sent.get("text").asText());
		assertEquals("Bearer re_chave-secreta", headers.get().getFirst("Authorization"));
		assertTrue(headers.get().getFirst("User-Agent").startsWith("Vyzzu"));
	}

	@Test
	void brevoSplitsTheSenderAndUsesTheApiKeyHeader() throws IOException {
		ApiEmailSender sender = new ApiEmailSender(Provider.BREVO, url(), "xkeysib-segredo", "\"Vyzzu\" <avisos@empresa.com>");

		sender.send(new Email("ana@loja.com", "Fatura", "Sua fatura venceu."));

		JsonNode sent = json.readTree(body.get());
		assertEquals("Vyzzu", sent.get("sender").get("name").asText());
		assertEquals("avisos@empresa.com", sent.get("sender").get("email").asText());
		assertEquals("ana@loja.com", sent.get("to").get(0).get("email").asText());
		assertEquals("Fatura", sent.get("subject").asText());
		assertEquals("Sua fatura venceu.", sent.get("textContent").asText());
		assertEquals("xkeysib-segredo", headers.get().getFirst("api-key"));
		assertNull(headers.get().getFirst("Authorization"));
	}

	@Test
	void brevoAcceptsAPlainAddressAsTheSender() throws IOException {
		new ApiEmailSender(Provider.BREVO, url(), "chave", "avisos@empresa.com").send(new Email("ana@loja.com", "Oi", "Texto"));

		JsonNode origin = json.readTree(body.get()).get("sender");
		assertEquals("avisos@empresa.com", origin.get("email").asText());
		assertFalse(origin.has("name"));
	}

	@Test
	void aRefusalBecomesAnErrorWithoutTheKeyInIt() {
		status = 401;
		answer = "{\"message\":\"API key is invalid\"}";
		ApiEmailSender sender = new ApiEmailSender(Provider.RESEND, url(), "re_chave-secreta", "avisos@empresa.com");

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> sender.send(new Email("ana@loja.com", "Oi", "Texto")));

		assertTrue(error.getMessage().contains("HTTP 401"));
		assertTrue(error.getMessage().contains("API key is invalid"));
		assertFalse(error.getMessage().contains("re_chave-secreta"));
	}

	@Test
	void anUnreachableProviderIsAnErrorToo() {
		server.stop(0);
		ApiEmailSender sender = new ApiEmailSender(Provider.BREVO, url(), "chave", "avisos@empresa.com");

		assertThrows(IllegalStateException.class, () -> sender.send(new Email("ana@loja.com", "Oi", "Texto")));
	}

	@Test
	void theProviderIsChosenByName() {
		assertEquals(Provider.RESEND, Provider.of(" Resend "));
		assertEquals(Provider.BREVO, Provider.of("BREVO"));
		assertNull(Provider.of("sendgrid"));
		assertNull(Provider.of(null));
	}

	@Test
	void configurationEnablesOnlyWhatIsComplete() {
		MailConfig config = new MailConfig();
		// sem remetente nada sai, mesmo com chave
		assertFalse(sender(config, "", "", "resend", "chave").enabled());
		// a API com remetente e chave liga
		assertTrue(sender(config, "", "avisos@empresa.com", "brevo", "chave").enabled());
		// nome de provedor desconhecido ou chave vazia: desligado (e o servidor sobe do mesmo jeito)
		assertFalse(sender(config, "", "avisos@empresa.com", "sendgrid", "chave").enabled());
		assertFalse(sender(config, "", "avisos@empresa.com", "resend", "").enabled());
		// SMTP continua valendo sem a API; sem host, desligado
		assertTrue(sender(config, "smtp.exemplo.com", "avisos@empresa.com", "", "").enabled());
		assertFalse(sender(config, "", "avisos@empresa.com", "", "").enabled());
	}

	private static EmailSender sender(MailConfig config, String host, String from, String api, String key) {
		return config.emailSender(host, 587, "", "", from, true, api, key);
	}
}
