package br.com.garagem.veiculo.api;

import br.com.garagem.veiculo.domain.Veiculo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class VeiculoDtos {
  private VeiculoDtos() {}

  @Schema(name = "VeiculoEntrada")
  public record Entrada(
      UUID clienteId,
      @Pattern(regexp = "[A-Za-z]{3}[- ]?[0-9][A-Za-z0-9][0-9]{2}") String placa,
      @NotBlank @Size(max = 80) String marca,
      @NotBlank @Size(max = 100) String modelo,
      @Min(1886) @Max(2200) int ano,
      @PositiveOrZero long km,
      @NotBlank @Size(max = 60) String cor,
      @PositiveOrZero long revisao,
      @Size(max = 100) String versao,
      @Min(1886) @Max(2200) Integer anoModelo,
      @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9]+") String chassi,
      @Size(max = 20) @Pattern(regexp = "[0-9]+") String renavam,
      @Size(max = 40) String combustivel,
      @Size(max = 40) String cambio,
      @Size(max = 4000) String observacoes) {}

  @Schema(name = "VeiculoSaida")
  public record Saida(
      UUID id,
      UUID clienteId,
      String placa,
      String marca,
      String modelo,
      int ano,
      long km,
      String cor,
      long revisao,
      String propriedade,
      String versao,
      Integer anoModelo,
      String chassi,
      String renavam,
      String combustivel,
      String cambio,
      String observacoes) {
    public static Saida de(Veiculo v) {
      return new Saida(
          v.id,
          v.clienteId,
          v.placa,
          v.marca,
          v.modelo,
          v.ano,
          v.km,
          v.cor,
          v.revisao,
          v.propriedade,
          v.versao,
          v.anoModelo,
          v.chassi,
          v.renavam,
          v.combustivel,
          v.cambio,
          v.observacoes);
    }
  }
}
