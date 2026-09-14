package br.com.garagem.ordemservico.foto.repository;

import br.com.garagem.ordemservico.foto.domain.FotoVeiculo;
import br.com.garagem.shared.persistence.TenantRepository;

public interface FotoVeiculoRepository extends TenantRepository<FotoVeiculo> {
  java.util.List<FotoVeiculo> findByOrdemServicoIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      java.util.UUID parentId, java.util.UUID oficinaId);
}
