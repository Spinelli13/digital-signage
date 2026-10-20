package com.digitalsignage.backend.device;

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
@Table(name = "devices")
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "activation_code")
	private String activationCode;

	@Column(name = "device_token_hash")
	private String deviceTokenHash;

	private String location;

	@Column(nullable = false)
	private String status = DeviceStatus.PENDING.name();

	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

	@Column(name = "player_version")
	private String playerVersion;

	@Column(name = "current_playlist_id")
	private Long currentPlaylistId;

	@Column(name = "config_version", nullable = false)
	private long configVersion = 0;

	@Column(name = "update_requested_at")
	private Instant updateRequestedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Device() {
	}

	public Device(String name, String location) {
		this.name = name;
		this.location = location;
	}

	/** Dispositivo que se auto-registrou e aguarda o administrador digitar o código exibido na TV. */
	public static Device selfRegistered(String activationCode, String deviceTokenHash, String playerVersion) {
		Device device = new Device("Novo dispositivo", null);
		device.activationCode = activationCode;
		device.deviceTokenHash = deviceTokenHash;
		device.playerVersion = playerVersion;
		return device;
	}

	public void activate(String name, String location) {
		this.name = name;
		this.location = location;
		this.status = DeviceStatus.ACTIVE.name();
		this.activationCode = null;
	}

	public void recordHeartbeat(String playerVersion, long syncedConfigVersion) {
		this.lastSeenAt = Instant.now();
		this.playerVersion = playerVersion;
		this.configVersion = syncedConfigVersion;
	}

	/** Pede que a TV consulte agora o site de downloads por uma versão nova do Player. */
	public void requestUpdateCheck() {
		this.updateRequestedAt = Instant.now();
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

	public String getActivationCode() {
		return activationCode;
	}

	public String getDeviceTokenHash() {
		return deviceTokenHash;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public String getPlayerVersion() {
		return playerVersion;
	}

	public Long getCurrentPlaylistId() {
		return currentPlaylistId;
	}

	public void setCurrentPlaylistId(Long currentPlaylistId) {
		this.currentPlaylistId = currentPlaylistId;
	}

	public long getConfigVersion() {
		return configVersion;
	}

	public Instant getUpdateRequestedAt() {
		return updateRequestedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
