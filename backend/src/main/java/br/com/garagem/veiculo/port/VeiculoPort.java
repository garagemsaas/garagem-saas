package br.com.garagem.veiculo.port;

import java.util.Optional;
import java.util.UUID;

/** Dados de veículo que outros módulos consultam, sem alcançar o repositório nem a entidade. */
public interface VeiculoPort {
  record DadosVeiculo(UUID id, UUID clienteId, String placa, String marca, String modelo, long km) {
    /** Descrição pronta para exibição, para não espalhar formatação de veículo por aí. */
    public String descricao() {
      return marca + " " + modelo + " · " + placa;
    }
  }

  Optional<DadosVeiculo> buscar(UUID veiculoId);

  /**
   * Atualiza a quilometragem a partir de uma leitura de entrada na oficina. Existe porque abrir uma
   * OS registra o odômetro do veículo — antes o módulo de OS escrevia direto na entidade alheia.
   */
  void atualizarQuilometragem(UUID veiculoId, long km);
}
