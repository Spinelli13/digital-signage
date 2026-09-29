package com.digitalsignage.backend.playlist;

import jakarta.validation.constraints.NotNull;

public record PlaylistItemRequest(
		@NotNull Long mediaId,
		Integer durationSeconds) {
}
