package com.digitalsignage.backend.campaign;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Uma playlist que só vale em certas datas, dias da semana e horas (no fuso da campanha), para certas TVs, com
 * prioridade. A agenda em si (está ativa agora?) fica em {@link CampaignSchedule}.
 */
@Entity
@Table(name = "campaigns")
public class Campaign {

	public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";
	private static final String ALL_DAYS = "1,2,3,4,5,6,7";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(name = "playlist_id", nullable = false)
	private Long playlistId;

	@Column(nullable = false)
	private int priority = 0;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(name = "days_of_week", nullable = false)
	private String daysOfWeek = ALL_DAYS;

	@Column(nullable = false)
	private String timezone = DEFAULT_TIMEZONE;

	@OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CampaignTarget> targets = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Campaign() {
	}

	public Campaign(String name) {
		this.name = name;
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

	// ---- agenda ----

	public ZoneId zone() {
		return ZoneId.of(timezone);
	}

	/** Dias da semana em que a campanha vale (vazio nunca acontece: a API exige ao menos um). */
	public Set<DayOfWeek> days() {
		return daysOfWeek.isBlank()
				? EnumSet.noneOf(DayOfWeek.class)
				: java.util.Arrays.stream(daysOfWeek.split(","))
						.map(String::trim)
						.map(Integer::parseInt)
						.map(DayOfWeek::of)
						.collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
	}

	public void setDays(Set<DayOfWeek> days) {
		this.daysOfWeek = days.stream()
				.sorted()
				.map(day -> String.valueOf(day.getValue()))
				.collect(Collectors.joining(","));
	}

	public void replaceTargets(List<CampaignTarget> newTargets) {
		targets.clear();
		newTargets.forEach(target -> {
			target.setCampaign(this);
			targets.add(target);
		});
	}

	// ---- acesso ----

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

	public Long getPlaylistId() {
		return playlistId;
	}

	public void setPlaylistId(Long playlistId) {
		this.playlistId = playlistId;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public List<CampaignTarget> getTargets() {
		return targets;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
