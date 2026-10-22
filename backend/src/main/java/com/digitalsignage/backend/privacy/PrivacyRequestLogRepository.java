package com.digitalsignage.backend.privacy;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PrivacyRequestLogRepository extends JpaRepository<PrivacyRequestLog, Long> {

	@Query("select r from PrivacyRequestLog r order by r.performedAt desc, r.id desc")
	List<PrivacyRequestLog> latest(Pageable page);

}
