package com.digitalsignage.backend.playlist;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

	/** As playlists que exibem esta mídia (para avisar as TVs quando o conteúdo de um widget muda). */
	@Query("select distinct p from Playlist p join p.items i where i.media.id = :mediaId")
	List<Playlist> findUsing(@Param("mediaId") Long mediaId);
}
