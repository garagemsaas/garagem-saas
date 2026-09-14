package br.com.garagem.ordemservico.diagnostico.repository;

import br.com.garagem.ordemservico.diagnostico.domain.DiagnosticoItem;
import br.com.garagem.shared.persistence.TenantRepository;

public interface DiagnosticoItemRepository extends TenantRepository<DiagnosticoItem> {
  java.util.List<DiagnosticoItem> findByOrdemServicoIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
