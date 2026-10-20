package com.digitalsignage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.digitalsignage.backend.device.ConfigVersions;
import com.digitalsignage.backend.layout.LayoutRequest.ZoneRequest;
import com.digitalsignage.backend.layout.LayoutTemplates;
import com.digitalsignage.backend.layout.LayoutValidator;
import com.digitalsignage.backend.media.WebContent;

/** Regras puras (sem banco): geometria das zonas, versão combinada do conteúdo e endereço de conteúdo web. */
class LayoutRulesTest {

	private static ZoneRequest main(String name, int x, int y, int w, int h) {
		return new ZoneRequest(name, x, y, w, h, true, null);
	}

	private static ZoneRequest zone(String name, int x, int y, int w, int h) {
		return new ZoneRequest(name, x, y, w, h, false, null);
	}

	private static String refusal(ZoneRequest... zones) {
		return assertThrows(IllegalArgumentException.class, () -> LayoutValidator.validate(List.of(zones))).getMessage();
	}

	// ---------- zonas ----------

	@Test
	void aSingleFullScreenZoneIsValid() {
		LayoutValidator.validate(List.of(main("Principal", 0, 0, 100, 100)));
	}

	@Test
	void zonesThatOnlyTouchAtTheEdgesAreNotOverlapping() {
		LayoutValidator.validate(List.of(main("A", 0, 0, 50, 100), zone("B", 50, 0, 50, 50), zone("C", 50, 50, 50, 50)));
	}

	@Test
	void needsExactlyOneMainZone() {
		assertEquals("Escolha exatamente uma zona principal (a que exibe a playlist da TV)", refusal(zone("A", 0, 0, 50, 100)));
		assertEquals("Escolha exatamente uma zona principal (a que exibe a playlist da TV)",
				refusal(main("A", 0, 0, 50, 100), main("B", 50, 0, 50, 100)));
		assertEquals("O layout precisa de pelo menos uma zona", assertThrows(IllegalArgumentException.class, () -> LayoutValidator.validate(List.of())).getMessage());
		assertEquals("O layout precisa de pelo menos uma zona", assertThrows(IllegalArgumentException.class, () -> LayoutValidator.validate(null)).getMessage());
	}

	@Test
	void refusesZonesOutsideTheScreenOrTooSmall() {
		assertEquals("A zona \"A\" passa do limite da tela (100%)", refusal(main("A", 60, 0, 50, 100)));
		assertEquals("A zona \"A\" passa do limite da tela (100%)", refusal(main("A", 0, 90, 100, 20)));
		assertEquals("A zona \"A\" começa fora da tela", refusal(main("A", -1, 0, 50, 50)));
		assertEquals("A zona \"A\" é pequena demais (mínimo 5% de largura e de altura)", refusal(main("A", 0, 0, 4, 50)));
		assertEquals("A zona \"A\" é pequena demais (mínimo 5% de largura e de altura)", refusal(main("A", 0, 0, 50, 0)));
	}

	@Test
	void refusesOverlappingZonesAndSaysWhich() {
		assertEquals("As zonas \"Principal\" e \"Lateral\" se sobrepõem", refusal(main("Principal", 0, 0, 80, 100), zone("Lateral", 75, 0, 25, 100)));
		assertEquals("As zonas \"A\" e \"B\" se sobrepõem", refusal(main("A", 10, 10, 50, 50), zone("B", 20, 20, 10, 10))); // uma dentro da outra
	}

	@Test
	void refusesMoreThanSixZonesAndRepeatedNames() {
		List<ZoneRequest> many = new ArrayList<>();
		many.add(main("Z0", 0, 0, 10, 10));
		for (int i = 1; i <= 6; i++) {
			many.add(zone("Z" + i, i * 10, 0, 10, 10));
		}
		assertEquals("O layout pode ter no máximo 6 zonas", assertThrows(IllegalArgumentException.class, () -> LayoutValidator.validate(many)).getMessage());
		assertEquals("Cada zona precisa de um nome diferente", refusal(main("Igual", 0, 0, 50, 100), zone(" igual ", 50, 0, 50, 100)));
	}

