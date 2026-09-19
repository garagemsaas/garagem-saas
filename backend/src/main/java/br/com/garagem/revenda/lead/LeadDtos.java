package br.com.garagem.revenda.lead;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class LeadDtos {
  private LeadDtos() {}

  public enum Origem {
    PRESENCIAL,
    TELEFONE,
    WHATSAPP,
    SITE,
    INSTAGRAM,
    FACEBOOK,
    INDICACAO,
    MARKETPLACE,
    OUTRO
  }

  public enum Status {
    NOVO,
    CONTATO_REALIZADO,
    INTERESSADO,
    PROPOSTA,
    NEGOCIACAO,
    VENDIDO,
    PERDIDO
  }

  public record Novo(
      @NotNull UUID clienteId,
      UUID veiculoId,
      @NotNull UUID vendedorId,
      @NotNull Origem origem,
      @Size(max = 4000) String observacoes) {}

  public record Transicao(@NotNull @PositiveOrZero Long revisao, @NotNull Status status) {}

  public record Item(
      UUID id,
      UUID clienteId,
      UUID veiculoId,
      UUID vendedorId,
      String origem,
      String status,
      String observacoes,
      long revisao,
      Instant criadoEm,
      String clienteNome,
      String telefone,
      String email,
      String veiculoDescricao) {}

  public record Filtro(Status status, UUID vendedorId, UUID veiculoId, Instant de, Instant ate) {}
}
