package com.digitalsignage.backend.screenalert;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreenAlertRepository extends JpaRepository<ScreenAlert, Long> {

	/** Os avisos no ar agora (do cliente da requisição), do mais novo para o mais antigo. */
	@Query("select a from ScreenAlert a where a.endedAt is null and a.expiresAt > :now order by a.createdAt desc, a.id desc")
	List<ScreenAlert> findActive(@Param("now") Instant now);

	/** O histórico recente (inclui os encerrados e vencidos). */
	List<ScreenAlert> findTop30ByOrderByCreatedAtDescIdDesc();
}
