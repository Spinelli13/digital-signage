package com.digitalsignage.backend.device;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.digitalsignage.backend.tenant.TenantContext;

/**
 * O último relatório de diagnóstico que cada TV mandou (internet, servidor, espaço, permissões...). Só o mais recente: serve ao
 * suporte, não é histórico.
 */
@Service
public class DeviceDiagnosticService {

	public static final int MAX_REPORT_CHARS = 4_000;
	public static final int MAX_ISSUES = 50;

	/** {@code report} e {@code receivedAt} nulos quando a TV nunca mandou um. */
	public record Diagnostic(String report, int issues, Instant receivedAt, Instant requestedAt) {
	}

	private final JdbcTemplate jdbc;

	public DeviceDiagnosticService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void save(long deviceId, String report, int issues, Instant now) {
		String text = report.length() <= MAX_REPORT_CHARS ? report : report.substring(0, MAX_REPORT_CHARS);
		jdbc.update("""
				INSERT INTO device_diagnostics (device_id, tenant_id, report, issues, received_at) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (device_id) DO UPDATE SET report = EXCLUDED.report, issues = EXCLUDED.issues, received_at = EXCLUDED.received_at
				""", deviceId, TenantContext.current(), text, Math.max(0, Math.min(MAX_ISSUES, issues)), Timestamp.from(now));
	}

	/** O último relatório da TV e quando o painel pediu o próximo (nulos se nunca houve). */
	public Diagnostic find(Device device) {
		Instant requested = device.getDiagnosticRequestedAt();
		return jdbc.query("SELECT report, issues, received_at FROM device_diagnostics WHERE device_id = ? AND tenant_id = ?",
				(rs, i) -> new Diagnostic(rs.getString("report"), rs.getInt("issues"), rs.getTimestamp("received_at").toInstant(), requested),
				device.getId(), TenantContext.current()).stream().findFirst().orElse(new Diagnostic(null, 0, null, requested));
	}
}
