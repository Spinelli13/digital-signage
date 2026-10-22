package com.digitalsignage.backend.template;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.device.BulkAction;
import com.digitalsignage.backend.device.BulkDeviceRequest;
import com.digitalsignage.backend.device.DeviceService;
import com.digitalsignage.backend.layout.LayoutController;
import com.digitalsignage.backend.layout.LayoutRepository;
import com.digitalsignage.backend.layout.LayoutRequest;
import com.digitalsignage.backend.layout.LayoutRequest.ZoneRequest;
import com.digitalsignage.backend.media.Media;
import com.digitalsignage.backend.plan.Feature;
import com.digitalsignage.backend.playlist.PlaylistController;
import com.digitalsignage.backend.playlist.PlaylistCreateRequest;
import com.digitalsignage.backend.playlist.PlaylistItemRequest;
import com.digitalsignage.backend.price.PriceList;
import com.digitalsignage.backend.price.PriceListRepository;
import com.digitalsignage.backend.price.PriceListRequest;
import com.digitalsignage.backend.price.PriceListService;
import com.digitalsignage.backend.template.SegmentTemplates.Notice;
import com.digitalsignage.backend.template.SegmentTemplates.PriceDef;
import com.digitalsignage.backend.template.SegmentTemplates.Template;
import com.digitalsignage.backend.tenant.PlanLimits;
import com.digitalsignage.backend.tenant.TenantContext;
import com.digitalsignage.backend.widget.WidgetKind;
import com.digitalsignage.backend.widget.WidgetService;

/**
 * Cria, de uma vez, o que um modelo de segmento promete: os avisos (widgets), a tabela de preços, as playlists e o layout. Só usa
 * o que o plano do cliente inclui: sem layouts tudo roda numa playlist só, sem tabela de preços ela não é criada, e o resultado
 * conta o que ficou de fora. O que é criado é igual ao feito à mão (nada fica preso ao modelo): o cliente edita os textos depois.
 */
@Service
public class SegmentTemplateService {

	/** A cidade da previsão do tempo (vem da busca de cidades do painel). */
	public record City(String name, Double latitude, Double longitude, String timezone) {
	}

	public record ApplyRequest(Map<String, String> fields, City city, List<Long> deviceIds) {
	}

	public record Created(Long id, String name) {
	}

	public record Result(String templateId, Created layout, List<Created> playlists, List<Created> widgets, Created priceList, List<String> notes,
			int devices) {
	}

	private final WidgetService widgets;
	private final PriceListService priceLists;
	private final PriceListRepository priceListRepository;
	private final PlaylistController playlists;
	private final LayoutController layouts;
	private final LayoutRepository layoutRepository;
	private final DeviceService devices;
	private final PlanLimits limits;

	public SegmentTemplateService(WidgetService widgets, PriceListService priceLists, PriceListRepository priceListRepository, PlaylistController playlists,
			LayoutController layouts, LayoutRepository layoutRepository, DeviceService devices, PlanLimits limits) {
		this.widgets = widgets;
		this.priceLists = priceLists;
		this.priceListRepository = priceListRepository;
		this.playlists = playlists;
		this.layouts = layouts;
		this.layoutRepository = layoutRepository;
		this.devices = devices;
		this.limits = limits;
	}

	/** O que este plano deixa o modelo criar (a tela mostra antes de a pessoa confirmar). */
	public record Availability(boolean widgets, boolean prices, boolean layouts) {
	}

	public Availability availability() {
		long tenant = TenantContext.current();
		return new Availability(limits.includes(tenant, Feature.WIDGETS), limits.includes(tenant, Feature.PRICES), limits.includes(tenant, Feature.LAYOUTS));
	}

