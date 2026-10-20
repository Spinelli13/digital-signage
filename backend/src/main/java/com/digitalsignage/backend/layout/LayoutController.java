package com.digitalsignage.backend.layout;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.playlist.Playlist;
import com.digitalsignage.backend.playlist.PlaylistRepository;

import jakarta.validation.Valid;

/** Layouts (divisão da tela em zonas) e os modelos prontos para começar de um deles. */
@RestController
@RequestMapping("/api/v1/layouts")
public class LayoutController {

	public record FromTemplateRequest(@jakarta.validation.constraints.NotBlank String templateId, String name) {
	}

	private final LayoutRepository layoutRepository;
	private final PlaylistRepository playlistRepository;
	private final DeviceRepository deviceRepository;

	public LayoutController(LayoutRepository layoutRepository, PlaylistRepository playlistRepository, DeviceRepository deviceRepository) {
		this.layoutRepository = layoutRepository;
		this.playlistRepository = playlistRepository;
		this.deviceRepository = deviceRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<LayoutResponse> list() {
		Map<Long, String> playlistNames = playlistNames();
		Map<Long, Long> usage = deviceRepository.countByLayout().stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
		return layoutRepository.findAll().stream()
				.sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
				.map(layout -> LayoutResponse.from(layout, playlistNames, usage.getOrDefault(layout.getId(), 0L)))
				.toList();
	}

	@GetMapping("/templates")
	public List<LayoutTemplates.Template> templates() {
		return LayoutTemplates.ALL;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public LayoutResponse create(@Valid @RequestBody LayoutRequest request) {
		String name = request.name().trim();
		requireFreeName(name, null);
		Layout layout = new Layout(name, blankToNull(request.description()));
		applyZones(layout, request.zones());
		return responseFor(layoutRepository.save(layout));
	}

	/** Cria um layout já com as zonas de um modelo pronto (o nome padrão é o do modelo). */
	@PostMapping("/from-template")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public LayoutResponse fromTemplate(@Valid @RequestBody FromTemplateRequest request) {
		LayoutTemplates.Template template = LayoutTemplates.find(request.templateId())
				.orElseThrow(() -> new NoSuchElementException("Modelo de layout não encontrado: " + request.templateId()));
		String name = request.name() == null || request.name().isBlank() ? template.name() : request.name().trim();
		requireFreeName(name, null);
		Layout layout = new Layout(name, template.description());
		applyZones(layout, template.zones());
		return responseFor(layoutRepository.save(layout));
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public LayoutResponse get(@PathVariable Long id) {
		return responseFor(findOrThrow(id));
	}

	@PutMapping("/{id}")
	@Transactional
	public LayoutResponse update(@PathVariable Long id, @Valid @RequestBody LayoutRequest request) {
		Layout layout = findOrThrow(id);
		String name = request.name().trim();
		requireFreeName(name, id);
		layout.setName(name);
		layout.setDescription(blankToNull(request.description()));
		applyZones(layout, request.zones());
		return responseFor(layoutRepository.save(layout));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		Layout layout = findOrThrow(id);
		long using = deviceRepository.countByLayoutId(id);
		if (using > 0) {
			throw new ConflictException("O layout \"" + layout.getName() + "\" está em uso por " + using
					+ " TV(s). Escolha outro layout (ou tela cheia) para elas antes de excluir.");
		}
		layoutRepository.delete(layout);
	}

	// ---------- utilidades ----------

	private void applyZones(Layout layout, List<LayoutRequest.ZoneRequest> zones) {
		LayoutValidator.validate(zones);
		Set<Long> playlistIds = new HashSet<>();
		zones.stream().map(LayoutRequest.ZoneRequest::playlistId).filter(java.util.Objects::nonNull).forEach(playlistIds::add);
		if (!playlistIds.isEmpty() && playlistRepository.findAllById(playlistIds).size() != playlistIds.size()) {
			throw new NoSuchElementException("Playlist não encontrada entre: " + playlistIds);
		}
		layout.replaceZones(zones.stream()
				.map(zone -> new LayoutZone(zone.name().trim(), zone.x(), zone.y(), zone.width(), zone.height(), zone.main(), zone.playlistId()))
				.toList());
	}

	private void requireFreeName(String name, Long ownId) {
		boolean taken = ownId == null
				? layoutRepository.existsByNameIgnoreCase(name)
				: layoutRepository.existsByNameIgnoreCaseAndIdNot(name, ownId);
		if (taken) {
			throw new ConflictException("Já existe um layout chamado \"" + name + "\"");
		}
	}

	private LayoutResponse responseFor(Layout layout) {
		return LayoutResponse.from(layout, playlistNames(), deviceRepository.countByLayoutId(layout.getId()));
	}

	private Map<Long, String> playlistNames() {
		return playlistRepository.findAll().stream().collect(Collectors.toMap(Playlist::getId, Playlist::getName));
	}

	private Layout findOrThrow(Long id) {
		return layoutRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Layout não encontrado: " + id));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
