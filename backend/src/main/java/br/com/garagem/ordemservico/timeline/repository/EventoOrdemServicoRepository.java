package br.com.garagem.ordemservico.timeline.repository;

import br.com.garagem.ordemservico.timeline.domain.EventoOrdemServico;
import br.com.garagem.shared.persistence.TenantRepository;

public interface EventoOrdemServicoRepository extends TenantRepository<EventoOrdemServico> {
  java.util.List<EventoOrdemServico> findByOrdemServicoIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
