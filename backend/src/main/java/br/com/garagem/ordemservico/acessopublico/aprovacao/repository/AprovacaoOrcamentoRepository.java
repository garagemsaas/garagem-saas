package br.com.garagem.ordemservico.acessopublico.aprovacao.repository;

import br.com.garagem.ordemservico.acessopublico.aprovacao.domain.AprovacaoOrcamento;
import br.com.garagem.shared.persistence.TenantRepository;

public interface AprovacaoOrcamentoRepository extends TenantRepository<AprovacaoOrcamento> {
  java.util.Optional<AprovacaoOrcamento> findByOrcamentoVersaoIdAndOficinaId(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
