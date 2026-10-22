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

	/** Pedido de titular: a equipe do cliente com o nome ou o e-mail buscado (o termo já vem com os curingas do LIKE escapados). */
	@org.springframework.data.jpa.repository.Query("select u from User u where u.tenantId = :tenantId and (lower(u.name) like lower(concat('%', :term, '%')) escape '\\' or lower(u.email) like lower(concat('%', :term, '%')) escape '\\') order by u.name")
	List<User> forPrivacy(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, @org.springframework.data.repository.query.Param("term") String term);

}
