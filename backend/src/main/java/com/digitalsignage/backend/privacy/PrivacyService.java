package com.digitalsignage.backend.privacy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.monitoring.MonitoringSettings;
import com.digitalsignage.backend.monitoring.MonitoringSettingsRepository;
import com.digitalsignage.backend.promo.Promotion;
import com.digitalsignage.backend.promo.PromotionCoupon;
import com.digitalsignage.backend.promo.PromotionCouponRepository;
import com.digitalsignage.backend.promo.PromotionRepository;
import com.digitalsignage.backend.queue.ServiceQueue;
import com.digitalsignage.backend.queue.ServiceQueueRepository;
import com.digitalsignage.backend.queue.Ticket;
import com.digitalsignage.backend.queue.TicketRepository;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantContext;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.user.User;
import com.digitalsignage.backend.user.UserRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * A LGPD no dia a dia do cliente (a loja): os prazos em que os nomes são apagados, o aviso mostrado às pessoas e o atendimento
 * dos pedidos de titulares (achar, exportar e apagar os dados de uma pessoa). Tudo dentro do cliente em que a requisição atua:
 * nunca enxerga dados de outro.
 */
@Service
public class PrivacyService {

	public static final int MIN_TERM = 3;
	static final int MAX_RESULTS = 200;
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	/** Campos ausentes não mudam. Os dois últimos são os mesmos prazos da tela de alertas (histórico das TVs e exibições). */
	public record SettingsRequest(
			@Size(max = 120) String contactName,
			@Size(max = 255) String contactEmail,
			@Min(1) @Max(365) Integer ticketRetentionDays,
			@Min(1) @Max(365) Integer couponNameRetentionDays,
			@Min(7) @Max(730) Integer eventsRetentionDays,
			@Min(7) @Max(365) Integer playLogRetentionDays) {
	}

	public record Settings(String contactName, String contactEmail, int ticketRetentionDays, int couponNameRetentionDays,
			int eventsRetentionDays, int playLogRetentionDays) {
	}

	public record TicketMatch(Long id, String queue, String label, LocalDate day, String name, String source, String status, Instant issuedAt,
			Instant consentAt) {
	}

	public record CouponMatch(Long id, String code, String promotion, String name, String status, Instant claimedAt, Instant expiresAt,
			Instant redeemedAt, Instant consentAt) {
	}

	public record UserMatch(Long id, String name, String email, String role, boolean active, Instant createdAt) {
	}

	public record SearchResult(List<TicketMatch> tickets, List<CouponMatch> coupons, List<UserMatch> users, boolean truncated) {

		public int total() {
			return tickets.size() + coupons.size() + users.size();
		}
	}

	public record EraseResult(int tickets, int coupons, int usersFound) {
	}

	public record RequestEntry(Long id, String kind, int tickets, int coupons, int users, String note, String performedBy, Instant performedAt) {
	}

	private final PrivacySettingsRepository settingsRepository;
	private final PrivacyRequestLogRepository requestLog;
	private final MonitoringSettingsRepository monitoringSettings;
	private final TicketRepository tickets;
	private final ServiceQueueRepository queues;
	private final PromotionCouponRepository coupons;
	private final PromotionRepository promotions;
	private final UserRepository users;
	private final TenantRepository tenants;
	private final Clock clock;

	public PrivacyService(PrivacySettingsRepository settingsRepository, PrivacyRequestLogRepository requestLog,
			MonitoringSettingsRepository monitoringSettings, TicketRepository tickets, ServiceQueueRepository queues,
			PromotionCouponRepository coupons, PromotionRepository promotions, UserRepository users, TenantRepository tenants, Clock clock) {
		this.settingsRepository = settingsRepository;
		this.requestLog = requestLog;
		this.monitoringSettings = monitoringSettings;
		this.tickets = tickets;
		this.queues = queues;
		this.coupons = coupons;
		this.promotions = promotions;
		this.users = users;
		this.tenants = tenants;
		this.clock = clock;
	}

	// ---------- configurações ----------

	private PrivacySettings current() {
		return settingsRepository.findAll().stream().findFirst().orElseGet(PrivacySettings::new);
	}

	private MonitoringSettings monitoring() {
		return monitoringSettings.findAll().stream().findFirst().orElseGet(MonitoringSettings::new);
	}

	@Transactional(readOnly = true)
	public Settings settings() {
		PrivacySettings privacy = current();
		MonitoringSettings monitoring = monitoring();
		return new Settings(privacy.getContactName(), privacy.getContactEmail(), privacy.getTicketRetentionDays(),
				privacy.getCouponNameRetentionDays(), monitoring.getEventsRetentionDays(), monitoring.getPlayLogRetentionDays());
	}

