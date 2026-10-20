package com.digitalsignage.backend.player;

import jakarta.validation.constraints.Size;

/** configVersion = versão da playlist que o Player de fato sincronizou (baixou e validou). */
public record PlayerHeartbeatRequest(
		@Size(max = 50) String playerVersion,
		long configVersion) {
}
