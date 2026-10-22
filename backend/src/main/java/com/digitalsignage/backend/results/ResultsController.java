package com.digitalsignage.backend.results;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.monitoring.CsvWriter;
import com.digitalsignage.backend.monitoring.MonitoringService;
import com.digitalsignage.backend.plan.Feature;
import com.digitalsignage.backend.security.CurrentUser;
import com.digitalsignage.backend.tenant.PlanLimits;
import com.digitalsignage.backend.tenant.TenantContext;

/**
 * "Resultados": o painel simples do cliente (o conteúdo rodou X vezes, Y cupons foram usados) e o CSV para abrir no Excel. Qualquer
 * papel pode ver. Sem recurso de plano próprio: os números de cupom só existem para quem usa cupons.
 */
@RestController
@RequestMapping("/api/v1/results")
public class ResultsController {

	private static final int MAX_RANGE_DAYS = 366;

	/** O relatório mais um aviso de que o plano não inclui cupons (a tela esconde essa parte). */
	public record Response(boolean couponsEnabled, ResultsService.Report report) {
	}

	private final ResultsService service;
	private final MonitoringService monitoring;
	private final PlanLimits limits;
	private final Clock clock;

	public ResultsController(ResultsService service, MonitoringService monitoring, PlanLimits limits, Clock clock) {
		this.service = service;
		this.monitoring = monitoring;
		this.limits = limits;
		this.clock = clock;
	}

	@GetMapping
	public Object results(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long promotionId,
			@RequestParam(required = false) String format,
			@RequestParam(required = false, defaultValue = "day") String by) {
		ZoneId zone = monitoring.reportZone();
		LocalDate last = to == null ? clock.instant().atZone(zone).toLocalDate() : to;
		LocalDate first = from == null ? last.minusDays(29) : from;
		if (first.isAfter(last)) {
			throw new IllegalArgumentException("A data inicial não pode ser depois da data final");
		}
		if (first.plusDays(MAX_RANGE_DAYS).isBefore(last)) {
			throw new IllegalArgumentException("O período pode ter no máximo " + MAX_RANGE_DAYS + " dias");
		}
		ResultsService.Report report = service.report(first, last, zone, promotionId);
		if ("csv".equalsIgnoreCase(format)) {
			return csv(report, by);
		}
		boolean coupons = CurrentUser.get().map(user -> user.isPlatformAdmin() || limits.includes(TenantContext.current(), Feature.COUPONS)).orElse(true);
		return new Response(coupons, report);
	}

	private static ResponseEntity<byte[]> csv(ResultsService.Report report, String by) {
		CsvWriter csv = new CsvWriter();
		String name;
		if ("tv".equalsIgnoreCase(by)) {
			name = "resultados-por-tv-";
			csv.row("TV ou origem do QR Code", "Exibições do conteúdo", "QR Codes escaneados", "Cupons pegos", "Cupons usados");
			report.tvs().forEach(tv -> csv.row(tv.name() == null ? "TV excluída" : tv.name(), tv.plays(), tv.scans(), tv.claimed(), tv.redeemed()));
			report.origins().forEach(origin -> csv.row(origin.origin().isEmpty() ? "QR Code geral (sem origem)" : "QR Code: " + origin.origin(), "", origin.scans(),
					origin.claimed(), origin.redeemed()));
		} else if ("promotion".equalsIgnoreCase(by)) {
			name = "resultados-por-promocao-";
			csv.row("Promoção", "QR Codes escaneados", "Cupons pegos", "Cupons usados");
			report.promotions().forEach(promotion -> csv.row(promotion.name(), promotion.scans(), promotion.claimed(), promotion.redeemed()));
		} else {
			name = "resultados-por-dia-";
			csv.row("Dia", "Exibições do conteúdo", "QR Codes escaneados", "Cupons pegos", "Cupons usados");
			report.days().forEach(day -> csv.row(day.day(), day.plays(), day.scans(), day.claimed(), day.redeemed()));
		}
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name + report.from() + "-a-" + report.to() + ".csv").build().toString())
				.body(csv.bytes());
	}
}
