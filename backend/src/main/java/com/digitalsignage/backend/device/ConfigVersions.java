package com.digitalsignage.backend.device;

import java.util.List;

/**
 * A "versão do conteúdo" que o Player compara com a que já sincronizou. Sem layout é a versão da playlist (como
 * sempre foi). Com layout, o conteúdo depende da playlist principal, do layout e das playlists das outras zonas;
 * qualquer uma dessas coisas que mudar precisa fazer a TV baixar de novo, então a versão é uma combinação delas.
 *
 * <p>Só importa que seja igual para a mesma configuração e diferente quando algo muda (o Player compara por
 * igualdade). O resultado cabe em 53 bits: é lido sem perda também por JavaScript (painel).
 */
public final class ConfigVersions {

	private static final long MASK_53_BITS = (1L << 53) - 1;

	/**
	 * Faz a orientação da TV entrar na versão do conteúdo: mudar de "automática" para "vertical" precisa fazer a TV baixar
	 * a configuração de novo (é nela que vem a orientação). No automático a versão fica exatamente como sempre foi, então
	 * nenhuma TV existente é reconfigurada por causa disto. Versão 0 (TV sem conteúdo) fica 0: não há o que girar.
	 */
	public static long withOrientation(long version, Orientation orientation) {
		if (orientation == Orientation.AUTO || version == 0) {
			return version;
		}
		long hash = 31 * (version + 7) + orientation.ordinal();
		long mixed = (hash * 1_000_003L + 17) & MASK_53_BITS;
		return mixed == 0 ? 1 : mixed;
	}

	/**
	 * Faz entrar na versão da playlist a marca de quais itens valem hoje (validade por item). Sem marca (nenhum item com validade) a
	 * versão fica exatamente como sempre foi: nenhuma TV existente baixa de novo por causa disto.
	 */
	public static long withVisibility(long version, long visibilityKey) {
		if (visibilityKey == 0 || version == 0) {
			return version;
		}
		long mixed = ((31 * (version + 11) + visibilityKey) * 1_000_003L + 29) & MASK_53_BITS;
		return mixed == 0 ? 1 : mixed;
	}

	/** playlistId + versão de uma zona que não é a principal (playlistId 0 = zona sem playlist). */
	public record ZoneVersion(long playlistId, long version) {
	}

	private ConfigVersions() {
	}

	/** {@code mainPlaylistId} entra na conta: duas playlists recém-criadas têm a mesma versão, e trocar uma pela outra (campanha) precisa mudar o resultado. */
	public static long composite(long mainPlaylistId, long mainVersion, long layoutId, long layoutVersion, List<ZoneVersion> otherZones) {
		long hash = 1_125_899_906_842_597L;
		hash = 31 * hash + mainPlaylistId;
		hash = 31 * hash + mainVersion;
		hash = 31 * hash + layoutId;
		hash = 31 * hash + layoutVersion;
		for (ZoneVersion zone : otherZones) {
			hash = 31 * hash + zone.playlistId();
			hash = 31 * hash + zone.version();
		}
		return hash & MASK_53_BITS;
	}

}
