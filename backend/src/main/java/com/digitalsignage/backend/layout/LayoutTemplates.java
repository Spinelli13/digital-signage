package com.digitalsignage.backend.layout;

import java.util.List;
import java.util.Optional;

/** Modelos prontos de layout: o administrador escolhe um e ajusta, em vez de calcular as zonas na mão. */
public final class LayoutTemplates {

	public record Template(String id, String name, String description, List<LayoutRequest.ZoneRequest> zones) {
	}

	private static LayoutRequest.ZoneRequest main(String name, int x, int y, int width, int height) {
		return new LayoutRequest.ZoneRequest(name, x, y, width, height, true, null);
	}

	private static LayoutRequest.ZoneRequest zone(String name, int x, int y, int width, int height) {
		return new LayoutRequest.ZoneRequest(name, x, y, width, height, false, null);
	}

	public static final List<Template> ALL = List.of(
			new Template("tela-cheia", "Tela cheia",
					"Um único conteúdo ocupando a tela toda (o comportamento de sempre).",
					List.of(main("Principal", 0, 0, 100, 100))),
			new Template("principal-lateral", "Principal + faixa lateral",
					"Conteúdo principal à esquerda e uma faixa à direita (avisos, promoções, QR Code).",
					List.of(main("Principal", 0, 0, 75, 100), zone("Lateral", 75, 0, 25, 100))),
			new Template("principal-rodape", "Principal + rodapé",
					"Conteúdo principal em cima e uma faixa embaixo (avisos, horários, notícias).",
					List.of(main("Principal", 0, 0, 100, 85), zone("Rodapé", 0, 85, 100, 15))),
			new Template("principal-lateral-rodape", "Principal + lateral + rodapé",
					"Conteúdo principal, uma faixa à direita e um rodapé.",
					List.of(main("Principal", 0, 0, 75, 85), zone("Lateral", 75, 0, 25, 85), zone("Rodapé", 0, 85, 100, 15))),
			new Template("duas-metades", "Duas metades",
					"A tela dividida ao meio: dois conteúdos lado a lado.",
					List.of(main("Esquerda", 0, 0, 50, 100), zone("Direita", 50, 0, 50, 100))),
			new Template("quatro-quadrantes", "Quatro quadros",
					"Quatro conteúdos iguais (bom para painéis de informação).",
					List.of(main("Superior esquerdo", 0, 0, 50, 50), zone("Superior direito", 50, 0, 50, 50),
							zone("Inferior esquerdo", 0, 50, 50, 50), zone("Inferior direito", 50, 50, 50, 50))));

	private LayoutTemplates() {
	}

	public static Optional<Template> find(String id) {
		return ALL.stream().filter(template -> template.id().equals(id)).findFirst();
	}

}
