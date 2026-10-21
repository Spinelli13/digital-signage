package com.digitalsignage.backend.plan;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Os recursos que um plano pode incluir ou não. O que não está aqui (TVs, mídias, playlists, locais, grupos, alertas e
 * equipe) faz parte de qualquer plano e só tem limite de quantidade.
 *
 * <p>Cada recurso conhece os endereços do painel que ele protege. Quando {@code readsStayOpen} é verdadeiro, só gravar é
 * barrado: consultar continua liberado porque outras telas dependem dessa lista (ex.: escolher o layout de uma TV).
 * Nada que já está em uso é desligado por causa do plano: o Player segue exibindo, só o painel deixa de oferecer o recurso.
 */
public enum Feature {

	CAMPAIGNS("Campanhas", true, "/api/v1/campaigns"),
	LAYOUTS("Layouts com zonas", true, "/api/v1/layouts"),
	WIDGETS("Widgets (conteúdo dinâmico)", false, "/api/v1/widgets"),
	PRICES("Tabelas de preços", true, "/api/v1/price-lists"),
	QUEUE("Fila de atendimento", false, "/api/v1/attendance", "/api/v1/queues", "/api/v1/counters"),
	PROOF_OF_PLAY("Comprovação de exibição", false, "/api/v1/reports/proof");

	private final String label;
	private final boolean readsStayOpen;
	private final List<String> prefixes;

	Feature(String label, boolean readsStayOpen, String... prefixes) {
		this.label = label;
		this.readsStayOpen = readsStayOpen;
		this.prefixes = List.of(prefixes);
	}

	public String label() {
		return label;
	}

	public boolean readsStayOpen() {
		return readsStayOpen;
	}

	/** O recurso que protege este endereço (ou nulo se o endereço não é de um recurso opcional). */
	public static Feature forPath(String path) {
		for (Feature feature : values()) {
			for (String prefix : feature.prefixes) {
				if (path.equals(prefix) || path.startsWith(prefix + "/")) {
					return feature;
				}
			}
		}
		return null;
	}

	/** "CAMPAIGNS,QUEUE" vira o conjunto; códigos que não existem mais são ignorados. */
	public static Set<Feature> parse(String stored) {
		Set<Feature> result = EnumSet.noneOf(Feature.class);
		if (stored != null) {
			for (String code : stored.split(",")) {
				String trimmed = code.trim();
				if (!trimmed.isEmpty()) {
					try {
						result.add(valueOf(trimmed));
					} catch (IllegalArgumentException ignored) {
						// recurso removido em uma versão nova: não vale mais
					}
				}
			}
		}
		return result;
	}

	public static String join(Collection<Feature> features) {
		return features.stream().sorted().map(Feature::name).collect(Collectors.joining(","));
	}

	public static Set<Feature> all() {
		return EnumSet.allOf(Feature.class);
	}

	public static String joinAll() {
		return join(Arrays.asList(values()));
	}

}
