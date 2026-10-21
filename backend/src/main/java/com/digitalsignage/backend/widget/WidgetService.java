package com.digitalsignage.backend.widget;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.media.MediaRepository;
import com.digitalsignage.backend.monitoring.WebhookUrlPolicy;
import com.digitalsignage.backend.playlist.PlaylistRepository;
import com.digitalsignage.backend.price.PriceListRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Criar e editar widgets (a mídia do tipo WIDGET): valida as escolhas e avisa as TVs que usam o widget quando algo muda. */
@Service
public class WidgetService {

	public record Request(
			@Size(max = 255) String name,
			@NotBlank String kind,
			Map<String, Object> settings,
			@Min(5) @Max(3600) Integer durationSeconds) {
	}

	private final MediaRepository mediaRepository;
	private final PlaylistRepository playlistRepository;
	private final WebhookUrlPolicy urls;
	private final PriceListRepository priceLists;

	public WidgetService(MediaRepository mediaRepository, PlaylistRepository playlistRepository, WebhookUrlPolicy urls, PriceListRepository priceLists) {
		this.mediaRepository = mediaRepository;
		this.playlistRepository = playlistRepository;
		this.urls = urls;
		this.priceLists = priceLists;
	}

	@Transactional
	public Media create(Request request) {
		WidgetKind kind = WidgetKind.of(request.kind());
		Map<String, Object> settings = WidgetSettings.sanitize(kind, request.settings(), urls);
		requirePriceList(kind, settings);
		int seconds = request.durationSeconds() == null ? kind.defaultSeconds() : request.durationSeconds();
		return mediaRepository.save(Media.widget(nameFor(request.name(), kind, settings), kind.name(), settings, seconds));
	}

	/** O tipo do widget não muda (um relógio não vira clima): só o nome, as escolhas e o tempo na tela. */
	@Transactional
	public Media update(Long id, Request request) {
		Media media = mediaRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Mídia não encontrada: " + id));
		if (!media.isWidget()) {
			throw new IllegalArgumentException("Esta mídia não é um widget");
		}
		WidgetKind kind = WidgetKind.of(media.getWidgetKind());
		if (request.kind() != null && !request.kind().isBlank() && WidgetKind.of(request.kind()) != kind) {
			throw new IllegalArgumentException("O tipo do widget não pode ser trocado: crie outro widget");
		}
		Map<String, Object> settings = WidgetSettings.sanitize(kind, request.settings(), urls);
		requirePriceList(kind, settings);
		int seconds = request.durationSeconds() == null ? media.getDurationSeconds() : request.durationSeconds();
		media.changeWidget(nameFor(request.name(), kind, settings), settings, seconds);
		Media saved = mediaRepository.save(media);
		// As TVs guardam as escolhas do widget dentro da configuração da playlist: sem isto, só veriam a mudança numa troca de playlist.
		playlistRepository.findUsing(id).forEach(playlist -> playlist.touch());
		return saved;
	}

	/** Um painel de preços só faz sentido com uma tabela que existe (do cliente atual). */
	private void requirePriceList(WidgetKind kind, Map<String, Object> settings) {
		if (kind == WidgetKind.PRICES && !priceLists.existsById(((Number) settings.get("priceListId")).longValue())) {
			throw new IllegalArgumentException("A tabela de preços escolhida não existe");
		}
	}

	static String nameFor(String name, WidgetKind kind, Map<String, Object> settings) {
		if (name != null && !name.isBlank()) {
			return name.strip();
		}
		Object place = kind == WidgetKind.WEATHER ? settings.get("city") : null;
		return place == null ? kind.label() : kind.label() + " - " + place;
	}

}
