package com.digitalsignage.backend.price;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

/** Tabelas de preços (cardápio, lista de serviços...): editar à mão, importar e exportar planilha, e ver como fica na TV. */
@RestController
@RequestMapping("/api/v1/price-lists")
public class PriceListController {

	private final PriceListService service;
	private final PriceListRepository repository;
	private final PriceBoardService board;

	public PriceListController(PriceListService service, PriceListRepository repository, PriceBoardService board) {
		this.service = service;
		this.repository = repository;
		this.board = board;
	}

	public record ImportResult(PriceListResponse list, int imported, List<String> skipped) {
	}

	@GetMapping
	public List<PriceListResponse> list() {
		return repository.findAllByOrderByNameAsc().stream().map(this::response).toList();
	}

	@GetMapping("/{id}")
	public PriceListResponse get(@PathVariable Long id) {
		return response(service.find(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PriceListResponse create(@Valid @RequestBody PriceListRequest request) {
		return response(service.create(request));
	}

	@PutMapping("/{id}")
	public PriceListResponse update(@PathVariable Long id, @Valid @RequestBody PriceListRequest request) {
		return response(service.update(id, request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	/** Os preços das próximas 24 horas de uma tabela AINDA NÃO SALVA: a pré-visualização do painel enquanto a pessoa edita. */
	@PostMapping("/preview")
	public Map<String, Object> preview(@Valid @RequestBody PriceListRequest request) {
		return board.dataFor(service.draft(request));
	}

	@GetMapping("/{id}/export")
	public ResponseEntity<byte[]> export(@PathVariable Long id) {
		PriceList list = service.find(id);
		String plain = java.text.Normalizer.normalize(list.getName(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String filename = "precos-" + plain.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") + ".csv";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8))
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(service.exportCsv(list));
	}

	/** {@code mode=replace} troca todos os itens pelos da planilha; {@code mode=append} acrescenta no fim. */
	@PostMapping("/{id}/import")
	public ImportResult importCsv(@PathVariable Long id, @RequestParam("file") MultipartFile file,
			@RequestParam(value = "mode", defaultValue = "replace") String mode) throws IOException {
		if (file.isEmpty()) {
			throw new IllegalArgumentException("Arquivo vazio");
		}
		if (!"replace".equals(mode) && !"append".equals(mode)) {
			throw new IllegalArgumentException("Modo de importação desconhecido: " + mode);
		}
		PriceListService.Imported imported = service.importCsv(id, file.getBytes(), "replace".equals(mode));
		return new ImportResult(response(imported.list()), imported.imported(), imported.skipped());
	}

	private PriceListResponse response(PriceList list) {
		return PriceListResponse.from(list, service.widgetsUsing(list.getId()).size());
	}

}
