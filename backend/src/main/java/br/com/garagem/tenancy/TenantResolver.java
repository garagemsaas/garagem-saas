package br.com.garagem.tenancy;

import java.util.Map;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver
    implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {
  @Override
  public UUID resolveCurrentTenantIdentifier() {
    return TenantContext.resolvedOrEmpty();
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }

  @Override
  public void customize(Map<String, Object> properties) {
    properties.put("hibernate.tenant_identifier_resolver", this);
  }
}
