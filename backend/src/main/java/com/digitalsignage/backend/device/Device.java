package com.digitalsignage.backend.device;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import com.digitalsignage.backend.location.Location;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

	/** Ponto físico onde a TV está (opcional). Apagar o local não apaga a TV: ela fica "sem local". */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "location_id")
	private Location location;

	/** Grupos de que a TV faz parte (tabela device_group_members; as chaves estrangeiras garantem a integridade). */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "device_group_members", joinColumns = @JoinColumn(name = "device_id"))
	@Column(name = "group_id")
	@BatchSize(size = 100)
	private Set<Long> groupIds = new HashSet<>();

	@Column(nullable = false)
	private String status = DeviceStatus.PENDING.name();

	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

	@Column(name = "player_version")
	private String playerVersion;

	@Column(name = "current_playlist_id")
	private Long currentPlaylistId;

	/** Layout da tela (vazio = tela cheia). Só o id: o layout é um cadastro à parte e não pode ser apagado enquanto em uso. */
	@Column(name = "layout_id")
	private Long layoutId;

	@Column(name = "config_version", nullable = false)
	private long configVersion = 0;

	@Column(name = "update_requested_at")
	private Instant updateRequestedAt;

	/** Último estado de conexão que o monitoramento já registrou como evento (para gravar só as transições). */
	@Column(name = "recorded_connection")
	private String recordedConnection;

	@Column(name = "activated_at")
	private Instant activatedAt;

	/** O que o Player diz estar exibindo neste momento (sem chave estrangeira: a mídia pode ser apagada). */
	@Column(name = "current_media_id")
	private Long currentMediaId;

	@Column(name = "last_playlist_id")
	private Long lastPlaylistId;

	@Column(name = "last_campaign_id")
	private Long lastCampaignId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Device() {
	}

	public Device(String name, Location location) {
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

	public void activate(String name, Location location) {
		this.name = name;
		this.location = location;
		this.status = DeviceStatus.ACTIVE.name();
		this.activationCode = null;
		this.activatedAt = Instant.now();
		this.recordedConnection = null;
	}

	public void recordHeartbeat(String playerVersion, long syncedConfigVersion, Instant now) {
		this.lastSeenAt = now;
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

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public Set<Long> getGroupIds() {
		return groupIds;
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

	public Long getLayoutId() {
		return layoutId;
	}

	public void setLayoutId(Long layoutId) {
		this.layoutId = layoutId;
	}

	public long getConfigVersion() {
		return configVersion;
	}

	public Instant getUpdateRequestedAt() {
		return updateRequestedAt;
	}

	public String getRecordedConnection() {
		return recordedConnection;
	}

	public void setRecordedConnection(String recordedConnection) {
		this.recordedConnection = recordedConnection;
	}

	public Instant getActivatedAt() {
		return activatedAt;
	}

	public Long getCurrentMediaId() {
		return currentMediaId;
	}

	public void setCurrentMediaId(Long currentMediaId) {
		this.currentMediaId = currentMediaId;
	}

	public Long getLastPlaylistId() {
		return lastPlaylistId;
	}

	public Long getLastCampaignId() {
		return lastCampaignId;
	}

	public void setLastContent(Long playlistId, Long campaignId) {
		this.lastPlaylistId = playlistId;
		this.lastCampaignId = campaignId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
