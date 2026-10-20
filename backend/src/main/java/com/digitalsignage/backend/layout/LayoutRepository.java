package com.digitalsignage.backend.layout;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LayoutRepository extends JpaRepository<Layout, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	Optional<Layout> findByNameIgnoreCase(String name);

	/** Layout que usa a playlist numa zona (para recusar apagar uma playlist em uso). */
	@Query("select l from Layout l join l.zones z where z.playlistId = :playlistId order by l.id")
	List<Layout> findUsingPlaylist(@Param("playlistId") Long playlistId);

}
