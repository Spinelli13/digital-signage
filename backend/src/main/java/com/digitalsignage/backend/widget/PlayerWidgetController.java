package com.digitalsignage.backend.widget;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.media.MediaRepository;

/** O que o Player busca para desenhar um widget que mostra dados de fora (clima, notícias, cotações). */
@RestController
@RequestMapping("/api/v1/player/widgets")
public class PlayerWidgetController {

	private final MediaRepository mediaRepository;
	private final WidgetDataService data;
	private final java.time.Clock clock;

	public PlayerWidgetController(MediaRepository mediaRepository, WidgetDataService data, java.time.Clock clock) {
		this.mediaRepository = mediaRepository;
		this.data = data;
		this.clock = clock;
	}

	/** Os dados de agora (ou os últimos guardados) do widget; {@code data} nulo e {@code error} preenchido se não deu para buscar. */
	@GetMapping("/{mediaId}/data")
	public Map<String, Object> data(@PathVariable Long mediaId) {
		Media media = mediaRepository.findById(mediaId).filter(Media::isWidget)
				.orElseThrow(() -> new NoSuchElementException("Widget não encontrado: " + mediaId));
		WidgetKind kind = WidgetKind.of(media.getWidgetKind());
		WidgetDataService.Result result = data.dataFor(kind, media.getWidgetConfig());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("kind", kind.name());
		body.put("data", result.data());
		body.put("error", result.error());
		body.put("serverTimeMs", clock.millis());
		return body;
	}

}
