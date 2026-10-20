package com.digitalsignage.backend.campaign;

/** A quem a campanha se dirige. Quanto mais específico o alvo, mais ele pesa no desempate (ver CampaignResolver). */
public enum TargetType {
	ALL(0),
	LOCATION(1),
	GROUP(2),
	DEVICE(3);

	private final int specificity;

	TargetType(int specificity) {
		this.specificity = specificity;
	}

	public int specificity() {
		return specificity;
	}
}
