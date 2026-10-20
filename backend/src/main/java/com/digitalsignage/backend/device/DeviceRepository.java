package com.digitalsignage.backend.device;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

	Optional<Device> findByDeviceTokenHash(String deviceTokenHash);

	Optional<Device> findByActivationCodeAndStatus(String activationCode, String status);

	boolean existsByActivationCode(String activationCode);

	long countByStatusAndDeviceTokenHashIsNotNull(String status);

	@Transactional
	long deleteByStatusAndDeviceTokenHashIsNotNullAndCreatedAtBefore(String status, Instant before);

}
