package br.com.garagem.revenda.proposta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PropostaDtos {
  private PropostaDtos() {}

  public enum Status {
    RASCUNHO,
    ENVIADA,
    ACEITA,
    RECUSADA,
    EXPIRADA,
    CANCELADA
  }

  public record Termos(
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valorNegociado,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal entrada,
      UUID avaliacaoTrocaId,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal valorTroca,
      @NotNull @Future Instant validade,
      @Size(max = 4000) String observacoes) {}

  public record Nova(
      @NotNull UUID estoqueId,
      @NotNull UUID clienteId,
      @NotNull UUID vendedorId,
      UUID leadId,
      @NotNull @Valid Termos termos) {}

  public record Revisar(@NotNull @PositiveOrZero Long revisao, @NotNull @Valid Termos termos) {}

  public record Transicao(@NotNull @PositiveOrZero Long revisao, @NotNull Status status) {}

  public record Item(
      UUID id,
      UUID estoqueId,
      UUID clienteId,
      UUID vendedorId,
      UUID leadId,
      String status,
      int numeroVersao,
      long revisao,
      Instant criadoEm,
      UUID versaoId,
      BigDecimal precoAnunciado,
      BigDecimal valorNegociado,
      BigDecimal desconto,
      BigDecimal entrada,
      UUID avaliacaoTrocaId,
      BigDecimal valorTroca,
      Instant validade,
      String observacoes,
      String clienteNome,
      // Para chamar no WhatsApp sem precisar abrir o cadastro do cliente.
      String telefone,
      String veiculoDescricao) {}

  public record Versao(
      UUID id,
      int numero,
      BigDecimal precoAnunciado,
      BigDecimal valorNegociado,
      BigDecimal desconto,
      BigDecimal entrada,
      UUID avaliacaoTrocaId,
      BigDecimal valorTroca,
      Instant validade,
      String observacoes,
      UUID autorId,
      Instant criadoEm) {}

  public record Filtro(Status status, UUID clienteId, UUID veiculoId, UUID vendedorId) {}
}
