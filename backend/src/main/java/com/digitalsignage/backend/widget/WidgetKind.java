package com.digitalsignage.backend.widget;

/** Os tipos de widget que o Player sabe desenhar. O nome (CLOCK...) é o que fica no banco e vai para a TV. */
public enum WidgetKind {

	CLOCK("Relógio e data", 10, false),
	WEATHER("Previsão do tempo", 15, true),
	NEWS("Notícias", 20, true),
	RATES("Cotações", 12, true),
	NOTICE("Recado", 10, false),
	COUNTDOWN("Contagem regressiva", 10, false),
	PRICES("Tabela de preços", 20, true),
	QUEUE("Painel de chamadas", 15, true);

	private final String label;
	private final int defaultSeconds;
	private final boolean needsData;

	WidgetKind(String label, int defaultSeconds, boolean needsData) {
		this.label = label;
		this.defaultSeconds = defaultSeconds;
		this.needsData = needsData;
	}

	/** O nome que aparece na biblioteca quando o painel não dá outro. */
	public String label() {
		return label;
	}

	/** Quanto tempo o widget fica na tela quando o painel não escolhe. */
	public int defaultSeconds() {
		return defaultSeconds;
	}

	/** Busca dados de fora (clima, notícias, cotações); os demais se desenham só com as escolhas do painel. */
	public boolean needsData() {
		return needsData;
	}

	public static WidgetKind of(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Escolha o tipo do widget");
		}
		try {
			return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Tipo de widget desconhecido: " + name);
		}
	}

}
