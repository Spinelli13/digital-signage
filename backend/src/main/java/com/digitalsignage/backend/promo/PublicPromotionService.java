package com.digitalsignage.backend.promo;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.digitalsignage.backend.common.RateLimitException;
import com.digitalsignage.backend.promo.PromotionModels.PublicCoupon;
import com.digitalsignage.backend.promo.PromotionModels.PublicPromotion;
import com.digitalsignage.backend.tenant.TenantContext;

/**
 * A área pública dos cupons (quem escaneia o QR Code da TV não tem login): o cliente (tenant) vem do segredo do endereço.
 * Só mostra o mínimo: a promoção e o PRÓPRIO cupom, nunca os de outras pessoas.
 *
 * <p>Este serviço NÃO é transacional de propósito (como o da fila): o cliente precisa ser definido antes de abrir a sessão do
 * banco, e quem abre é o {@link PromotionService}, já dentro do cliente certo.
 */
@Service
public class PublicPromotionService {

	/**
	 * Quantos cupons uma mesma rede (IP) pega por promoção dentro da janela. É alto porque o Wi-Fi dos visitantes de uma loja é UM
	 * endereço só para todos os clientes; o que segura quem tenta pegar vários é o limite de cupons da própria promoção.
	 */
	static final int MAX_PER_NETWORK = 60;
	static final long WINDOW_MS = 60 * 60 * 1000L;
	private static final int MAX_TRACKED_CLIENTS = 20_000;

	private final JdbcTemplate jdbc;
	private final PromotionService promotions;
	private final Clock clock;
	private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();

	/** O mesmo aparelho reabrindo a página dentro desta janela não conta de novo (recarregar a página não infla o número). */
	static final long SCAN_DEDUPE_MS = 10 * 60 * 1000L;

	private final java.time.ZoneId reportZone;
	private final Map<String, Long> recentScans = new ConcurrentHashMap<>();

	public PublicPromotionService(JdbcTemplate jdbc, PromotionService promotions, Clock clock,
			@Value("${app.monitoring.report-timezone:America/Sao_Paulo}") String reportZone) {
		this.jdbc = jdbc;
		this.promotions = promotions;
		this.clock = clock;
		this.reportZone = java.time.ZoneId.of(reportZone);
	}

	private record Owner(long tenantId, long id) {
	}

	/** Quem abre a página escaneou o QR Code: conta um escaneamento (por dia e origem) e devolve a promoção. */
	public PublicPromotion promotion(String token, String origin, String device, String network) {
		Owner owner = ownerOfPromotion(token);
		countScan(owner, ScanOrigin.clean(origin), device != null && device.matches("[0-9a-zA-Z-]{8,64}") ? device : network);
		return TenantContext.callAs(owner.tenantId(), () -> promotions.publicView(owner.id()));
	}

	/** Um número por dia e origem, sem dado de pessoa. Nunca derruba a abertura da página: o aviso da promoção vale mais que o contador. */
	private void countScan(Owner owner, String origin, String who) {
		long now = clock.millis();
		if (recentScans.size() > MAX_TRACKED_CLIENTS) {
			recentScans.clear();
		}
		String key = owner.id() + "|" + origin + "|" + who;
		Long last = recentScans.get(key);
		if (last != null && now - last < SCAN_DEDUPE_MS) {
			return;
		}
		recentScans.put(key, now);
		try {
			jdbc.update("INSERT INTO promotion_scans (promotion_id, tenant_id, day, origin, scans) VALUES (?, ?, ?, ?, 1) "
					+ "ON CONFLICT (promotion_id, day, origin) DO UPDATE SET scans = promotion_scans.scans + 1",
					owner.id(), owner.tenantId(), java.sql.Date.valueOf(clock.instant().atZone(reportZone).toLocalDate()), origin);
		} catch (RuntimeException e) {
			// o contador falhou: a pessoa ainda vê a promoção
		}
	}

	public PublicCoupon claim(String token, String customerName, boolean consented, String network, String device, String origin) {
		Owner owner = ownerOfPromotion(token);
		String clientId = device != null && device.matches("[0-9a-zA-Z-]{8,64}") ? device : null;
		return TenantContext.callAs(owner.tenantId(), () -> {
			if (clientId != null) {
				// quem volta a escanear com o mesmo aparelho recebe o mesmo cupom, sem gastar o limite da rede
				var existing = promotions.existing(owner.id(), clientId);
				if (existing.isPresent()) {
					return existing.get();
				}
			}
			throttle(token + "|net|" + network, MAX_PER_NETWORK);
			try {
				return promotions.claim(owner.id(), customerName, consented, clientId, origin);
			} catch (DataIntegrityViolationException e) {
				// dois pedidos do mesmo aparelho ao mesmo tempo: o outro já criou o cupom
				return promotions.existing(owner.id(), clientId).orElseThrow(() -> e);
			}
		});
	}

	public PublicCoupon coupon(String couponToken) {
		Owner owner = ownerOfCoupon(couponToken);
		return TenantContext.callAs(owner.tenantId(), () -> promotions.publicCoupon(owner.id()));
	}

	private Owner ownerOfPromotion(String token) {
		return lookup("SELECT tenant_id, id FROM promotions WHERE public_token = ?", token, "Este QR Code não é válido. Procure o atendimento da loja.");
	}

	private Owner ownerOfCoupon(String token) {
		return lookup("SELECT tenant_id, id FROM promotion_coupons WHERE public_token = ?", token, "Este cupom não foi encontrado.");
	}

	private Owner lookup(String sql, String token, String notFound) {
		// Só 32 caracteres hexadecimais: qualquer outra coisa nem vai ao banco.
		if (token == null || !token.matches("[0-9a-f]{32}")) {
			throw new NoSuchElementException(notFound);
		}
		try {
			return jdbc.queryForObject(sql, (rs, row) -> new Owner(rs.getLong("tenant_id"), rs.getLong("id")), token);
		} catch (EmptyResultDataAccessException e) {
			throw new NoSuchElementException(notFound);
		}
	}

	private void throttle(String key, int limit) {
		long now = clock.millis();
		if (recent.size() > MAX_TRACKED_CLIENTS) {
			recent.clear();
		}
		Deque<Long> times = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
				times.pollFirst();
			}
			if (times.size() >= limit) {
				throw new RateLimitException("Muitos cupons pegos nesta rede por hora. Procure o atendimento da loja.");
			}
			times.addLast(now);
		}
	}

	/** Para os testes. */
	public void forgetClients() {
		recent.clear();
		recentScans.clear();
	}

}