	@Test
	void theMainZoneCannotHaveAFixedPlaylist() {
		assertEquals("A zona \"A\" é a principal: ela exibe a playlist da TV, não uma playlist fixa",
				refusal(new ZoneRequest("A", 0, 0, 100, 100, true, 7L)));
	}

	@Test
	void everyShippedTemplateIsAValidLayout() {
		for (LayoutTemplates.Template template : LayoutTemplates.ALL) {
			LayoutValidator.validate(template.zones());
		}
		assertEquals("tela-cheia", LayoutTemplates.ALL.get(0).id());
		assertEquals(LayoutTemplates.ALL.size(), LayoutTemplates.ALL.stream().map(LayoutTemplates.Template::id).distinct().count());
	}

	// ---------- versão combinada ----------

	@Test
	void theCombinedVersionIsStableAndFitsInFiftyThreeBits() {
		List<ConfigVersions.ZoneVersion> others = List.of(new ConfigVersions.ZoneVersion(5, 3));

		long first = ConfigVersions.composite(8, 4, 9, 2, others);

		assertEquals(first, ConfigVersions.composite(8, 4, 9, 2, others));
		assertTrue(first >= 0 && first < (1L << 53));
	}

	@Test
	void anyChangeInTheContentChangesTheVersion() {
		List<ConfigVersions.ZoneVersion> others = List.of(new ConfigVersions.ZoneVersion(5, 3));
		long base = ConfigVersions.composite(8, 4, 9, 2, others);

		assertNotEquals(base, ConfigVersions.composite(8, 5, 9, 2, others)); // o conteúdo da playlist principal mudou
		assertNotEquals(base, ConfigVersions.composite(9, 4, 9, 2, others)); // outra playlist principal com a mesma versão (campanha)
		assertNotEquals(base, ConfigVersions.composite(8, 4, 10, 2, others)); // outro layout
		assertNotEquals(base, ConfigVersions.composite(8, 4, 9, 3, others)); // as zonas do layout mudaram
		assertNotEquals(base, ConfigVersions.composite(8, 4, 9, 2, List.of(new ConfigVersions.ZoneVersion(5, 4)))); // a playlist de uma zona mudou
		assertNotEquals(base, ConfigVersions.composite(8, 4, 9, 2, List.of(new ConfigVersions.ZoneVersion(6, 3)))); // outra playlist na zona
		assertNotEquals(base, ConfigVersions.composite(8, 4, 9, 2, List.of())); // zona sem playlist
	}

	// ---------- conteúdo web ----------

	private static String webRefusal(String url) {
		return assertThrows(IllegalArgumentException.class, () -> WebContent.validate(url)).getMessage();
	}

	@Test
	void acceptsHttpsAddresses() {
		assertEquals("example.com", WebContent.validate("  https://example.com/painel?x=1  ").getHost());
		assertEquals("https://example.com/painel?x=1", WebContent.validate("  https://example.com/painel?x=1  ").toString());
	}

	@Test
	void refusesAnythingButHttpsWithoutCredentials() {
		assertEquals("O endereço da página precisa começar com https://", webRefusal("http://example.com"));
		assertEquals("O endereço da página precisa começar com https://", webRefusal("javascript:alert(1)"));
		assertEquals("O endereço da página precisa começar com https://", webRefusal("file:///etc/passwd"));
		assertEquals("O endereço da página precisa começar com https://", webRefusal("example.com"));
		assertEquals("O endereço da página não pode conter usuário e senha", webRefusal("https://user:pass@example.com"));
		assertEquals("O endereço da página não é válido", webRefusal("https://"));
		assertEquals("O endereço da página não é válido", webRefusal("https://exa mple.com"));
		assertEquals("Informe o endereço da página", webRefusal("   "));
		assertEquals("Informe o endereço da página", webRefusal(null));
		assertTrue(webRefusal("https://example.com/" + "a".repeat(2000)).startsWith("O endereço é longo demais"));
	}

	@Test
	void theNameDefaultsToTheSiteAddress() {
		URI uri = WebContent.validate("https://painel.empresa.com/tv");

		assertEquals("painel.empresa.com", WebContent.nameFor(null, uri));
		assertEquals("painel.empresa.com", WebContent.nameFor("  ", uri));
		assertEquals("Painel de vendas", WebContent.nameFor("  Painel de vendas ", uri));
	}

}
