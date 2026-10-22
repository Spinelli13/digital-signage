package com.digitalsignage.backend.playlist;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import com.digitalsignage.backend.media.Media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "playlist_items")
public class PlaylistItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "playlist_id", nullable = false)
	private Playlist playlist;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "media_id", nullable = false)
	private Media media;

	@Column(nullable = false)
	private int position;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	/** Primeiro e último dia em que o item vale (inclusive); nulo = sem limite daquele lado. */
	@Column(name = "valid_from")
	private LocalDate validFrom;

	@Column(name = "valid_until")
	private LocalDate validUntil;

	/** Dias da semana em que vale, "1,3,5" (1 = segunda); nulo = todos. */
	@Column(name = "days_of_week")
	private String daysOfWeek;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PlaylistItem() {
	}

	public PlaylistItem(Media media, int position, int durationSeconds) {
		this.media = media;
		this.position = position;
		this.durationSeconds = durationSeconds;
	}

	public PlaylistItem(Media media, int position, int durationSeconds, LocalDate validFrom, LocalDate validUntil, String daysOfWeek) {
		this(media, position, durationSeconds);
		this.validFrom = validFrom;
		this.validUntil = validUntil;
		this.daysOfWeek = daysOfWeek;
	}

	/** O item tem alguma regra de validade (datas ou dias)? Sem regra vale sempre, como todo item sempre valeu. */
	public boolean hasRules() {
		return validFrom != null || validUntil != null || daysOfWeek != null;
	}

	/** Vale neste dia? As datas são inclusivas e os dias da semana são os de {@code day}. */
	public boolean isValidOn(LocalDate day) {
		if (validFrom != null && day.isBefore(validFrom)) {
			return false;
		}
		if (validUntil != null && day.isAfter(validUntil)) {
			return false;
		}
		return daysOfWeek == null || ItemValidity.parseDays(daysOfWeek).contains(DayOfWeek.from(day));
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	void assignTo(Playlist playlist) {
		this.playlist = playlist;
	}

	public Long getId() {
		return id;
	}

	public Playlist getPlaylist() {
		return playlist;
	}

	public Media getMedia() {
		return media;
	}

	public int getPosition() {
		return position;
	}

	public int getDurationSeconds() {
		return durationSeconds;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}


	public LocalDate getValidFrom() {
		return validFrom;
	}

	public LocalDate getValidUntil() {
		return validUntil;
	}

	public String getDaysOfWeek() {
		return daysOfWeek;
	}
}
