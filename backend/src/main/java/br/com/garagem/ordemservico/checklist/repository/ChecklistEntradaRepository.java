package br.com.garagem.ordemservico.checklist.repository;

import br.com.garagem.ordemservico.checklist.domain.ChecklistEntrada;
import br.com.garagem.shared.persistence.TenantRepository;

public interface ChecklistEntradaRepository extends TenantRepository<ChecklistEntrada> {
  java.util.Optional<ChecklistEntrada> findByOrdemServicoIdAndOficinaId(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
