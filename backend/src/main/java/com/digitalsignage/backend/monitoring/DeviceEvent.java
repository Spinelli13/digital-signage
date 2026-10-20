package com.digitalsignage.backend.monitoring;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Uma linha da história de uma TV. */
@Entity
@Table(name = "device_events")
public class DeviceEvent {

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
	private String level;

	@Column(nullable = false)
	private String message;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DeviceEvent() {
	}

	public DeviceEvent(Long deviceId, EventType type, EventLevel level, String message, Instant occurredAt) {
		this.deviceId = deviceId;
		this.type = type.name();
		this.level = level.name();
		this.message = message;
		this.occurredAt = occurredAt;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
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

	public String getType() {
		return type;
	}

	public String getLevel() {
		return level;
	}

	public String getMessage() {
		return message;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
