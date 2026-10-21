package com.digitalsignage.backend.layout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Um retângulo da tela, em % (0-100) do canto superior esquerdo. */
@Entity
@Table(name = "layout_zones")
public class LayoutZone {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "layout_id", nullable = false)
	private Layout layout;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int x;

	@Column(nullable = false)
	private int y;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	/** A zona principal exibe a playlist da TV (e a das campanhas). As demais usam {@link #playlistId}. */
	@Column(name = "is_main", nullable = false)
	private boolean main;

	/** Playlist fixa de uma zona que não é a principal (vazio = a zona fica sem conteúdo). */
	@Column(name = "playlist_id")
	private Long playlistId;

	/** Zona da versão do layout para TV em pé (falso = versão horizontal). */
	@Column(nullable = false)
	private boolean portrait;

	/** CONTAIN (a mídia cabe inteira, com faixas pretas) ou COVER (preenche a zona, cortando as bordas). */
	@Column(nullable = false)
	private String fit = FIT_CONTAIN;

	protected LayoutZone() {
	}

	public static final String FIT_CONTAIN = "CONTAIN";
	public static final String FIT_COVER = "COVER";

	public LayoutZone(String name, int x, int y, int width, int height, boolean main, Long playlistId) {
		this(name, x, y, width, height, main, playlistId, FIT_CONTAIN, false);
	}

	public LayoutZone(String name, int x, int y, int width, int height, boolean main, Long playlistId, String fit, boolean portrait) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.main = main;
		this.playlistId = playlistId;
		this.fit = fit == null || fit.isBlank() ? FIT_CONTAIN : fit;
		this.portrait = portrait;
	}

	void assignTo(Layout layout, int position) {
		this.layout = layout;
		this.position = position;
	}

	public Long getId() {
		return id;
	}

	public int getPosition() {
		return position;
	}

	public String getName() {
		return name;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean isMain() {
		return main;
	}

	public Long getPlaylistId() {
		return playlistId;
	}

	public boolean isPortrait() {
		return portrait;
	}

	public String getFit() {
		return fit;
	}

}
