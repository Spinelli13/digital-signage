package com.digitalsignage.backend.privacy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.security.PanelUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Privacidade e dados pessoais do cliente: prazos de guarda, canal de contato e o atendimento dos pedidos de titulares (LGPD,
 * art. 18). Só o ADMIN do cliente (inclusive para consultar: a busca mostra dados pessoais).
 */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

	public record SubjectRequest(@NotBlank @Size(max = 120) String term, @Size(max = 200) String note) {
	}

	private final PrivacyService privacy;

	public PrivacyController(PrivacyService privacy) {
		this.privacy = privacy;
	}

	@GetMapping("/settings")
	public PrivacyService.Settings settings() {
		return privacy.settings();
	}

	@PutMapping("/settings")
	public PrivacyService.Settings update(@Valid @RequestBody PrivacyService.SettingsRequest request) {
		return privacy.update(request);
	}

	/** Acha os dados de uma pessoa (nome, e-mail ou código do cupom) neste cliente, sem alterar nada. */
	@GetMapping("/search")
	public PrivacyService.SearchResult search(@RequestParam("q") String term) {
		return privacy.search(term);
	}

	/** A cópia dos dados da pessoa, como arquivo JSON. O pedido fica registrado. */
	@PostMapping("/export")
	public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal PanelUser caller, @Valid @RequestBody SubjectRequest request) {
		Map<String, Object> body = privacy.export(request.term(), request.note(), caller.email());
		String file = "dados-pessoais-" + LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString().replace("-", "") + ".json";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body);
	}

	/** Apaga os dados pessoais da pessoa (nome, aparelho e concordância) das senhas e dos cupons. O pedido fica registrado. */
	@PostMapping("/erase")
	public PrivacyService.EraseResult erase(@AuthenticationPrincipal PanelUser caller, @Valid @RequestBody SubjectRequest request) {
		return privacy.erase(request.term(), request.note(), caller.email());
	}

	@GetMapping("/requests")
	public List<PrivacyService.RequestEntry> requests() {
		return privacy.requests();
	}

}
