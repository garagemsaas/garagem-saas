package br.com.garagem.ordemservico.orcamento.item.repository;

import br.com.garagem.ordemservico.orcamento.item.domain.ItemOrcamento;
import br.com.garagem.shared.persistence.TenantRepository;

public interface ItemOrcamentoRepository extends TenantRepository<ItemOrcamento> {
  java.util.List<ItemOrcamento> findByOrcamentoVersaoIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
