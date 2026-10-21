package com.digitalsignage.backend.device;

import java.util.Arrays;

/**
 * Como a TV foi instalada. Uma TV presa na parede na vertical continua recebendo um sinal de vídeo horizontal (a saída
 * da caixa de TV não gira sozinha): o Player precisa girar o conteúdo. {@link #AUTO} é o que sempre foi: o Player usa
 * a tela como o aparelho a entrega (o Player é travado na horizontal).
 *
 * <p>Os nomes dizem para onde aponta o TOPO DA TV (o lado onde fica o botão de ligar/a logomarca), que é o que quem
 * instala vê: "girada para a direita" é ambíguo, "topo para a direita" não.
 */
public enum Orientation {
	/** O Player usa a tela como o aparelho a entrega, sem girar nada (padrão). */
	AUTO("automática (segue a tela da TV)"),
	/** Horizontal, a posição normal. */
	LANDSCAPE("horizontal"),
	/** Horizontal de cabeça para baixo (TV no teto, por exemplo). */
	LANDSCAPE_FLIPPED("horizontal de cabeça para baixo"),
	/** Vertical, com o topo da TV virado para a direita. */
	PORTRAIT_TOP_RIGHT("vertical, com o topo da TV virado para a direita"),
	/** Vertical, com o topo da TV virado para a esquerda. */
	PORTRAIT_TOP_LEFT("vertical, com o topo da TV virado para a esquerda");

	private final String description;

	Orientation(String description) {
		this.description = description;
	}

	/** A frase do histórico da TV ("Orientação da tela: ..."). */
	public String description() {
		return description;
	}

	public boolean isPortrait() {
		return this == PORTRAIT_TOP_RIGHT || this == PORTRAIT_TOP_LEFT;
	}

	/** Aceita maiúsculas ou minúsculas; valor desconhecido vira 400 com a lista do que vale. */
	public static Orientation parse(String value) {
		String wanted = value == null ? "" : value.trim().toUpperCase();
		return Arrays.stream(values()).filter(orientation -> orientation.name().equals(wanted)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Orientação inválida: use AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT ou PORTRAIT_TOP_LEFT"));
	}

	/** Do que está gravado na TV; qualquer coisa estranha volta ao automático (nunca derruba a configuração). */
	public static Orientation of(String stored) {
		return Arrays.stream(values()).filter(orientation -> orientation.name().equals(stored)).findFirst().orElse(AUTO);
	}
}
