package br.com.garagem.revenda.reserva;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ReservaDtos {
  private ReservaDtos() {}

  public record Nova(
      @NotNull UUID estoqueId,
      @NotNull UUID clienteId,
      @NotNull UUID vendedorId,
      @NotNull @PositiveOrZero Long revisaoEstoque,
      @NotNull @Future Instant validade,
      @Size(max = 2000) String observacoes) {}

  public record Cancelamento(@NotNull @PositiveOrZero Long revisao) {}

  public record Item(
      UUID id,
      UUID estoqueId,
      UUID clienteId,
      UUID vendedorId,
      Instant validade,
      String observacoes,
      String status,
      long revisao,
      Instant criadoEm) {}
}
