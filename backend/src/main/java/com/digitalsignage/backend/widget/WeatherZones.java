package com.digitalsignage.backend.widget;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * O fuso horário de uma cidade da previsão. O MET Norway entrega a hora em UTC, e os dias da previsão precisam ser os dias
 * do lugar da TV. A busca de cidades já guarda o fuso no widget; para os widgets antigos (sem fuso) vale uma estimativa
 * pela posição, que erra no máximo uma hora na divisão entre dias.
 */
final class WeatherZones {

	private WeatherZones() {
	}

	/** O fuso guardado no widget, ou a estimativa pela posição quando não há. */
	static ZoneId of(Object configured, double latitude, double longitude) {
		if (configured instanceof String id && !id.isBlank()) {
			try {
				return ZoneId.of(id);
			} catch (DateTimeException e) {
				// fuso inválido guardado: cai na estimativa
			}
		}
		return ZoneId.of(approximate(latitude, longitude));
	}

	/**
	 * O fuso de um lugar achado na busca. No Brasil vale o estado (o Brasil não tem horário de verão desde 2019); fora dele,
	 * a estimativa pela longitude.
	 */
	static String forPlace(String countryCode, String subdivision, double latitude, double longitude) {
		if ("br".equalsIgnoreCase(countryCode) && subdivision != null) {
			String state = subdivision.toUpperCase(java.util.Locale.ROOT).replace("BR-", "");
			switch (state) {
				case "AC":
					return "America/Rio_Branco";
				case "AM":
					return longitude < -66.5 ? "America/Eirunepe" : "America/Manaus";
				case "RO":
					return "America/Porto_Velho";
				case "RR":
					return "America/Boa_Vista";
				case "MT":
					return "America/Cuiaba";
				case "MS":
					return "America/Campo_Grande";
				default:
					return "America/Sao_Paulo";
			}
		}
		return approximate(latitude, longitude);
	}

	/** Sem o estado: no Brasil pelas faixas de longitude; no resto do mundo, uma hora por 15 graus (sem horário de verão). */
	static String approximate(double latitude, double longitude) {
		boolean brazil = latitude >= -34 && latitude <= 6 && longitude >= -74 && longitude <= -34;
		if (brazil) {
			if (longitude < -66.5) {
				return "America/Rio_Branco";
			}
			if (longitude < -54.7 && latitude > -25) {
				return "America/Manaus";
			}
			return "America/Sao_Paulo";
		}
		int hours = (int) Math.round(longitude / 15.0);
		if (hours == 0) {
			return "UTC";
		}
		// "Etc/GMT-3" é UTC+3: o sinal do nome é invertido
		return "Etc/GMT" + (hours > 0 ? "-" : "+") + Math.abs(hours);
	}

}
