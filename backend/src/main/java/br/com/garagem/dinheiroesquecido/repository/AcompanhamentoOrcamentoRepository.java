package br.com.garagem.dinheiroesquecido.repository;

import br.com.garagem.dinheiroesquecido.domain.AcompanhamentoOrcamento;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AcompanhamentoOrcamentoRepository
    extends TenantRepository<AcompanhamentoOrcamento> {
  Optional<AcompanhamentoOrcamento> findByOrcamentoVersaoIdAndOficinaId(
      UUID orcamentoVersaoId, UUID oficinaId);
}
