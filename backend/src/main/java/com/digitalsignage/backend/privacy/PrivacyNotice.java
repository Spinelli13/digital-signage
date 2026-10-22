package com.digitalsignage.backend.privacy;

/**
 * O aviso de privacidade mostrado a quem informa o nome nas páginas públicas (senha da fila e cupom). A versão vai junto do
 * registro de concordância: se o texto do aviso mudar, sobe a versão e dá para saber qual texto cada pessoa viu.
 */
public final class PrivacyNotice {

	/** Muda sempre que o texto do aviso (ou da Política de Privacidade) muda. */
	public static final String VERSION = "2026-10-22.3";

	private PrivacyNotice() {
	}

	/** O que a página pública mostra no aviso: quem é o responsável, o canal de contato e por quantos dias o nome fica. */
	public record Info(String controllerName, String contactEmail, int retentionDays, String version) {
	}

}
