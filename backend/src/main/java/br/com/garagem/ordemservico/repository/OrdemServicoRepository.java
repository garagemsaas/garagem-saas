package br.com.garagem.ordemservico.repository;

import br.com.garagem.ordemservico.domain.OrdemServico;
import br.com.garagem.shared.persistence.TenantRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrdemServicoRepository
    extends TenantRepository<OrdemServico>, OrdemServicoRepositoryCustom {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OrdemServico o where o.id = :id and o.oficinaId = :oficinaId")
  Optional<OrdemServico> lock(UUID id, UUID oficinaId);
}
