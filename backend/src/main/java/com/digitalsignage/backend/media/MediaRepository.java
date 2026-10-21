package com.digitalsignage.backend.media;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {

	/** Os widgets de um tipo (ex.: "PRICES"), do cliente atual. */
	java.util.List<Media> findByWidgetKind(String widgetKind);
}
