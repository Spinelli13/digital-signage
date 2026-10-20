package com.digitalsignage.backend.layout;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Como a tela da TV é dividida em zonas (ex.: conteúdo principal + faixa lateral + rodapé). */
@Entity
@Table(name = "layouts")
public class Layout {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** O cliente dono da linha: o Hibernate preenche ao gravar e filtra em toda consulta (ver TenantContext). */
	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(nullable = false)
	private String name;

	private String description;

	/** Sobe a cada mudança nas zonas: faz o Player baixar a configuração de novo. */
	@Column(name = "config_version", nullable = false)
	private long configVersion = 1;

	@OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@OrderBy("position ASC")
	private List<LayoutZone> zones = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Layout() {
	}

	public Layout(String name, String description) {
		this.name = name;
		this.description = description;
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

	public void replaceZones(List<LayoutZone> newZones) {
		zones.clear();
		int position = 0;
		for (LayoutZone zone : newZones) {
			zone.assignTo(this, position++);
			zones.add(zone);
		}
		configVersion++;
	}

	public LayoutZone mainZone() {
		return zones.stream().filter(LayoutZone::isMain).findFirst().orElse(null);
	}

	public Long getTenantId() {
		return tenantId;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public long getConfigVersion() {
		return configVersion;
	}

	public List<LayoutZone> getZones() {
		return zones;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
