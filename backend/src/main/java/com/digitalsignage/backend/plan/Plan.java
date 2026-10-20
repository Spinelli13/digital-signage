package com.digitalsignage.backend.plan;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Um plano de assinatura. Limite nulo = sem limite. */
@Entity
@Table(name = "plans")
public class Plan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(name = "price_cents", nullable = false)
	private int priceCents;

	@Column(nullable = false)
	private String currency = "BRL";

	@Column(name = "max_devices")
	private Integer maxDevices;

	@Column(name = "max_storage_mb")
	private Integer maxStorageMb;

	@Column(name = "max_users")
	private Integer maxUsers;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Plan() {
	}

	public Plan(String code, String name, String description, int priceCents, Integer maxDevices, Integer maxStorageMb, Integer maxUsers) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.priceCents = priceCents;
		this.maxDevices = maxDevices;
		this.maxStorageMb = maxStorageMb;
		this.maxUsers = maxUsers;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getPriceCents() {
		return priceCents;
	}

	public void setPriceCents(int priceCents) {
		this.priceCents = priceCents;
	}

	public String getCurrency() {
		return currency;
	}

	public Integer getMaxDevices() {
		return maxDevices;
	}

	public void setMaxDevices(Integer maxDevices) {
		this.maxDevices = maxDevices;
	}

	public Integer getMaxStorageMb() {
		return maxStorageMb;
	}

	public void setMaxStorageMb(Integer maxStorageMb) {
		this.maxStorageMb = maxStorageMb;
	}

	public Integer getMaxUsers() {
		return maxUsers;
	}

	public void setMaxUsers(Integer maxUsers) {
		this.maxUsers = maxUsers;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

}
