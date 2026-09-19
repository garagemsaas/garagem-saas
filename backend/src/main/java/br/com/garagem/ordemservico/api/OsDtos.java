package br.com.garagem.ordemservico.api;

import br.com.garagem.ordemservico.diagnostico.domain.Classificacao;
import br.com.garagem.ordemservico.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class OsDtos {
  private OsDtos() {}

  public record NovaOs(
      @NotNull UUID veiculoId,
      UUID mecanicoId,
      @PositiveOrZero long kmEntrada,
      @NotBlank @Size(max = 4000) String relato,
      Instant previsaoEntrega) {}

  /**
   * Filtros da listagem de OS. Todos são opcionais e combinam com E lógico; qualquer id informado
   * ainda precisa pertencer à oficina do token para casar.
   */
  public record OsFiltro(
      String busca,
      Long numero,
      StatusOs status,
      UUID clienteId,
      UUID veiculoId,
      UUID mecanicoId,
      String placa,
      Instant de,
      Instant ate) {}

  public record StatusEntrada(@NotNull StatusOs status, @PositiveOrZero long revisao) {}

  public record ResponsavelEntrada(@NotNull UUID mecanicoId, @PositiveOrZero long revisao) {}

  public record OsSaida(
      UUID id,
      long numero,
      UUID veiculoId,
      UUID clienteId,
      UUID mecanicoId,
      StatusOs status,
      long kmEntrada,
      String relato,
      Instant criadoEm,
      Instant previsaoEntrega,
      Instant concluidaEm,
      long revisao,
      String tipo) {
    public static OsSaida de(OrdemServico o) {
      return new OsSaida(
          o.id,
          o.numero,
          o.veiculoId,
          o.clienteId,
          o.mecanicoId,
          o.status,
          o.kmEntrada,
          o.relato,
          o.criadoEm,
          o.previsaoEntrega,
          o.concluidaEm,
          o.revisao,
          o.tipo);
    }
  }

  public record ChecklistEntradaDto(
      @Size(max = 4000) String observacoes,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid ChecklistItemEntrada> itens) {}

  public record ChecklistItemEntrada(
      @NotBlank @Size(max = 200) String descricao,
      @NotBlank @Size(max = 100) String condicao,
      @Size(max = 1000) String observacao) {}

  public record ChecklistItemSaida(UUID id, String descricao, String condicao, String observacao) {}

  public record ChecklistSaida(UUID id, String observacoes, List<ChecklistItemSaida> itens) {}

  public record DiagnosticoEntrada(
      @NotBlank @Size(max = 4000) String descricao, @NotNull Classificacao classificacao) {}

  public record DiagnosticoSaida(
      UUID id, String descricao, Classificacao classificacao, Instant criadoEm) {}

  public record VersaoEntrada(
      @Size(max = 4000) String observacoes,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid ItemEntrada> itens) {}

  public record ItemEntrada(
      @NotBlank @Pattern(regexp = "PECA|SERVICO") String tipo,
      @NotBlank @Size(max = 500) String descricao,
      @NotNull @DecimalMin("0.001") @Digits(integer = 6, fraction = 3) BigDecimal quantidade,
      @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal valorUnitario) {}

  public record ItemSaida(
      UUID id,
      String tipo,
      String descricao,
      BigDecimal quantidade,
      BigDecimal valorUnitario,
      BigDecimal subtotal) {}

  public record DecisaoSaida(boolean aprovado, Instant criadoEm, String canal) {}

  public record VersaoSaida(
      UUID id,
      int numero,
      String observacoes,
      BigDecimal total,
      Instant criadoEm,
      List<ItemSaida> itens,
      DecisaoSaida decisao) {}

  public record EventoSaida(
      UUID id, String tipo, String descricao, String origem, UUID autorId, Instant criadoEm) {}

  public record LinkSaida(UUID id, String url, String token, Instant expiraEm) {}

  public record DecisaoEntrada(@NotNull UUID versaoId, @NotNull Boolean aprovado) {}

  public record PublicoSaida(
      long numero,
      StatusOs status,
      String veiculo,
      Instant previsaoEntrega,
      VersaoSaida orcamento) {}
}
