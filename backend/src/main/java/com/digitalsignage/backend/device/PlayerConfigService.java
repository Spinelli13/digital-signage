package com.digitalsignage.backend.device;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.campaign.CampaignService;
import com.digitalsignage.backend.layout.Layout;
import com.digitalsignage.backend.layout.LayoutRepository;
import com.digitalsignage.backend.layout.LayoutZone;
import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistItem;
import com.digitalsignage.backend.playlist.PlaylistRepository;

/**
 * Monta a configuração que o Player consome. O configVersion vem sempre do conteúdo (playlist e, com layout, o
 * layout e as playlists das outras zonas), nunca de devices.config_version, que guarda o que o Player reportou
 * ter sincronizado.
 */
@Service
public class PlayerConfigService {

	private final PlaylistRepository playlistRepository;
	private final LayoutRepository layoutRepository;
	private final CampaignService campaignService;

	public PlayerConfigService(PlaylistRepository playlistRepository, LayoutRepository layoutRepository, CampaignService campaignService) {
		this.playlistRepository = playlistRepository;
		this.layoutRepository = layoutRepository;
		this.campaignService = campaignService;
	}

	@Transactional(readOnly = true)
	public PlayerConfigResponse buildFor(Device device) {
		return buildFor(device, campaignService.effectiveFor(device));
	}

	/** Com o que a TV exibe agora já decidido (campanha ativa que a alcança ou, sem campanha, a playlist base). */
	@Transactional(readOnly = true)
	public PlayerConfigResponse buildFor(Device device, CampaignService.Effective effective) {
		PlayerConfigResponse base = build(device, effective);
		return effective.fromCampaign() ? base.withCampaign(effective.campaign().getId(), effective.campaign().getName()) : base;
	}

	private PlayerConfigResponse build(Device device, CampaignService.Effective effective) {
		Layout layout = device.getLayoutId() == null ? null : layoutRepository.findById(device.getLayoutId()).orElse(null);
		Long mainPlaylistId = effective.playlistId();
		Playlist main = mainPlaylistId == null ? null : playlistRepository.findById(mainPlaylistId)
				.orElseThrow(() -> new NoSuchElementException("Playlist não encontrada: " + mainPlaylistId));

		if (layout == null) {
			return main == null
					? new PlayerConfigResponse(device.getId(), null, 0, List.of())
					: new PlayerConfigResponse(device.getId(), main.getId(), main.getConfigVersion(), itemsOf(main));
		}

		List<PlayerConfigZone> zones = new ArrayList<>();
		Map<Long, Long> versions = new HashMap<>();
		for (LayoutZone zone : layout.getZones()) {
			Playlist playlist = zone.isMain() ? main : playlistOf(zone.getPlaylistId());
			if (playlist != null) {
				versions.put(playlist.getId(), playlist.getConfigVersion());
			}
			zones.add(new PlayerConfigZone(zone.getId(), zone.getName(), zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight(),
					zone.isMain(), playlist == null ? null : playlist.getId(), playlist == null ? List.of() : itemsOf(playlist)));
		}
		List<PlayerConfigMediaItem> mainItems = zones.stream().filter(PlayerConfigZone::main).findFirst()
				.map(PlayerConfigZone::items).orElse(List.of());
		return new PlayerConfigResponse(device.getId(), main == null ? null : main.getId(),
				versionFor(mainPlaylistId, layout, versions), mainItems, zones);
	}

	/**
	 * A versão do conteúdo para várias TVs de uma vez (a lista do painel mostra "sincronizando…" comparando com o
	 * que a TV reportou). Mesma conta do heartbeat: as duas usam {@link #versionFor}.
	 */
	@Transactional(readOnly = true)
	public Map<Long, Long> desiredVersions(Collection<Device> devices, Map<Long, CampaignService.Effective> effective) {
		Map<Long, Layout> layouts = layoutRepository.findAllById(
				devices.stream().map(Device::getLayoutId).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(Layout::getId, layout -> layout));
		List<Long> playlistIds = new ArrayList<>();
		devices.forEach(device -> {
			Long main = effective.get(device.getId()).playlistId();
			if (main != null) {
				playlistIds.add(main);
			}
		});
		layouts.values().forEach(layout -> layout.getZones().stream().map(LayoutZone::getPlaylistId).filter(Objects::nonNull).forEach(playlistIds::add));
		Map<Long, Long> versions = playlistRepository.findAllById(playlistIds.stream().distinct().toList()).stream()
				.collect(Collectors.toMap(Playlist::getId, Playlist::getConfigVersion));

		Map<Long, Long> result = new HashMap<>();
		for (Device device : devices) {
			Layout layout = device.getLayoutId() == null ? null : layouts.get(device.getLayoutId());
			result.put(device.getId(), versionFor(effective.get(device.getId()).playlistId(), layout, versions));
		}
		return result;
	}

	/**
	 * Sem layout: a versão da playlist (0 sem playlist). Com layout: combinação da playlist principal, do layout e das
	 * playlists das outras zonas. {@code playlistVersions} traz a versão de cada playlist envolvida.
	 */
	static long versionFor(Long mainPlaylistId, Layout layout, Map<Long, Long> playlistVersions) {
		long mainVersion = mainPlaylistId == null ? 0 : playlistVersions.getOrDefault(mainPlaylistId, 0L);
		if (layout == null) {
			return mainVersion;
		}
		List<ConfigVersions.ZoneVersion> others = layout.getZones().stream()
				.filter(zone -> !zone.isMain())
				.map(zone -> zone.getPlaylistId() == null
						? new ConfigVersions.ZoneVersion(0, 0)
						: new ConfigVersions.ZoneVersion(zone.getPlaylistId(), playlistVersions.getOrDefault(zone.getPlaylistId(), 0L)))
				.toList();
		return ConfigVersions.composite(mainPlaylistId == null ? 0 : mainPlaylistId, mainVersion, layout.getId(), layout.getConfigVersion(), others);
	}

	private Playlist playlistOf(Long playlistId) {
		return playlistId == null ? null : playlistRepository.findById(playlistId).orElse(null);
	}

	static List<PlayerConfigMediaItem> itemsOf(Playlist playlist) {
		return playlist.getItems().stream().map(PlayerConfigService::itemOf).toList();
	}

	private static PlayerConfigMediaItem itemOf(PlaylistItem item) {
		Media media = item.getMedia();
		// Conteúdo web não tem arquivo: o "url" é o próprio endereço da página, que o Player abre direto.
		// Widget também não tem arquivo: o "url" é de onde o Player busca os dados (clima, notícias...), e as escolhas vão junto.
		String url = media.isWeb() ? media.getWebUrl()
				: media.isWidget() ? "/api/v1/player/widgets/" + media.getId() + "/data"
				: "/api/v1/media/" + media.getId() + "/file";
		java.util.Map<String, Object> widget = media.isWidget()
				? java.util.Map.of("kind", media.getWidgetKind(), "settings", media.getWidgetConfig() == null ? java.util.Map.of() : media.getWidgetConfig())
				: null;
		return new PlayerConfigMediaItem(media.getId(), media.getName(), media.getType(), url, media.getChecksum(),
				media.getFileSize(), item.getPosition(), item.getDurationSeconds(), widget);
	}

}
