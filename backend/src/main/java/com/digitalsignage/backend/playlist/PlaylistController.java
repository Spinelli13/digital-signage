package com.digitalsignage.backend.playlist;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.campaign.CampaignRepository;
import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.layout.LayoutRepository;
import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.media.MediaRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/playlists")
public class PlaylistController {

	private static final int DEFAULT_ITEM_DURATION_SECONDS = 10;

	private final PlaylistRepository playlistRepository;
	private final MediaRepository mediaRepository;
	private final CampaignRepository campaignRepository;
	private final LayoutRepository layoutRepository;
	private final Clock clock;

	public PlaylistController(
			PlaylistRepository playlistRepository,
			MediaRepository mediaRepository,
			CampaignRepository campaignRepository,
			LayoutRepository layoutRepository,
			Clock clock) {
		this.clock = clock;
		this.playlistRepository = playlistRepository;
		this.mediaRepository = mediaRepository;
		this.campaignRepository = campaignRepository;
		this.layoutRepository = layoutRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<PlaylistResponse> list() {
		java.time.LocalDate today = ItemValidity.today(clock);
		return playlistRepository.findAll().stream().map(playlist -> PlaylistResponse.from(playlist, today)).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public PlaylistResponse create(@Valid @RequestBody PlaylistCreateRequest request) {
		Playlist playlist = new Playlist(request.name(), request.description());
		if (request.items() != null) {
			playlist.replaceItems(buildItems(request.items()));
		}
		return PlaylistResponse.from(playlistRepository.save(playlist), ItemValidity.today(clock));
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public PlaylistResponse get(@PathVariable Long id) {
		return PlaylistResponse.from(findOrThrow(id), ItemValidity.today(clock));
	}

	@PutMapping("/{id}")
	@Transactional
	public PlaylistResponse update(@PathVariable Long id, @Valid @RequestBody PlaylistUpdateRequest request) {
		Playlist playlist = findOrThrow(id);
		playlist.setName(request.name());
		playlist.setDescription(request.description());
		if (request.items() != null) {
			playlist.replaceItems(buildItems(request.items()));
		}
		return PlaylistResponse.from(playlistRepository.save(playlist), ItemValidity.today(clock));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		Playlist playlist = findOrThrow(id);
		campaignRepository.findFirstByPlaylistId(id).ifPresent(campaign -> {
			throw new ConflictException("A playlist \"" + playlist.getName() + "\" está em uso pela campanha \""
					+ campaign.getName() + "\". Troque a playlist da campanha ou exclua a campanha antes.");
		});
		layoutRepository.findUsingPlaylist(id).stream().findFirst().ifPresent(layout -> {
			throw new ConflictException("A playlist \"" + playlist.getName() + "\" está em uso pelo layout \""
					+ layout.getName() + "\". Troque a playlist da zona ou exclua o layout antes.");
		});
		playlistRepository.delete(playlist);
	}

	private Playlist findOrThrow(Long id) {
		return playlistRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Playlist não encontrada: " + id));
	}

	private List<PlaylistItem> buildItems(List<PlaylistItemRequest> requests) {
		List<PlaylistItem> items = new ArrayList<>();
		int position = 0;

		for (PlaylistItemRequest request : requests) {
			Media media = mediaRepository.findById(request.mediaId())
					.orElseThrow(() -> new NoSuchElementException("Mídia não encontrada: " + request.mediaId()));

			int duration = resolveDuration(request, media);
			if (request.validFrom() != null && request.validUntil() != null && request.validFrom().isAfter(request.validUntil())) {
				throw new IllegalArgumentException("A data de início da validade de \"" + media.getName() + "\" não pode ser depois da data de fim");
			}
			items.add(new PlaylistItem(media, position++, duration, request.validFrom(), request.validUntil(), ItemValidity.formatDays(request.days())));
		}

		return items;
	}

	private int resolveDuration(PlaylistItemRequest request, Media media) {
		if (request.durationSeconds() != null) {
			return request.durationSeconds();
		}
		if (media.getDurationSeconds() != null) {
			return media.getDurationSeconds();
		}
		return DEFAULT_ITEM_DURATION_SECONDS;
	}

}
