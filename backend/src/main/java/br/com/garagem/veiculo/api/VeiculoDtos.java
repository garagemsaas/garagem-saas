package br.com.garagem.veiculo.api;

import br.com.garagem.veiculo.domain.Veiculo;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class VeiculoDtos {
  private VeiculoDtos() {}

  public record Entrada(
      @NotNull UUID clienteId,
      @NotBlank @Pattern(regexp = "[A-Za-z]{3}[- ]?[0-9][A-Za-z0-9][0-9]{2}") String placa,
      @NotBlank @Size(max = 80) String marca,
      @NotBlank @Size(max = 100) String modelo,
      @Min(1886) @Max(2200) int ano,
      @PositiveOrZero long km,
      @NotBlank @Size(max = 60) String cor,
      @PositiveOrZero long revisao) {}

  public record Saida(
      UUID id,
      UUID clienteId,
      String placa,
      String marca,
      String modelo,
      int ano,
      long km,
      String cor,
      long revisao) {
    public static Saida de(Veiculo v) {
      return new Saida(
          v.id, v.clienteId, v.placa, v.marca, v.modelo, v.ano, v.km, v.cor, v.revisao);
    }
  }
}
