package com.digitalsignage.backend.playlist;

import java.time.Instant;

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

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PlaylistItem() {
	}

	public PlaylistItem(Media media, int position, int durationSeconds) {
		this.media = media;
		this.position = position;
		this.durationSeconds = durationSeconds;
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

}
