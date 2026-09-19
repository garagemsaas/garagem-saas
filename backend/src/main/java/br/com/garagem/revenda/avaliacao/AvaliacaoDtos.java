package br.com.garagem.revenda.avaliacao;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class AvaliacaoDtos {
  private AvaliacaoDtos() {}

  public enum Status {
    ABERTA,
    ACEITA,
    RECUSADA,
    EXPIRADA,
    CANCELADA
  }

  public record Nova(
      @NotNull UUID veiculoId,
      UUID clienteId,
      @NotNull UUID avaliadorId,
      @NotNull @PastOrPresent LocalDate data,
      @PositiveOrZero long km,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valorEstimado,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valorOferecido,
      @NotNull @Future Instant validade,
      @Size(max = 4000) String observacoes) {}

  public record Aceite(
      @NotNull @PositiveOrZero Long revisao,
      @NotNull UUID responsavelId,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precoAnunciado,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precoMinimo) {}

  public record Transicao(@NotNull @PositiveOrZero Long revisao, @NotNull Status status) {}

  public record Item(
      UUID id,
      UUID veiculoId,
      UUID clienteId,
      UUID avaliadorId,
      LocalDate data,
      long km,
      BigDecimal valorEstimado,
      BigDecimal valorOferecido,
      Instant validade,
      String observacoes,
      String status,
      long revisao,
      Instant criadoEm,
      String placa,
      String marca,
      String modelo) {}
}
