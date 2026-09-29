package com.digitalsignage.backend.device;

import java.util.List;

public record PlayerConfigResponse(
		Long deviceId,
		Long playlistId,
		long configVersion,
		List<PlayerConfigMediaItem> items) {
}
