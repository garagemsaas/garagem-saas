package br.com.garagem.revenda.estoque;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class EstoqueDtos {
  private EstoqueDtos() {}

  public enum Status {
    EM_AVALIACAO,
    EM_PREPARACAO,
    DISPONIVEL,
    RESERVADO,
    VENDIDO
  }

  public enum Origem {
    COMPRA,
    TROCA,
    AQUISICAO_DIRETA,
    OUTRO
  }

  public record Novo(
      @NotNull UUID veiculoId,
      @NotNull UUID responsavelId,
      @NotNull @PastOrPresent LocalDate entrada,
      @NotNull Origem origem,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valorAquisicao,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precoAnunciado,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precoMinimo,
      @Size(max = 4000) String observacoes) {}

  public record Precos(
      @NotNull @PositiveOrZero Long revisao,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precoAnunciado,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precoMinimo) {}

  public record Transicao(@NotNull @PositiveOrZero Long revisao, @NotNull Status status) {}

  public record Preparar(
      @NotNull @PositiveOrZero Long revisao,
      UUID mecanicoId,
      @PositiveOrZero long km,
      @NotBlank @Size(max = 4000) String relato) {}

  public record Revisao(@NotNull @PositiveOrZero Long revisao) {}

  public record NovoCusto(
      @NotNull @PositiveOrZero Long revisao,
      @NotBlank @Size(max = 500) String descricao,
      @NotBlank @Size(max = 60) String categoria,
      @Size(max = 160) String fornecedor,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal valor,
      @NotNull @PastOrPresent LocalDate data,
      @Size(max = 2000) String observacoes) {}

  public record Custo(
      UUID id,
      String descricao,
      String categoria,
      String fornecedor,
      BigDecimal valor,
      LocalDate data,
      String observacoes,
      UUID autorId,
      UUID ordemServicoId,
      UUID orcamentoVersaoId,
      Instant criadoEm) {}

  public record Item(
      UUID id,
      UUID veiculoId,
      UUID avaliacaoId,
      UUID responsavelId,
      LocalDate entrada,
      String origem,
      BigDecimal valorAquisicao,
      BigDecimal precoAnunciado,
      BigDecimal precoMinimo,
      String status,
      UUID ordemServicoId,
      String observacoes,
      long revisao,
      Instant criadoEm,
      String placa,
      String marca,
      String modelo,
      BigDecimal custosPreparacao,
      BigDecimal custoTotal,
      BigDecimal margemPrevista) {}

  public record Filtro(
      Status status,
      String marca,
      String modelo,
      BigDecimal precoDe,
      BigDecimal precoAte,
      LocalDate entradaDe,
      LocalDate entradaAte,
      String busca) {}
}
