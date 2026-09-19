package br.com.garagem.veiculo.port;

import java.util.Optional;
import java.util.UUID;

/** Dados de veículo que outros módulos consultam, sem alcançar o repositório nem a entidade. */
public interface VeiculoPort {
  record DadosVeiculo(
      UUID id,
      UUID clienteId,
      String placa,
      String marca,
      String modelo,
      long km,
      String propriedade) {
    /** Descrição pronta para exibição, para não espalhar formatação de veículo por aí. */
    public String descricao() {
      return marca + " " + modelo + (placa == null ? " · Sem placa" : " · " + placa);
    }
  }

  Optional<DadosVeiculo> buscar(UUID veiculoId);

  /** Trava física compartilhada por aquisição, troca e venda; chamar em ordem de UUID. */
  DadosVeiculo bloquear(UUID veiculoId);

  void adquirir(UUID veiculoId, UUID clienteEsperado, String motivo);

  void transferirAoCliente(UUID veiculoId, UUID clienteId, String motivo);

  /**
   * Atualiza a quilometragem a partir de uma leitura de entrada na oficina. Existe porque abrir uma
   * OS registra o odômetro do veículo — antes o módulo de OS escrevia direto na entidade alheia.
   */
  void atualizarQuilometragem(UUID veiculoId, long km);
}