	@Transactional
	public Settings update(SettingsRequest request) {
		PrivacySettings privacy = current();
		if (request.contactName() != null) {
			privacy.setContactName(blankToNull(request.contactName()));
		}
		if (request.contactEmail() != null) {
			String email = blankToNull(request.contactEmail());
			if (email != null && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
				throw new IllegalArgumentException("O e-mail de contato não parece válido");
			}
			privacy.setContactEmail(email);
		}
		if (request.ticketRetentionDays() != null) {
			privacy.setTicketRetentionDays(request.ticketRetentionDays());
		}
		if (request.couponNameRetentionDays() != null) {
			privacy.setCouponNameRetentionDays(request.couponNameRetentionDays());
		}
		settingsRepository.save(privacy);
		if (request.eventsRetentionDays() != null || request.playLogRetentionDays() != null) {
			MonitoringSettings monitoring = monitoring();
			if (request.eventsRetentionDays() != null) {
				monitoring.setEventsRetentionDays(request.eventsRetentionDays());
			}
			if (request.playLogRetentionDays() != null) {
				monitoring.setPlayLogRetentionDays(request.playLogRetentionDays());
			}
			monitoringSettings.save(monitoring);
		}
		return settings();
	}

	/** Dias que a senha (com o nome de quem esperou) fica guardada neste cliente. */
	@Transactional(readOnly = true)
	public int ticketRetentionDays() {
		return current().getTicketRetentionDays();
	}

	/** O aviso da senha pelo celular: o responsável (a loja), o canal de contato e por quantos dias o nome fica. */
	@Transactional(readOnly = true)
	public PrivacyNotice.Info ticketNotice() {
		PrivacySettings privacy = current();
		return new PrivacyNotice.Info(controllerName(), privacy.getContactEmail(), privacy.getTicketRetentionDays(), PrivacyNotice.VERSION);
	}

	/** O aviso do cupom: o mesmo, com o prazo dos nomes dos cupons. */
	@Transactional(readOnly = true)
	public PrivacyNotice.Info couponNotice() {
		PrivacySettings privacy = current();
		return new PrivacyNotice.Info(controllerName(), privacy.getContactEmail(), privacy.getCouponNameRetentionDays(), PrivacyNotice.VERSION);
	}

	@Transactional(readOnly = true)
	public int couponNameRetentionDays() {
		return current().getCouponNameRetentionDays();
	}

	private String controllerName() {
		return tenants.findById(TenantContext.current()).map(Tenant::getName).orElse("");
	}

	// ---------- prazos: o que passou do tempo é apagado ----------

	/**
	 * Apaga o que passou do prazo deste cliente: senhas antigas e os nomes dos cupons. Roda de hora em hora (junto da limpeza do
	 * monitoramento) e também no que já existia, de modo que a guarda não depende de alguém emitir senha ou cupom.
	 */
	@Transactional
	public int purgeExpired(Instant now) {
		PrivacySettings privacy = current();
		LocalDate cutoff = LocalDate.ofInstant(now, ZONE).minusDays(privacy.getTicketRetentionDays());
		int removedTickets = tickets.deleteBefore(cutoff);
		int forgottenNames = coupons.forgetNamesBefore(now.minus(Duration.ofDays(privacy.getCouponNameRetentionDays())));
		return removedTickets + forgottenNames;
	}

	// ---------- pedidos de titulares ----------

	@Transactional(readOnly = true)
	public SearchResult search(String rawTerm) {
		String term = requireTerm(rawTerm);
		return find(term);
	}

	private SearchResult find(String term) {
		String like = escapeLike(term);
		var page = PageRequest.of(0, MAX_RESULTS + 1);
		List<Ticket> foundTickets = tickets.withNameLike(like, page);
		List<PromotionCoupon> foundCoupons = coupons.forPrivacy(like, couponCode(term), page);
		List<User> foundUsers = users.forPrivacy(TenantContext.current(), like);
		boolean truncated = foundTickets.size() > MAX_RESULTS || foundCoupons.size() > MAX_RESULTS;

		Instant now = clock.instant();
		Map<Long, String> queueNames = names(queues.findAll(), ServiceQueue::getId, ServiceQueue::getName);
		Map<Long, String> promotionNames = names(promotions.findAll(), Promotion::getId, Promotion::getName);
		return new SearchResult(
				foundTickets.stream().limit(MAX_RESULTS)
						.map(t -> new TicketMatch(t.getId(), queueNames.getOrDefault(t.getQueueId(), ""), t.getLabel(), t.getDay(), t.getCustomerName(),
								t.getSource(), t.getStatus().name(), t.getIssuedAt(), t.getConsentAt()))
						.toList(),
				foundCoupons.stream().limit(MAX_RESULTS)
						.map(c -> new CouponMatch(c.getId(), c.getCode(), promotionNames.getOrDefault(c.getPromotionId(), ""), c.getClaimantName(),
								c.statusAt(now), c.getClaimedAt(), c.getExpiresAt(), c.getRedeemedAt(), c.getConsentAt()))
						.toList(),
				foundUsers.stream().map(u -> new UserMatch(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive(), u.getCreatedAt())).toList(),
				truncated);
	}

