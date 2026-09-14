package br.com.garagem.ordemservico.orcamento.repository;

import br.com.garagem.ordemservico.orcamento.domain.Orcamento;
import br.com.garagem.shared.persistence.TenantRepository;

public interface OrcamentoRepository extends TenantRepository<Orcamento> {
  java.util.Optional<Orcamento> findByOrdemServicoIdAndOficinaId(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