	@Transactional
	public Result apply(String templateId, ApplyRequest request) {
		Template template = SegmentTemplates.find(templateId).orElseThrow(() -> new NoSuchElementException("Modelo não encontrado: " + templateId));
		Availability available = availability();
		long tenant = TenantContext.current();
		if (!available.widgets()) {
			limits.requireFeature(tenant, Feature.WIDGETS); // lança a explicação padrão do plano
		}
		Map<String, String> values = SegmentTemplates.resolve(template, request == null ? null : request.fields());
		String business = values.getOrDefault("name", template.name());
		List<String> notes = new ArrayList<>();
		List<Created> createdWidgets = new ArrayList<>();

		List<Media> mainMedia = new ArrayList<>();
		for (Notice notice : template.main()) {
			mainMedia.add(widget(createdWidgets, business + " - " + label(notice), WidgetKind.NOTICE, noticeSettings(notice, values), notice.seconds()));
		}

		Created priceList = null;
		if (template.hasPrices()) {
			if (available.prices()) {
				priceList = priceList(template, business);
				Map<String, Object> settings = new LinkedHashMap<>();
				settings.put("priceListId", priceList.id());
				settings.put("title", template.priceTitle());
				settings.put("theme", "dark");
				mainMedia.add(widget(createdWidgets, business + " - " + template.priceTitle(), WidgetKind.PRICES, settings, WidgetKind.PRICES.defaultSeconds()));
			} else {
				notes.add("A tabela de preços não foi criada: o plano não inclui tabelas de preços.");
			}
		}

		Media clock = widget(createdWidgets, business + " - Relógio", WidgetKind.CLOCK, Map.of("timezone", "America/Sao_Paulo", "showDate", true, "theme", "dark"),
				WidgetKind.CLOCK.defaultSeconds());
		Media weather = null;
		City city = request == null ? null : request.city();
		if (city != null && city.name() != null && !city.name().isBlank() && city.latitude() != null && city.longitude() != null) {
			Map<String, Object> settings = new LinkedHashMap<>();
			settings.put("city", city.name());
			settings.put("latitude", city.latitude());
			settings.put("longitude", city.longitude());
			if (city.timezone() != null && !city.timezone().isBlank()) {
				settings.put("timezone", city.timezone());
			}
			settings.put("theme", "dark");
			weather = widget(createdWidgets, business + " - Clima", WidgetKind.WEATHER, settings, WidgetKind.WEATHER.defaultSeconds());
		} else {
			notes.add("A previsão do tempo não foi criada: escolha a cidade para incluí-la (você pode criar depois na Biblioteca).");
		}
		List<Media> sideMedia = new ArrayList<>();
		for (Notice notice : template.side()) {
			sideMedia.add(widget(createdWidgets, business + " - " + label(notice), WidgetKind.NOTICE, noticeSettings(notice, values), notice.seconds()));
		}

		List<Created> createdPlaylists = new ArrayList<>();
		Created layout = null;
		Created main;
		if (available.layouts()) {
			main = playlist(createdPlaylists, business + " - Principal", mainMedia);
			Created clockList = playlist(createdPlaylists, business + " - Relógio", List.of(clock));
			Created weatherList = weather == null ? null : playlist(createdPlaylists, business + " - Clima", List.of(weather));
			Created sideList = playlist(createdPlaylists, business + " - Faixa lateral", sideMedia);
			layout = layout(business, clockList.id(), weatherList == null ? null : weatherList.id(), sideList.id());
		} else {
			// sem layouts: uma playlist só, com tudo em sequência
			List<Media> all = new ArrayList<>(mainMedia);
			all.add(clock);
			if (weather != null) {
				all.add(weather);
			}
			all.addAll(sideMedia);
			main = playlist(createdPlaylists, business + " - Principal", all);
			notes.add("O layout com a faixa lateral não foi criado: o plano não inclui layouts. Tudo roda numa playlist só.");
		}

		int onTvs = 0;
		if (request != null && request.deviceIds() != null && !request.deviceIds().isEmpty()) {
			devices.bulk(new BulkDeviceRequest(BulkAction.ASSIGN_PLAYLIST, request.deviceIds(), null, null, main.id(), null, null, null, null));
			if (layout != null) {
				devices.bulk(new BulkDeviceRequest(BulkAction.ASSIGN_LAYOUT, request.deviceIds(), null, null, null, null, null, layout.id(), null));
			}
			onTvs = request.deviceIds().size();
		}
		return new Result(template.id(), layout, createdPlaylists, createdWidgets, priceList, notes, onTvs);
	}

	// ---------- peças ----------

	private Media widget(List<Created> created, String name, WidgetKind kind, Map<String, Object> settings, int seconds) {
		Media media = widgets.create(new WidgetService.Request(name, kind.name(), settings, seconds));
		created.add(new Created(media.getId(), media.getName()));
		return media;
	}

