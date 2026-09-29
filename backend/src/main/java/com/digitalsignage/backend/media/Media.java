package com.digitalsignage.backend.media;

import java.time.Instant;

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

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String type;

	@Column(name = "mime_type", nullable = false)
	private String mimeType;

	@Column(name = "storage_key", nullable = false)
	private String storageKey;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	private String checksum;

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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
