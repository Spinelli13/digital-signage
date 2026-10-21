package com.digitalsignage.backend.monitoring;

import java.time.Instant;

import jakarta.persistence.Column;
import org.hibernate.annotations.TenantId;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Configurações de monitoramento e alertas (uma linha por cliente). */
@Entity
@Table(name = "monitoring_settings")
public class MonitoringSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** O cliente dono da linha: o Hibernate preenche ao gravar e filtra em toda consulta (ver TenantContext). */
	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

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

	/** Quanto tempo o detalhe de cada exibição (comprovação) fica guardado; o resumo diário fica um ano. */
	@Column(name = "play_log_retention_days", nullable = false)
	private int playLogRetentionDays = 14;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Os padrões (um cliente novo ainda não tem linha gravada: ela nasce no primeiro "salvar"). */
	public MonitoringSettings() {
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

	public int getPlayLogRetentionDays() {
		return playLogRetentionDays;
	}

	public void setPlayLogRetentionDays(int value) {
		this.playLogRetentionDays = value;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
