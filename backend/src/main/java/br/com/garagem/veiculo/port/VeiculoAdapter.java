package br.com.garagem.veiculo.port;

import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.veiculo.repository.VeiculoRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VeiculoAdapter implements VeiculoPort {
  private final VeiculoRepository veiculos;

  public VeiculoAdapter(VeiculoRepository veiculos) {
    this.veiculos = veiculos;
  }

  @Override
  @Transactional
  public void atualizarQuilometragem(UUID veiculoId, long km) {
    var v =
        veiculos
            .findByIdAndOficinaId(veiculoId, TenantContext.current())
            .orElseThrow(br.com.garagem.shared.error.ApiException::missing);
    if (km > v.km) v.km = km;
  }

  @Override
  public Optional<DadosVeiculo> buscar(UUID veiculoId) {
    return veiculos
        .findByIdAndOficinaId(veiculoId, TenantContext.current())
        .map(v -> new DadosVeiculo(v.id, v.clienteId, v.placa, v.marca, v.modelo, v.km));
  }
}
