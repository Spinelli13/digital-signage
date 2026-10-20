package com.digitalsignage.backend.tenant;

import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Liga o isolamento por cliente do Hibernate: as entidades marcadas com {@code @TenantId} só enxergam as linhas do
 * cliente atual ({@link TenantContext}) e gravam o cliente atual nas linhas novas. Vale para consultas e para
 * leituras por id: o id de um registro de outro cliente simplesmente "não existe".
 */
@Component
public class TenantIdResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

	@Override
	public Long resolveCurrentTenantIdentifier() {
		return TenantContext.current();
	}

	/** Uma sessão aberta não pode "mudar de cliente" no meio do caminho: seria um vazamento. */
	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

	@Override
	public void customize(Map<String, Object> hibernateProperties) {
		hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
	}

}
