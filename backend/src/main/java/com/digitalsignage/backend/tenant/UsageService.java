package com.digitalsignage.backend.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Quanto um cliente está usando do plano. SQL direto com o cliente informado (não depende do cliente "atual"): a
 * operação da plataforma consulta vários clientes na mesma requisição.
 */
@Service
public class UsageService {

	/** TVs ativas ou desativadas (as que ainda aguardam ativação não contam), espaço das mídias em bytes e usuários ativos. */
	public record Usage(long devices, long storageBytes, long users) {
	}

	private final JdbcTemplate jdbc;

	public UsageService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Usage of(long tenantId) {
		long devices = jdbc.queryForObject("SELECT count(*) FROM devices WHERE tenant_id = ? AND status <> 'PENDING'", Long.class, tenantId);
		long storage = jdbc.queryForObject("SELECT COALESCE(SUM(file_size), 0) FROM media WHERE tenant_id = ?", Long.class, tenantId);
		long users = jdbc.queryForObject("SELECT count(*) FROM users WHERE tenant_id = ? AND active", Long.class, tenantId);
		return new Usage(devices, storage, users);
	}

}
