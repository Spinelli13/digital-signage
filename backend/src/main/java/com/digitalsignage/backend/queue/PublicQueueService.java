package com.digitalsignage.backend.queue;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.digitalsignage.backend.common.RateLimitException;
import com.digitalsignage.backend.queue.AttendanceModels.PublicQueue;
import com.digitalsignage.backend.queue.AttendanceModels.PublicTicket;
import com.digitalsignage.backend.tenant.TenantContext;

/**
 * A área pública da fila (senha pelo celular): quem escaneia o QR Code não tem login, então o cliente (tenant) vem do segredo
 * do endereço. Só mostra o mínimo: o nome da fila, quantos esperam e a posição da PRÓPRIA senha, nunca dados de outras pessoas.
 *
 * <p>Este serviço NÃO é transacional de propósito: o cliente precisa ser definido antes de abrir a sessão do banco, e quem
 * abre é o {@link AttendanceService}, já dentro do cliente certo.
 */
@Service
public class PublicQueueService {

	/**
	 * Quantas senhas um mesmo aparelho (o código que a página guarda no navegador) pode pegar por fila dentro da janela, e quantas
	 * uma mesma rede (IP). A rede tem um teto alto porque o Wi-Fi de visitantes de uma loja é UM endereço só para todos os clientes.
	 */
	static final int MAX_PER_DEVICE = 4;
	static final int MAX_PER_NETWORK = 60;
	static final long WINDOW_MS = 60 * 60 * 1000L;
	private static final int MAX_TRACKED_CLIENTS = 20_000;

	private final JdbcTemplate jdbc;
	private final AttendanceService attendance;
	private final Clock clock;
	private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();

	public PublicQueueService(JdbcTemplate jdbc, AttendanceService attendance, Clock clock) {
		this.jdbc = jdbc;
		this.attendance = attendance;
		this.clock = clock;
	}

	private record Owner(long tenantId, long id) {
	}

	public PublicQueue queue(String queueToken) {
		Owner owner = ownerOfQueue(queueToken);
		return TenantContext.callAs(owner.tenantId(), () -> attendance.publicQueue(owner.id()));
	}

	public PublicTicket issue(String queueToken, String customerName, boolean consented, String network, String device) {
		Owner owner = ownerOfQueue(queueToken);
		throttle(queueToken + "|net|" + network, MAX_PER_NETWORK);
		if (device != null && device.matches("[0-9a-zA-Z-]{8,64}")) {
			throttle(queueToken + "|dev|" + device, MAX_PER_DEVICE);
		}
		return TenantContext.callAs(owner.tenantId(), () -> attendance.issueRemote(owner.id(), customerName, consented));
	}

	public PublicTicket ticket(String ticketToken) {
		Owner owner = ownerOfTicket(ticketToken);
		return TenantContext.callAs(owner.tenantId(), () -> attendance.publicTicket(owner.id()));
	}

	public PublicTicket cancel(String ticketToken) {
		Owner owner = ownerOfTicket(ticketToken);
		return TenantContext.callAs(owner.tenantId(), () -> attendance.cancelRemote(owner.id()));
	}

	private Owner ownerOfQueue(String token) {
		return lookup("SELECT tenant_id, id FROM service_queues WHERE public_token = ?", token, "Este QR Code não é válido. Procure o atendimento.");
	}

	private Owner ownerOfTicket(String token) {
		return lookup("SELECT tenant_id, id FROM tickets WHERE public_token = ?", token, "Esta senha não foi encontrada. Ela pode ter expirado.");
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

	/** No máximo {@code limit} senhas por hora para esta chave (rede ou aparelho, sempre dentro de uma fila). */
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
				throw new RateLimitException("Você já pegou senhas demais por hora. Procure o atendimento.");
			}
			times.addLast(now);
		}
	}

	/** Para os testes. */
	public void forgetClients() {
		recent.clear();
	}

	List<String> trackedKeys() {
		return List.copyOf(recent.keySet());
	}

}
