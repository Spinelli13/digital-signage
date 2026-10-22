package com.digitalsignage.backend.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacySettingsRepository extends JpaRepository<PrivacySettings, Long> {
}
