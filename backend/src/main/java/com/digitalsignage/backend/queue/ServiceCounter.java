package com.digitalsignage.backend.queue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Um guichê (balcão, mesa, sala): de onde o atendente chama as senhas das filas que ele atende. */
@Entity
@Table(name = "service_counters")
public class ServiceCounter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(nullable = false)
	private String name;

	/** As filas que este guichê atende; vazio = todas. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "queue_ids", nullable = false, columnDefinition = "jsonb")
	private List<Long> queueIds = new ArrayList<>();

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ServiceCounter() {
	}

	public ServiceCounter(String name, List<Long> queueIds) {
		this.name = name;
		this.queueIds = new ArrayList<>(queueIds);
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

	public void change(String name, List<Long> queueIds, boolean active) {
		this.name = name;
		this.queueIds = new ArrayList<>(queueIds);
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<Long> getQueueIds() {
		return queueIds;
	}

	public boolean isActive() {
		return active;
	}

}
