package com.digitalsignage.backend.security;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Descobre de quem é uma TV a partir do token dela. É uma das pouquíssimas consultas que atravessam os clientes
 * de propósito (por isso é SQL direto, fora do isolamento do Hibernate): antes de saber o cliente da TV não há
 * como consultá-la "dentro" de um cliente. Só devolve o necessário para o filtro definir o cliente da requisição.
 */
@Component
public class DeviceLookup {

	public record DeviceRef(long id, long tenantId, String status, String tenantStatus) {
	}

	private final JdbcTemplate jdbc;

	public DeviceLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<DeviceRef> byTokenHash(String tokenHash) {
		return jdbc.query("""
				SELECT d.id, d.tenant_id, d.status, t.status AS tenant_status
				FROM devices d JOIN tenants t ON t.id = d.tenant_id
				WHERE d.device_token_hash = ?
				""", (rs, i) -> new DeviceRef(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("status"), rs.getString("tenant_status")),
				tokenHash).stream().findFirst();
	}

}
