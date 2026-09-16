package br.com.garagem.dinheiroesquecido.api;

import br.com.garagem.dinheiroesquecido.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

public final class RecuperacaoDtos {
  private RecuperacaoDtos() {}

  @Schema(name = "RecuperacaoFiltro")
  public record Filtro(
      TipoOportunidade tipo,
      StatusOportunidade status,
      UUID responsavelId,
      UUID clienteId,
      UUID veiculoId,
      @Schema(
              description = "Criação da oportunidade: limite inclusivo",
              example = "2026-09-01T00:00:00Z")
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant de,
      @Schema(description = "Criação da oportunidade: limite inclusivo")
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant ate,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant proximoContatoDe,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant proximoContatoAte,
      FaixaIdade faixaIdade) {}

  public enum FaixaIdade {
    DIAS_0_7,
    DIAS_8_15,
    DIAS_16_30,
    DIAS_31_60,
    MAIS_60
  }

  @Schema(name = "RecuperacaoClienteResumo")
  public record ClienteResumo(UUID id, String nome, String telefone, String email) {}

  @Schema(name = "RecuperacaoVeiculoResumo")
  public record VeiculoResumo(UUID id, String placa, String marca, String modelo) {}

  @Schema(name = "RecuperacaoResponsavelResumo")
  public record ResponsavelResumo(UUID id, String nome) {}

  @Schema(name = "RecuperacaoOrigemResumo")
  public record OrigemResumo(UUID ordemServicoId, long numeroOs, UUID orcamentoVersaoId) {}

  @Schema(name = "RecuperacaoOportunidadeSaida")
  public record OportunidadeSaida(
      UUID id,
      TipoOportunidade tipo,
      StatusOportunidade status,
      ClienteResumo cliente,
      VeiculoResumo veiculo,
      OrigemResumo origem,
      ResponsavelResumo responsavel,
      BigDecimal valorPotencial,
      Instant criadoEm,
      Instant elegivelDesde,
      long diasEmAberto,
      Instant ultimoContatoEm,
      Instant proximoContatoEm,
      Instant encerradaEm,
      long revisao) {}

  @Schema(name = "RecuperacaoContatoSaida")
  public record ContatoSaida(
      UUID id,
      UUID usuarioId,
      Instant realizadoEm,
      CanalContato canal,
      ResultadoContato resultado,
      String observacao,
      Instant proximoContatoEm) {}

  @Schema(name = "RecuperacaoResultadoSaida")
  public record ResultadoSaida(
      UUID id,
      UUID usuarioId,
      Instant registradoEm,
      BigDecimal valorRecuperado,
      UUID ordemServicoId,
      String observacao) {}

  @Schema(name = "RecuperacaoEventoSaida")
  public record EventoSaida(
      UUID id,
      UUID usuarioId,
      Instant criadoEm,
      String tipo,
      String anterior,
      String novo,
      String observacao) {}

  @Schema(name = "RecuperacaoDetalhe")
  public record Detalhe(
      OportunidadeSaida oportunidade,
      List<ContatoSaida> contatos,
      ResultadoSaida resultado,
      List<EventoSaida> auditoria) {}

  @Schema(name = "RecuperacaoContatoEntrada")
  public record ContatoEntrada(
      @NotNull CanalContato canal,
      @NotNull ResultadoContato resultado,
      @Size(max = 4000) String observacao,
      @Schema(description = "Próximo contato futuro; omitido/null limpa o agendamento")
          Instant proximoContatoEm,
      @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "RecuperacaoResultadoEntrada")
  public record ResultadoEntrada(
      @Schema(example = "1450.00", description = "Valor efetivo; pode diferir do potencial")
          @NotNull
          @DecimalMin("0.00")
          @Digits(integer = 17, fraction = 2)
          BigDecimal valorRecuperado,
      UUID ordemServicoId,
      @Size(max = 4000) String observacao,
      @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "RecuperacaoStatusEntrada")
  public record StatusEntrada(
      @NotNull StatusOportunidade status,
      @Size(max = 4000) String observacao,
      @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "RecuperacaoResponsavelEntrada")
  public record ResponsavelEntrada(
      @Schema(description = "OWNER/ATENDENTE ativo; null remove atribuição") UUID responsavelId,
      @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "RecuperacaoProximoContatoEntrada")
  public record ProximoContatoEntrada(
      Instant proximoContatoEm, @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "RecuperacaoProgramacaoEntrada")
  public record ProgramacaoEntrada(
      @Schema(example = "2026-10-16", description = "Data local de São Paulo; null remove")
          LocalDate data,
      @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "RecuperacaoProgramacaoSaida")
  public record ProgramacaoSaida(LocalDate data, long revisao) {}

  @Schema(name = "RecuperacaoIdentificacaoSaida")
  public record IdentificacaoSaida(long criadas, long descartadas) {}

  @Schema(name = "RecuperacaoGrupo")
  public record Grupo(String chave, long quantidade) {}

  @Schema(name = "RecuperacaoResumo")
  public record Resumo(
      Instant geradoEm,
      long oportunidadesAbertas,
      BigDecimal valorPotencialConhecido,
      BigDecimal valorRecuperado,
      long quantidadeRecuperada,
      long quantidadePerdida,
      @Schema(
              description =
                  "Percentual: 100 * recuperadas / (recuperadas + perdidas), encerradas no período; 0 sem denominador")
          BigDecimal taxaRecuperacao,
      List<Grupo> porTipo,
      List<Grupo> porStatus,
      List<Grupo> porResponsavel,
      List<Grupo> porPeriodo,
      List<Grupo> porIdade) {}
}
