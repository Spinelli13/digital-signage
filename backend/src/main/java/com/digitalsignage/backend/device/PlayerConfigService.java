package com.digitalsignage.backend.device;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistRepository;

/**
 * Monta a configuração que o Player consome. O configVersion vem sempre da própria playlist
 * (nunca de devices.config_version, que guarda o que o Player reportou ter sincronizado).
 */
@Service
public class PlayerConfigService {

	private final PlaylistRepository playlistRepository;

	public PlayerConfigService(PlaylistRepository playlistRepository) {
		this.playlistRepository = playlistRepository;
	}

	@Transactional(readOnly = true)
	public PlayerConfigResponse buildFor(Device device) {
		if (device.getCurrentPlaylistId() == null) {
			return new PlayerConfigResponse(device.getId(), null, 0, List.of());
		}

		Playlist playlist = playlistRepository.findById(device.getCurrentPlaylistId())
				.orElseThrow(() -> new NoSuchElementException(
						"Playlist não encontrada: " + device.getCurrentPlaylistId()));

		List<PlayerConfigMediaItem> items = playlist.getItems().stream()
				.map(item -> new PlayerConfigMediaItem(
						item.getMedia().getId(),
						item.getMedia().getName(),
						item.getMedia().getType(),
						"/api/v1/media/" + item.getMedia().getId() + "/file",
						item.getMedia().getChecksum(),
						item.getMedia().getFileSize(),
						item.getPosition(),
						item.getDurationSeconds()))
				.toList();

		return new PlayerConfigResponse(device.getId(), playlist.getId(), playlist.getConfigVersion(), items);
	}

}
