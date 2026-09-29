package com.digitalsignage.backend.playlist;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record PlaylistUpdateRequest(
		@NotBlank String name,
		String description,
		@Valid List<PlaylistItemRequest> items) {
}
