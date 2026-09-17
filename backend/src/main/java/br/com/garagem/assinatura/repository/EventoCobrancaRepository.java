package br.com.garagem.assinatura.repository;

import br.com.garagem.assinatura.domain.EventoCobranca;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.UUID;
import org.springframework.data.domain.*;

public interface EventoCobrancaRepository extends TenantRepository<EventoCobranca> {
  Page<EventoCobranca> findByOficinaIdOrderByCriadoEmDescIdDesc(UUID oficinaId, Pageable pageable);
}
