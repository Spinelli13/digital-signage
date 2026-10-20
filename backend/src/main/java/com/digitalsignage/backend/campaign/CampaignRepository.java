package com.digitalsignage.backend.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

	/** Só as campanhas que não estão pausadas, já com os alvos (evita uma consulta por campanha). */
	@Query("select distinct c from Campaign c left join fetch c.targets where c.enabled = true")
	List<Campaign> findEnabledWithTargets();

	@Query("select distinct c from Campaign c left join fetch c.targets")
	List<Campaign> findAllWithTargets();

	Optional<Campaign> findFirstByPlaylistId(Long playlistId);

	long countByEnabledTrue();

}
