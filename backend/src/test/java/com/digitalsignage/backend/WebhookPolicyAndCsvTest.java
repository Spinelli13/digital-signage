package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.digitalsignage.backend.monitoring.CsvWriter;
import com.digitalsignage.backend.monitoring.WebhookUrlPolicy;

class WebhookPolicyAndCsvTest {

	private final WebhookUrlPolicy strict = new WebhookUrlPolicy(false);
	private final WebhookUrlPolicy lenient = new WebhookUrlPolicy(true);

	private String refusal(WebhookUrlPolicy policy, String url) {
		return assertThrows(IllegalArgumentException.class, () -> policy.validate(url)).getMessage();
	}

	// ---------- política do endereço do webhook ----------

	@Test
	void productionOnlyAcceptsHttps() {
		assertEquals("O endereço do webhook precisa começar com https://", refusal(strict, "http://hooks.example.com/abc"));
		assertEquals("O endereço do webhook precisa começar com https://", refusal(strict, "ftp://hooks.example.com/abc"));
		assertEquals("O endereço do webhook precisa começar com https://", refusal(strict, "hooks.example.com/abc"));
	}

	@Test
	void rejectsBlankAndMalformedAddresses() {
		assertEquals("Informe o endereço do webhook", refusal(strict, "  "));
		assertEquals("Informe o endereço do webhook", refusal(strict, null));
		assertEquals("O endereço do webhook não é válido", refusal(strict, "https://"));
		assertEquals("O endereço do webhook não é válido", refusal(strict, "https://exa mple.com/x"));
	}

	@Test
	void rejectsCredentialsInTheAddress() {
		assertEquals("O endereço do webhook não pode conter usuário e senha", refusal(strict, "https://user:pass@1.1.1.1/x"));
	}

	/** Proteção contra SSRF: o servidor não pode ser levado a chamar a rede interna. */
	@Test
	void rejectsInternalAddresses() {
		for (String host : new String[] { "127.0.0.1", "localhost", "10.0.0.5", "192.168.1.10", "172.16.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "[::1]" }) {
			assertEquals("O endereço do webhook não pode apontar para a rede interna", refusal(strict, "https://" + host + "/hook"), host);
		}
	}

	@Test
	void acceptsAPublicAddress() {
		assertEquals("1.1.1.1", strict.validate("https://1.1.1.1/hook").getHost());
	}

	@Test
	void theTestPolicyAllowsPlainHttpAndLocalAddresses() {
		assertEquals("localhost", lenient.validate("http://localhost:8099/hook").getHost());
		assertEquals("O endereço do webhook precisa começar com https://", refusal(lenient, "ftp://localhost/x"));
	}

	@Test
	void theHintNeverRevealsTheWholeSecretAddress() {
		String hint = WebhookUrlPolicy.hint("https://hooks.slack.com/services/T000/B000/SECRETSECRET1234");

		assertEquals("hooks.slack.com/…1234", hint);
		assertTrue(!hint.contains("SECRET"));
		assertNull(WebhookUrlPolicy.hint(null));
		assertNull(WebhookUrlPolicy.hint("  "));
	}

	// ---------- CSV ----------

	private static String csv(CsvWriter writer) {
		return new String(writer.bytes(), StandardCharsets.UTF_8);
	}

	@Test
	void startsWithTheBomAndUsesSemicolonsAndCrlf() {
		String text = csv(new CsvWriter().row("TV", "Local").row("Recepção", "Centro"));

		assertEquals("﻿TV;Local\r\nRecepção;Centro\r\n", text);
	}

	@Test
	void quotesCellsWithSeparatorsQuotesAndLineBreaks() {
		assertEquals("\"a;b\"", csv(new CsvWriter().row("a;b")).substring(1).trim());
		assertEquals("\"diz \"\"oi\"\"\"", csv(new CsvWriter().row("diz \"oi\"")).substring(1).trim());
		assertEquals("\"linha1\nlinha2\"", csv(new CsvWriter().row("linha1\nlinha2")).substring(1).replace("\r\n", ""));
	}

	@Test
	void emptyCellsAreEmptyAndNumbersAreWrittenAsTheyAre() {
		assertEquals("a;;3;4.5", csv(new CsvWriter().row("a", null, 3, 4.5)).substring(1).trim());
	}

	/** Injeção de CSV: o Excel executaria uma célula que começa com = como fórmula. */
	@Test
	void neutralisesCellsThatCouldBeExecutedAsFormulas() {
		String text = csv(new CsvWriter().row("=1+1", "+cmd", "-2", "@SUM(A1)", "normal")).substring(1).trim();

		assertEquals("'=1+1;'+cmd;'-2;'@SUM(A1);normal", text);
	}

}
