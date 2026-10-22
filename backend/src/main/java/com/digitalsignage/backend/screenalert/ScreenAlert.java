package com.digitalsignage.backend.screenalert;

import java.time.Instant;

import org.hibernate.annotations.TenantId;

import com.digitalsignage.backend.device.Device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um aviso em tela cheia ("Alertar agora"): vale do momento em que foi criado até o prazo ou até alguém encerrar. */
@Entity
@Table(name = "screen_alerts")
public class ScreenAlert {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** O cliente dono da linha: o Hibernate preenche ao gravar e filtra em toda consulta (ver TenantContext). */
	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private Long tenantId;

	private String title;

	@Column(nullable = false)
	private String message;

	@Column(nullable = false)
	private String tone;

	@Column(name = "target_type", nullable = false)
	private String targetType;

	@Column(name = "target_id")
	private Long targetId;

	@Column(name = "created_by")
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "ended_by")
	private String endedBy;

	protected ScreenAlert() {
	}

	public ScreenAlert(String title, String message, Tone tone, Target target, Long targetId, String createdBy, Instant createdAt, Instant expiresAt) {
		this.title = title;
		this.message = message;
		this.tone = tone.name();
		this.targetType = target.name();
		this.targetId = targetId;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** A cor e a urgência do aviso. A TV mostra mais de um? O mais urgente vence. */
	public enum Tone {
		URGENT, WARNING, INFO
	}

	/** Para quem vai. */
	public enum Target {
		ALL, GROUP, DEVICE
	}

	/** No ar neste instante: não foi encerrado e o prazo não passou. */
	public boolean isActiveAt(Instant now) {
		return endedAt == null && now.isBefore(expiresAt);
	}

	/** Esta TV deve mostrar o aviso? (Se o aviso está no ar é outra pergunta: {@link #isActiveAt}.) */
	public boolean appliesTo(Device device) {
		return switch (Target.valueOf(targetType)) {
			case ALL -> true;
			case GROUP -> targetId != null && device.getGroupIds().contains(targetId);
			case DEVICE -> targetId != null && targetId.equals(device.getId());
		};
	}

	public void end(Instant now, String by) {
		if (endedAt == null) {
			endedAt = now;
			endedBy = by;
		}
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public Tone getTone() {
		return Tone.valueOf(tone);
	}

	public Target getTargetType() {
		return Target.valueOf(targetType);
	}

	public Long getTargetId() {
		return targetId;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public String getEndedBy() {
		return endedBy;
	}
}
