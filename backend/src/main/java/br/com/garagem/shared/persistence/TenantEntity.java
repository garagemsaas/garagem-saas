package br.com.garagem.shared.persistence;

import br.com.garagem.tenancy.TenantContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@MappedSuperclass
public abstract class TenantEntity {
  @Id public UUID id = UUID.randomUUID();

  @TenantId
  @Column(nullable = false, updatable = false)
  public UUID oficinaId;

  @Column(nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  @PrePersist
  void requireTenant() {
    UUID current = TenantContext.current();
    if (oficinaId != null && !oficinaId.equals(current))
      throw new IllegalStateException("Oficina inválida");
    oficinaId = current;
  }
}
