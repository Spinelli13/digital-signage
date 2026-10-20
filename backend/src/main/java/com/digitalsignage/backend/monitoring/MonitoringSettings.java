package com.digitalsignage.backend.monitoring;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Configurações de monitoramento e alertas (uma única linha, id = 1). */
@Entity
@Table(name = "monitoring_settings")
public class MonitoringSettings {

	public static final short ID = 1;

	@Id
	private Short id = ID;

	@Column(name = "offline_after_minutes", nullable = false)
	private int offlineAfterMinutes = 10;

	@Column(name = "never_connected_after_hours", nullable = false)
	private int neverConnectedAfterHours = 24;

	@Column(name = "error_burst_count", nullable = false)
	private int errorBurstCount = 5;

	@Column(name = "webhook_url")
	private String webhookUrl;

	@Column(name = "notify_on_resolve", nullable = false)
	private boolean notifyOnResolve = true;

	@Column(name = "events_retention_days", nullable = false)
	private int eventsRetentionDays = 90;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MonitoringSettings() {
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	public int getOfflineAfterMinutes() {
		return offlineAfterMinutes;
	}

	public void setOfflineAfterMinutes(int value) {
		this.offlineAfterMinutes = value;
	}

	public int getNeverConnectedAfterHours() {
		return neverConnectedAfterHours;
	}

	public void setNeverConnectedAfterHours(int value) {
		this.neverConnectedAfterHours = value;
	}

	public int getErrorBurstCount() {
		return errorBurstCount;
	}

	public void setErrorBurstCount(int value) {
		this.errorBurstCount = value;
	}

	public String getWebhookUrl() {
		return webhookUrl;
	}

	public void setWebhookUrl(String webhookUrl) {
		this.webhookUrl = webhookUrl;
	}

	public boolean isNotifyOnResolve() {
		return notifyOnResolve;
	}

	public void setNotifyOnResolve(boolean notifyOnResolve) {
		this.notifyOnResolve = notifyOnResolve;
	}

	public int getEventsRetentionDays() {
		return eventsRetentionDays;
	}

	public void setEventsRetentionDays(int value) {
		this.eventsRetentionDays = value;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
