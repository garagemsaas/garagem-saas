package br.com.garagem.ordemservico.checklist.item.repository;

import br.com.garagem.ordemservico.checklist.item.domain.ChecklistItem;
import br.com.garagem.shared.persistence.TenantRepository;

public interface ChecklistItemRepository extends TenantRepository<ChecklistItem> {
  java.util.List<ChecklistItem> findByChecklistEntradaIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
