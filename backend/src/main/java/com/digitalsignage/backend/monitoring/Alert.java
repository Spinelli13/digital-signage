package com.digitalsignage.backend.monitoring;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um problema detectado numa TV. Fica aberto até o problema passar. */
@Entity
@Table(name = "alerts")
public class Alert {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** O cliente dono da linha: o Hibernate preenche ao gravar e filtra em toda consulta (ver TenantContext). */
	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(name = "device_id", nullable = false)
	private Long deviceId;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String message;

	@Column(name = "opened_at", nullable = false)
	private Instant openedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "notified_at")
	private Instant notifiedAt;

	@Column(name = "resolution_notified_at")
	private Instant resolutionNotifiedAt;

	protected Alert() {
	}

	public Alert(Long deviceId, AlertType type, String message, Instant openedAt) {
		this.deviceId = deviceId;
		this.type = type.name();
		this.message = message;
		this.openedAt = openedAt;
	}

	public boolean isOpen() {
		return resolvedAt == null;
	}

	public void resolve(Instant at) {
		this.resolvedAt = at;
	}

	public void markNotified(Instant at) {
		this.notifiedAt = at;
	}

	public void markResolutionNotified(Instant at) {
		this.resolutionNotifiedAt = at;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public Long getId() {
		return id;
	}

	public Long getDeviceId() {
		return deviceId;
	}

	public AlertType getType() {
		return AlertType.valueOf(type);
	}

	public String getMessage() {
		return message;
	}

	public Instant getOpenedAt() {
		return openedAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public Instant getNotifiedAt() {
		return notifiedAt;
	}

	public Instant getResolutionNotifiedAt() {
		return resolutionNotifiedAt;
	}

}
