package com.digitalsignage.backend.layout;

import java.util.List;
import java.util.Objects;

/** Regras de um layout, sem banco (testadas à parte): zonas dentro da tela, sem sobreposição, uma única principal. */
public final class LayoutValidator {

	public static final int MAX_ZONES = 6;
	public static final int MIN_SIZE = 5;

	private LayoutValidator() {
	}

	public static void validate(List<LayoutRequest.ZoneRequest> zones) {
		if (zones == null || zones.isEmpty()) {
			throw new IllegalArgumentException("O layout precisa de pelo menos uma zona");
		}
		if (zones.size() > MAX_ZONES) {
			throw new IllegalArgumentException("O layout pode ter no máximo " + MAX_ZONES + " zonas");
		}

		long mains = zones.stream().filter(LayoutRequest.ZoneRequest::main).count();
		if (mains != 1) {
			throw new IllegalArgumentException("Escolha exatamente uma zona principal (a que exibe a playlist da TV)");
		}

		for (LayoutRequest.ZoneRequest zone : zones) {
			String label = "A zona \"" + zone.name().trim() + "\"";
			if (zone.x() < 0 || zone.y() < 0) {
				throw new IllegalArgumentException(label + " começa fora da tela");
			}
			if (zone.width() < MIN_SIZE || zone.height() < MIN_SIZE) {
				throw new IllegalArgumentException(label + " é pequena demais (mínimo " + MIN_SIZE + "% de largura e de altura)");
			}
			if (zone.x() + zone.width() > 100 || zone.y() + zone.height() > 100) {
				throw new IllegalArgumentException(label + " passa do limite da tela (100%)");
			}
			if (zone.main() && zone.playlistId() != null) {
				throw new IllegalArgumentException(label + " é a principal: ela exibe a playlist da TV, não uma playlist fixa");
			}
			String fit = zone.fit();
			if (fit != null && !fit.isBlank() && !LayoutZone.FIT_CONTAIN.equals(fit) && !LayoutZone.FIT_COVER.equals(fit)) {
				throw new IllegalArgumentException(label + " tem um ajuste de mídia desconhecido (use CONTAIN ou COVER)");
			}
		}

		for (int i = 0; i < zones.size(); i++) {
			for (int j = i + 1; j < zones.size(); j++) {
				if (overlap(zones.get(i), zones.get(j))) {
					throw new IllegalArgumentException("As zonas \"" + zones.get(i).name().trim() + "\" e \""
							+ zones.get(j).name().trim() + "\" se sobrepõem");
				}
			}
		}

		long distinctNames = zones.stream().map(zone -> zone.name().trim().toLowerCase()).distinct().count();
		if (distinctNames != zones.size()) {
			throw new IllegalArgumentException("Cada zona precisa de um nome diferente");
		}
	}

	/** Retângulos que só se tocam nas bordas não se sobrepõem. */
	static boolean overlap(LayoutRequest.ZoneRequest a, LayoutRequest.ZoneRequest b) {
		Objects.requireNonNull(a);
		Objects.requireNonNull(b);
		return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
				&& a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
	}

}
