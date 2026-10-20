package com.digitalsignage.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Short> {
}
