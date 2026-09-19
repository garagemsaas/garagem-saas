package br.com.garagem.revenda.venda;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class VendaDtos {
  private VendaDtos() {}

  public record Nova(
      @NotNull UUID estoqueId,
      @NotNull UUID clienteId,
      @NotNull UUID vendedorId,
      @NotNull @PositiveOrZero Long revisaoEstoque,
      UUID propostaId,
      UUID propostaVersaoId,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valorVendido,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal entrada,
      UUID avaliacaoTrocaId,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valorTroca,
      @Size(max = 4000) String observacoes) {}

  public record Item(
      UUID id,
      UUID estoqueId,
      UUID veiculoId,
      UUID clienteId,
      UUID vendedorId,
      UUID propostaId,
      UUID propostaVersaoId,
      UUID reservaId,
      UUID estoqueTrocaId,
      BigDecimal precoAnunciado,
      BigDecimal valorVendido,
      BigDecimal desconto,
      BigDecimal entrada,
      BigDecimal valorTroca,
      BigDecimal custoAcumulado,
      BigDecimal margemBruta,
      String observacoes,
      Instant criadoEm,
      String clienteNome,
      String veiculoDescricao) {}

  public record Filtro(Instant de, Instant ate, UUID vendedorId, UUID clienteId, UUID veiculoId) {}
}
