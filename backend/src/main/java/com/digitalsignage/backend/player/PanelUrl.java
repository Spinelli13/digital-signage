package com.digitalsignage.backend.player;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** O endereço do painel que vai no QR Code de pareamento da TV: {@code <painel>/parear/<código>}. */
@Component
public class PanelUrl {

	private final String base;

	public PanelUrl(@Value("${app.panel-url:}") String configured, @Value("${app.cors.allowed-origins}") String allowedOrigins) {
		String chosen = configured == null || configured.isBlank() ? firstOrigin(allowedOrigins) : configured.strip();
		this.base = chosen.endsWith("/") ? chosen.substring(0, chosen.length() - 1) : chosen;
	}

	private static String firstOrigin(String origins) {
		for (String origin : origins.split(",")) {
			if (!origin.isBlank()) {
				return origin.strip();
			}
		}
		return "";
	}

	/** Endereço para a pessoa abrir com o celular; nulo quando o painel não está configurado. */
	public String pairingUrl(String activationCode) {
		return base.isEmpty() || activationCode == null ? null : base + "/parear/" + activationCode;
	}

}
