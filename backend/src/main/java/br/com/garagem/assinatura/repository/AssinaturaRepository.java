package br.com.garagem.assinatura.repository;

import br.com.garagem.assinatura.domain.Assinatura;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AssinaturaRepository extends TenantRepository<Assinatura> {
  Optional<Assinatura> findByOficinaId(UUID oficinaId);

  /**
   * Serializa as decisões de ciclo de vida da oficina: webhook, mudança de plano e cancelamento
   * concorrem pela mesma linha.
   */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Assinatura a where a.oficinaId=:oficina")
  Optional<Assinatura> lock(UUID oficina);
}
