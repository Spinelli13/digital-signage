package com.digitalsignage.backend.layout;

import java.util.List;
import java.util.Optional;

/** Modelos prontos de layout: o administrador escolhe um e ajusta, em vez de calcular as zonas na mão. */
public final class LayoutTemplates {

	/** {@code portraitZones}: a sugestão para TV em pé (vazio = o modelo serve como está nas duas orientações). */
	public record Template(String id, String name, String description, List<LayoutRequest.ZoneRequest> zones,
			List<LayoutRequest.ZoneRequest> portraitZones) {

		public Template(String id, String name, String description, List<LayoutRequest.ZoneRequest> zones) {
			this(id, name, description, zones, List.of());
		}
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
					"Conteúdo principal à esquerda e uma faixa à direita (avisos, promoções, QR Code). Em TV em pé, a faixa vai para baixo.",
					List.of(main("Principal", 0, 0, 75, 100), zone("Lateral", 75, 0, 25, 100)),
					List.of(main("Principal", 0, 0, 100, 75), zone("Lateral", 0, 75, 100, 25))),
			new Template("principal-rodape", "Principal + rodapé",
					"Conteúdo principal em cima e uma faixa embaixo (avisos, horários, notícias). Serve também para TV em pé.",
					List.of(main("Principal", 0, 0, 100, 85), zone("Rodapé", 0, 85, 100, 15)),
					List.of(main("Principal", 0, 0, 100, 88), zone("Rodapé", 0, 88, 100, 12))),
			new Template("principal-lateral-rodape", "Principal + lateral + rodapé",
					"Conteúdo principal, uma faixa à direita e um rodapé. Em TV em pé, a faixa lateral vira uma faixa acima do rodapé.",
					List.of(main("Principal", 0, 0, 75, 85), zone("Lateral", 75, 0, 25, 85), zone("Rodapé", 0, 85, 100, 15)),
					List.of(main("Principal", 0, 0, 100, 65), zone("Lateral", 0, 65, 100, 20), zone("Rodapé", 0, 85, 100, 15))),
			new Template("duas-metades", "Duas metades",
					"A tela dividida ao meio: dois conteúdos lado a lado (em TV em pé, um em cima do outro).",
					List.of(main("Esquerda", 0, 0, 50, 100), zone("Direita", 50, 0, 50, 100)),
					List.of(main("Esquerda", 0, 0, 100, 50), zone("Direita", 0, 50, 100, 50))),
			new Template("totem-em-pe", "Totem em pé: topo, principal e rodapé",
					"Para TV em pé (totem): uma faixa no topo, o conteúdo principal no meio e um rodapé. Na horizontal, as três faixas ficam empilhadas do mesmo jeito.",
					List.of(zone("Topo", 0, 0, 100, 15), main("Principal", 0, 15, 100, 70), zone("Rodapé", 0, 85, 100, 15)),
					List.of(zone("Topo", 0, 0, 100, 10), main("Principal", 0, 10, 100, 75), zone("Rodapé", 0, 85, 100, 15))),
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
