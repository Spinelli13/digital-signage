package com.digitalsignage.backend.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Usuário do painel. Os usuários não são isolados por cliente no Hibernate (o login procura pelo e-mail, que é único
 * em toda a plataforma); quem decide o que cada um enxerga é o {@code tenantId} dele.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	/** O cliente a que o usuário pertence (o do operador da plataforma é o cliente onde ele "mora"). */
	@Column(name = "tenant_id")
	private Long tenantId;

	@Column(nullable = false)
	private String role = Role.ADMIN.name();

	@Column(nullable = false)
	private boolean active = true;

	/** Senha provisória (definida por outra pessoa): até trocá-la, o usuário só consegue ver quem é e trocar a senha. */
	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String name, String email, String passwordHash, Long tenantId, Role role) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.tenantId = tenantId;
		this.role = role.name();
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public Role getRole() {
		return Role.valueOf(role);
	}

	public void setRole(Role role) {
		this.role = role.name();
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public void setMustChangePassword(boolean mustChangePassword) {
		this.mustChangePassword = mustChangePassword;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
