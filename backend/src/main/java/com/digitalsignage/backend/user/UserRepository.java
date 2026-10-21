package com.digitalsignage.backend.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmailIgnoreCase(String email);

	List<User> findByTenantIdOrderByName(Long tenantId);

	long countByTenantIdAndActiveTrue(Long tenantId);

	long countByTenantIdAndRoleAndActiveTrue(Long tenantId, String role);

	Optional<User> findByIdAndTenantId(Long id, Long tenantId);

	List<User> findByRoleOrderByName(String role);

}
