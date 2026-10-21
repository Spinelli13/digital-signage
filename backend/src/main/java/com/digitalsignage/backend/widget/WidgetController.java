package com.digitalsignage.backend.widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.monitoring.WebhookUrlPolicy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** O que o painel precisa para montar widgets: o catálogo, a busca de cidades e a pré-visualização dos dados. */
@RestController
@RequestMapping("/api/v1/widgets")
public class WidgetController {

	public record PreviewRequest(@NotBlank String kind, Map<String, Object> settings) {
	}

	private final WidgetDataService data;
	private final WebhookUrlPolicy urls;

	public WidgetController(WidgetDataService data, WebhookUrlPolicy urls) {
		this.data = data;
		this.urls = urls;
	}

	/** Tipos, fontes de notícias e moedas disponíveis. */
	@GetMapping("/catalog")
	public Map<String, Object> catalog() {
		List<Map<String, Object>> kinds = new ArrayList<>();
		for (WidgetKind kind : WidgetKind.values()) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("kind", kind.name());
			row.put("label", kind.label());
			row.put("defaultSeconds", kind.defaultSeconds());
			row.put("needsData", kind.needsData());
			kinds.add(row);
		}
		Map<String, Object> catalog = new LinkedHashMap<>();
		catalog.put("kinds", kinds);
		catalog.put("newsSources", NewsFeeds.catalog());
		catalog.put("currencies", WidgetSettings.CURRENCIES);
		return catalog;
	}

	/** Busca de cidade (para a previsão do tempo): nome, estado, país e coordenadas. */
	@GetMapping("/places")
	public List<WidgetDataService.Place> places(@RequestParam("q") String query, @RequestParam(value = "lang", required = false) String lang) {
		return data.searchPlaces(query, lang);
	}

	/**
	 * Confere as escolhas (as mesmas regras de salvar) e devolve as escolhas já limpas e os dados que a TV mostraria agora,
	 * sem gravar nada: é o que alimenta a pré-visualização do painel.
	 */
	@PostMapping("/preview")
	public Map<String, Object> preview(@Valid @RequestBody PreviewRequest request) {
		WidgetKind kind = WidgetKind.of(request.kind());
		Map<String, Object> settings = WidgetSettings.sanitize(kind, request.settings(), urls);
		WidgetDataService.Result result = data.dataFor(kind, settings);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("kind", kind.name());
		body.put("settings", settings);
		body.put("data", result.data());
		body.put("error", result.error());
		return body;
	}

}
