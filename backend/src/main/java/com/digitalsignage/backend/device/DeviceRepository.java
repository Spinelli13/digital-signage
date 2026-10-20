package com.digitalsignage.backend.device;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

	Optional<Device> findByDeviceTokenHash(String deviceTokenHash);

	Optional<Device> findByActivationCodeAndStatus(String activationCode, String status);

	boolean existsByActivationCode(String activationCode);

	long countByStatusAndDeviceTokenHashIsNotNull(String status);

	@Transactional
	long deleteByStatusAndDeviceTokenHashIsNotNullAndCreatedAtBefore(String status, Instant before);

	@Query("select d from Device d join d.groupIds g where g = :groupId order by d.id")
	List<Device> findByGroupId(@Param("groupId") Long groupId);

	List<Device> findByLocationIdOrderById(Long locationId);

	long countByLayoutId(Long layoutId);

	/** Linhas [layoutId, quantidade de TVs] dos layouts em uso. */
	@Query("select d.layoutId, count(d) from Device d where d.layoutId is not null group by d.layoutId")
	List<Object[]> countByLayout();

}
