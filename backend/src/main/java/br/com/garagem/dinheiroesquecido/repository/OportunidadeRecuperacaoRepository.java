package br.com.garagem.dinheiroesquecido.repository;

import br.com.garagem.dinheiroesquecido.domain.OportunidadeRecuperacao;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OportunidadeRecuperacaoRepository
    extends TenantRepository<OportunidadeRecuperacao> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OportunidadeRecuperacao o where o.id=:id and o.oficinaId=:oficina")
  Optional<OportunidadeRecuperacao> lock(UUID id, UUID oficina);

  List<OportunidadeRecuperacao> findByOrdemServicoIdAndOficinaId(
      UUID ordemServicoId, UUID oficinaId);
}