	private static <T> Map<Long, String> names(List<T> items, Function<T, Long> id, Function<T, String> name) {
		Map<Long, String> map = new LinkedHashMap<>();
		items.forEach(item -> map.put(id.apply(item), name.apply(item)));
		return map;
	}

	/** A cópia dos dados da pessoa (tudo o que está guardado sobre ela neste cliente), pronta para entregar. O pedido fica registrado. */
	@Transactional
	public Map<String, Object> export(String rawTerm, String note, String by) {
		String term = requireTerm(rawTerm);
		SearchResult result = find(term);
		Instant now = clock.instant();

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("geradoEm", now.toString());
		out.put("controlador", controllerName());
		out.put("termoBuscado", term);
		out.put("observacao", "Cópia dos dados pessoais encontrados neste cliente. Senhas de acesso nunca são exportadas.");
		out.put("senhasDaFila", result.tickets().stream().map(t -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("fila", t.queue());
			row.put("senha", t.label());
			row.put("dia", t.day().toString());
			row.put("nome", t.name());
			row.put("origem", t.source());
			row.put("situacao", t.status());
			row.put("emitidaEm", str(t.issuedAt()));
			row.put("concordouComOAvisoEm", str(t.consentAt()));
			return row;
		}).toList());
		out.put("cupons", result.coupons().stream().map(c -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("promocao", c.promotion());
			row.put("codigo", c.code());
			row.put("nome", c.name());
			row.put("situacao", c.status());
			row.put("pegoEm", str(c.claimedAt()));
			row.put("validoAte", str(c.expiresAt()));
			row.put("resgatadoEm", str(c.redeemedAt()));
			row.put("concordouComOAvisoEm", str(c.consentAt()));
			return row;
		}).toList());
		out.put("usuariosDoPainel", result.users().stream().map(u -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("nome", u.name());
			row.put("email", u.email());
			row.put("papel", u.role());
			row.put("ativo", u.active());
			row.put("criadoEm", str(u.createdAt()));
			return row;
		}).toList());
		requestLog.save(new PrivacyRequestLog(PrivacyRequestLog.Kind.EXPORT, result.tickets().size(), result.coupons().size(), result.users().size(),
				blankToNull(note), by, now));
		return out;
	}

	/**
	 * Apaga os dados pessoais encontrados: o nome (e a concordância) nas senhas e nos cupons, e o aparelho nos cupons. As senhas e os
	 * cupons ficam sem nada que identifique a pessoa, para os números do atendimento. Usuários do painel não são apagados aqui:
	 * a exclusão deles é feita em Equipe (a resposta diz quantos foram encontrados).
	 */
	@Transactional
	public EraseResult erase(String rawTerm, String note, String by) {
		String term = requireTerm(rawTerm);
		SearchResult result = find(term);
		List<Long> ticketIds = result.tickets().stream().map(TicketMatch::id).toList();
		List<Long> couponIds = result.coupons().stream().map(CouponMatch::id).toList();
		int erasedTickets = ticketIds.isEmpty() ? 0 : tickets.forgetPersonalData(ticketIds);
		int erasedCoupons = couponIds.isEmpty() ? 0 : coupons.forgetPersonalData(couponIds);
		requestLog.save(new PrivacyRequestLog(PrivacyRequestLog.Kind.ERASE, erasedTickets, erasedCoupons, 0, blankToNull(note), by, clock.instant()));
		return new EraseResult(erasedTickets, erasedCoupons, result.users().size());
	}

	@Transactional(readOnly = true)
	public List<RequestEntry> requests() {
		List<RequestEntry> entries = new ArrayList<>();
		for (PrivacyRequestLog log : requestLog.latest(PageRequest.of(0, 100))) {
			entries.add(new RequestEntry(log.getId(), log.getKind().name(), log.getTickets(), log.getCoupons(), log.getUsers(), log.getNote(),
					log.getPerformedBy(), log.getPerformedAt()));
		}
		return entries;
	}

	// ---------- utilidades ----------

	private static String requireTerm(String raw) {
		String term = raw == null ? "" : raw.strip();
		if (term.length() < MIN_TERM) {
			throw new IllegalArgumentException("Digite pelo menos " + MIN_TERM + " caracteres para buscar");
		}
		if (term.length() > 120) {
			throw new IllegalArgumentException("O texto buscado aceita no máximo 120 caracteres");
		}
		return term;
	}

	/** Os curingas do LIKE (%, _ e a barra) valem como texto comum. */
	static String escapeLike(String term) {
		return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** "abcd 2345", "ABCD-2345" e "abcd2345" são o mesmo código de cupom; qualquer outra coisa não é um código. */
	static String couponCode(String term) {
		String letters = term.toUpperCase().replaceAll("[^A-Z0-9]", "");
		return letters.length() == 8 ? letters.substring(0, 4) + "-" + letters.substring(4) : "-";
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static String str(Instant instant) {
		return instant == null ? null : instant.toString();
	}

}
