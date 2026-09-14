package br.com.garagem.ordemservico.orcamento.versao.repository;

import br.com.garagem.ordemservico.orcamento.versao.domain.OrcamentoVersao;
import br.com.garagem.shared.persistence.TenantRepository;

public interface OrcamentoVersaoRepository extends TenantRepository<OrcamentoVersao> {
  java.util.List<OrcamentoVersao> findByOrcamentoIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
