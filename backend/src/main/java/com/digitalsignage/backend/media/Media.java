package com.digitalsignage.backend.media;

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

@Entity
@Table(name = "media")
public class Media {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** O cliente dono da linha: o Hibernate preenche ao gravar e filtra em toda consulta (ver TenantContext). */
	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String type;

	@Column(name = "mime_type", nullable = false)
	private String mimeType;

	/** Vazio no conteúdo web (um endereço, não um arquivo). */
	@Column(name = "storage_key")
	private String storageKey;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	private String checksum;

	/** Endereço (https) do conteúdo web; vazio nos arquivos. */
	@Column(name = "web_url")
	private String webUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Media() {
	}

	public Media(String name, String type, String mimeType, String storageKey, long fileSize,
			Integer durationSeconds, String checksum) {
		this.name = name;
		this.type = type;
		this.mimeType = mimeType;
		this.storageKey = storageKey;
		this.fileSize = fileSize;
		this.durationSeconds = durationSeconds;
		this.checksum = checksum;
	}

	/** Uma página da web exibida pelo Player pelo tempo escolhido (sem arquivo para guardar nem baixar). */
	public static Media web(String name, String url, int durationSeconds) {
		Media media = new Media(name, MediaKind.WEB.name(), "text/html", null, 0, durationSeconds, null);
		media.webUrl = url;
		return media;
	}

	public boolean isWeb() {
		return MediaKind.WEB.name().equals(type);
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

	public Long getTenantId() {
		return tenantId;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getMimeType() {
		return mimeType;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public long getFileSize() {
		return fileSize;
	}

	public Integer getDurationSeconds() {
		return durationSeconds;
	}

	public String getChecksum() {
		return checksum;
	}

	public String getWebUrl() {
		return webUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
