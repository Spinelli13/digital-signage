package com.digitalsignage.backend.queue;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Uma fila de atendimento (ex.: "Atendimento geral", "Prioritário"): a letra da senha e se tem prioridade. */
@Entity
@Table(name = "service_queues")
public class ServiceQueue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, length = 3)
	private String prefix;

	@Column(nullable = false)
	private boolean priority;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false)
	private String timezone = "America/Sao_Paulo";

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ServiceQueue() {
	}

	public ServiceQueue(String name, String prefix, boolean priority, String timezone) {
		this.name = name;
		this.prefix = prefix;
		this.priority = priority;
		this.timezone = timezone;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public void change(String name, String prefix, boolean priority, boolean active, String timezone) {
		this.name = name;
		this.prefix = prefix;
		this.priority = priority;
		this.active = active;
		this.timezone = timezone;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getPrefix() {
		return prefix;
	}

	public boolean isPriority() {
		return priority;
	}

	public boolean isActive() {
		return active;
	}

	public String getTimezone() {
		return timezone;
	}

}
