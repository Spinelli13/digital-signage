package com.digitalsignage.backend.template;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.template.SegmentTemplates.Field;

/** Modelos prontos por segmento: a lista (qualquer papel) e "usar este modelo" (Editor e acima). */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

	/** O que o painel mostra de cada modelo (textos a conferir, o que vai ser criado). */
	public record TemplateInfo(String id, String name, String icon, String description, List<Field> fields, List<String> includes, boolean hasPrices) {
	}

	public record Catalog(SegmentTemplateService.Availability available, List<TemplateInfo> templates) {
	}

	private final SegmentTemplateService service;

	public TemplateController(SegmentTemplateService service) {
		this.service = service;
	}

	@GetMapping
	public Catalog list() {
		return new Catalog(service.availability(), SegmentTemplates.ALL.stream().map(TemplateController::info).toList());
	}

	@PostMapping("/{id}/apply")
	@ResponseStatus(HttpStatus.CREATED)
	public SegmentTemplateService.Result apply(@PathVariable String id, @RequestBody(required = false) SegmentTemplateService.ApplyRequest request) {
		return service.apply(id, request);
	}

	private static TemplateInfo info(SegmentTemplates.Template template) {
		java.util.ArrayList<String> includes = new java.util.ArrayList<>();
		template.main().forEach(notice -> includes.add(notice.title().contains("{") ? "Boas-vindas" : notice.title()));
		if (template.hasPrices()) {
			includes.add(template.priceTitle());
		}
		includes.add("Relógio");
		includes.add("Previsão do tempo");
		template.side().forEach(notice -> includes.add(notice.title()));
		return new TemplateInfo(template.id(), template.name(), template.icon(), template.description(), template.fields(), includes, template.hasPrices());
	}
}
