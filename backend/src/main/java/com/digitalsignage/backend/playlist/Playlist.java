package com.digitalsignage.backend.playlist;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import org.hibernate.annotations.TenantId;

import com.digitalsignage.backend.device.ConfigVersions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "playlists")
public class Playlist {

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

	@Column(name = "config_version", nullable = false)
	private long configVersion = 0;

	@OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<PlaylistItem> items = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Playlist() {
	}

	public Playlist(String name, String description) {
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

	/** Algo que a playlist exibe mudou por dentro (ex.: o texto de um widget): as TVs precisam baixar a configuração de novo. */
	public void touch() {
		configVersion++;
	}

	public void replaceItems(List<PlaylistItem> newItems) {
		items.clear();
		for (PlaylistItem item : newItems) {
			item.assignTo(this);
			items.add(item);
		}
		configVersion++;
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

	/** Algum item tem validade (datas ou dias)? Se não, a playlist é a de sempre e nada depende do dia. */
	public boolean hasValidityRules() {
		return items.stream().anyMatch(PlaylistItem::hasRules);
	}

	/** Os itens que valem neste dia, na ordem da playlist (os sem regra valem sempre). */
	public List<PlaylistItem> itemsValidOn(LocalDate day) {
		return items.stream().filter(item -> item.isValidOn(day)).toList();
	}

	/**
	 * A versão do que a TV deve exibir neste dia: a versão da playlist e, só quando algum item tem validade, uma marca de quais
	 * itens valem hoje. Muda sozinha na virada do dia em que um item entra ou sai do ar, e a TV baixa a configuração de novo. Sem
	 * nenhuma validade é exatamente {@link #getConfigVersion()}: nenhuma playlist existente é reconfigurada por causa disto.
	 */
	public long contentVersion(LocalDate day) {
		if (!hasValidityRules()) {
			return configVersion;
		}
		long hash = 1_099_511_628_211L;
		for (PlaylistItem item : items) {
			hash = 31 * hash + (item.isValidOn(day) ? item.getPosition() + 1 : 0);
		}
		return ConfigVersions.withVisibility(configVersion, hash == 0 ? 1 : hash);
	}

	public List<PlaylistItem> getItems() {
		return items;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
