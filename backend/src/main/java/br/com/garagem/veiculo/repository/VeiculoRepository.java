package br.com.garagem.veiculo.repository;

import br.com.garagem.shared.persistence.TenantRepository;
import br.com.garagem.veiculo.domain.Veiculo;

public interface VeiculoRepository extends TenantRepository<Veiculo> {
  org.springframework.data.domain.Page<Veiculo> findByOficinaIdAndPlacaContainingIgnoreCase(
      java.util.UUID oficinaId, String placa, org.springframework.data.domain.Pageable pageable);
}