	private static String label(Notice notice) {
		return switch (notice.key()) {
			case "welcome" -> "Boas-vindas";
			case "promo" -> "Promoção";
			case "special" -> "Prato do dia";
			case "notice" -> "Aviso";
			case "hours" -> "Horário";
			case "contact" -> "Contato";
			default -> notice.key();
		};
	}

	private static Map<String, Object> noticeSettings(Notice notice, Map<String, String> values) {
		Map<String, Object> settings = new LinkedHashMap<>();
		settings.put("title", SegmentTemplates.fill(notice.title(), values));
		settings.put("message", SegmentTemplates.fill(notice.message(), values));
		settings.put("accent", notice.accent());
		settings.put("theme", "dark");
		return settings;
	}

	private Created priceList(Template template, String business) {
		List<PriceListRequest.ItemRequest> items = new ArrayList<>();
		for (PriceDef def : template.prices()) {
			items.add(new PriceListRequest.ItemRequest(null, def.category(), def.name(), null, new BigDecimal(def.price()), def.unit(), true, false));
		}
		String name = uniqueName(business + " - " + template.priceTitle(), priceListRepository::existsByNameIgnoreCase);
		PriceList list = priceLists.create(new PriceListRequest(name, "BRL", "America/Sao_Paulo", items, List.of()));
		return new Created(list.getId(), list.getName());
	}

	/** Cria uma playlist com as mídias na ordem, cada uma pelo tempo que o widget já traz. */
	private Created playlist(List<Created> created, String name, List<Media> media) {
		List<PlaylistItemRequest> items = new ArrayList<>();
		for (Media item : media) {
			items.add(new PlaylistItemRequest(item.getId(), item.getDurationSeconds()));
		}
		var response = playlists.create(new PlaylistCreateRequest(name, null, items));
		Created playlist = new Created(response.id(), response.name());
		created.add(playlist);
		return playlist;
	}

	/**
	 * O layout do modelo: a tela principal à esquerda (72%) e uma faixa à direita com o relógio, a previsão do tempo (se houver) e os
	 * avisos fixos; em TV em pé a faixa vai para baixo. As zonas da faixa ficam sempre dentro da tela e sem se sobrepor.
	 */
	private Created layout(String business, Long clockPlaylist, Long weatherPlaylist, Long sidePlaylist) {
		boolean withWeather = weatherPlaylist != null;
		List<ZoneRequest> landscape = new ArrayList<>();
		List<ZoneRequest> portrait = new ArrayList<>();
		landscape.add(new ZoneRequest("Principal", 0, 0, 72, 100, true, null));
		portrait.add(new ZoneRequest("Principal", 0, 0, 100, withWeather ? 68 : 72, true, null));
		if (withWeather) {
			landscape.add(new ZoneRequest("Relógio", 72, 0, 28, 30, false, clockPlaylist));
			landscape.add(new ZoneRequest("Clima", 72, 30, 28, 35, false, weatherPlaylist));
			landscape.add(new ZoneRequest("Faixa lateral", 72, 65, 28, 35, false, sidePlaylist));
			portrait.add(new ZoneRequest("Relógio", 0, 68, 50, 16, false, clockPlaylist));
			portrait.add(new ZoneRequest("Clima", 50, 68, 50, 16, false, weatherPlaylist));
			portrait.add(new ZoneRequest("Faixa lateral", 0, 84, 100, 16, false, sidePlaylist));
		} else {
			landscape.add(new ZoneRequest("Relógio", 72, 0, 28, 35, false, clockPlaylist));
			landscape.add(new ZoneRequest("Faixa lateral", 72, 35, 28, 65, false, sidePlaylist));
			portrait.add(new ZoneRequest("Relógio", 0, 72, 100, 12, false, clockPlaylist));
			portrait.add(new ZoneRequest("Faixa lateral", 0, 84, 100, 16, false, sidePlaylist));
		}
		String name = uniqueName(business + " - Layout", layoutRepository::existsByNameIgnoreCase);
		var response = layouts.create(new LayoutRequest(name, "Criado pelo modelo pronto", landscape, portrait));
		return new Created(response.id(), response.name());
	}

	/** O nome livre: o pedido, ou o pedido com " 2", " 3"... se já existe (nomes de layout e de tabela são únicos). */
	private static String uniqueName(String base, Predicate<String> taken) {
		String name = base;
		for (int n = 2; taken.test(name) && n < 1000; n++) {
			name = base + " " + n;
		}
		return name;
	}
}
