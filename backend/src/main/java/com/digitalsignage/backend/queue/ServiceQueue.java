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

	/** Aberta a quem escaneia o QR Code da fila (senha pelo celular). */
	@Column(name = "remote_enabled", nullable = false)
	private boolean remoteEnabled;

	/** O segredo que vai no QR Code: quem o tem pode pegar senha desta fila (quando a senha pelo celular está ligada). */
	@Column(name = "public_token", nullable = false, updatable = false)
	private String publicToken = Tokens.random();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ServiceQueue() {
	}

	public ServiceQueue(String name, String prefix, boolean priority, String timezone, boolean remoteEnabled) {
		this.name = name;
		this.prefix = prefix;
		this.priority = priority;
		this.timezone = timezone;
		this.remoteEnabled = remoteEnabled;
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

	public void change(String name, String prefix, boolean priority, boolean active, String timezone, boolean remoteEnabled) {
		this.name = name;
		this.prefix = prefix;
		this.priority = priority;
		this.active = active;
		this.timezone = timezone;
		this.remoteEnabled = remoteEnabled;
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

	public boolean isRemoteEnabled() {
		return remoteEnabled;
	}

	public String getPublicToken() {
		return publicToken;
	}

}
