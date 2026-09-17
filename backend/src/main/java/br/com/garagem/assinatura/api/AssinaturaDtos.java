package br.com.garagem.assinatura.api;

import br.com.garagem.assinatura.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AssinaturaDtos {
  private AssinaturaDtos() {}

  @Schema(name = "PlanoSaida")
  public record PlanoSaida(
      UUID id,
      String codigo,
      String nome,
      String descricao,
      @Schema(description = "Valor em centavos; 0 enquanto o preço comercial não for definido")
          long valorCentavos,
      String moeda,
      Periodicidade periodicidade,
      int maxUsuarios,
      long maxArmazenamentoBytes,
      @Schema(description = "Nulo significa ilimitado") Integer maxOrdensServicoMes,
      @Schema(description = "Nulo significa ilimitado") Integer maxVeiculos,
      int ordem,
      boolean atual) {
    public static PlanoSaida de(Plano p, boolean atual) {
      return new PlanoSaida(
          p.id,
          p.codigo,
          p.nome,
          p.descricao,
          p.valorCentavos,
          p.moeda,
          p.periodicidade,
          p.maxUsuarios,
          p.maxArmazenamentoBytes,
          p.maxOrdensServicoMes,
          p.maxVeiculos,
          p.ordem,
          atual);
    }
  }

  /** Um limite do plano com o consumo correspondente, pronto para virar barra de progresso. */
  @Schema(name = "LimiteUso")
  public record LimiteUso(
      String chave,
      String rotulo,
      long usado,
      @Schema(description = "Nulo significa ilimitado") Long limite,
      String usadoLegivel,
      String limiteLegivel,
      int percentual,
      boolean atingido) {}

  @Schema(name = "AssinaturaSaida")
  public record AssinaturaSaida(
      UUID id,
      PlanoSaida plano,
      StatusAssinatura status,
      @Schema(description = "Texto pronto para exibição do estado comercial") String situacao,
      boolean permiteCrescer,
      String provedor,
      Instant periodoInicio,
      @Schema(description = "Data da próxima cobrança enquanto a assinatura estiver vigente")
          Instant periodoFim,
      Instant trialFim,
      Instant inadimplenteDesde,
      Instant suspensaEm,
      Instant canceladaEm,
      Instant cancelamentoEfetivoEm,
      String cancelamentoMotivo,
      long revisao) {}

  @Schema(name = "ConsumoSaida")
  public record ConsumoSaida(
      Instant geradoEm, String plano, StatusAssinatura status, List<LimiteUso> limites) {}

  @Schema(name = "AssinaturaDetalhe")
  public record Detalhe(
      AssinaturaSaida assinatura, ConsumoSaida consumo, List<PlanoSaida> planos) {}

  @Schema(name = "MudarPlanoEntrada")
  public record MudarPlanoEntrada(
      @NotBlank @Size(max = 40) String codigo, @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "CancelamentoEntrada")
  public record CancelamentoEntrada(
      @Schema(description = "true encerra agora; false mantém o acesso até o fim do período pago")
          boolean imediato,
      @NotBlank @Size(max = 500) String motivo,
      @NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "ReativacaoEntrada")
  public record ReativacaoEntrada(@NotNull @PositiveOrZero Long revisao) {}

  @Schema(name = "EventoCobrancaSaida")
  public record EventoCobrancaSaida(
      UUID id,
      Instant criadoEm,
      TipoEventoCobranca tipo,
      String mensagem,
      String provedor,
      UUID usuarioId,
      String metadados) {
    public static EventoCobrancaSaida de(EventoCobranca e) {
      return new EventoCobrancaSaida(
          e.id, e.criadoEm, e.tipo, e.mensagem, e.provedor, e.usuarioId, e.metadados);
    }
  }

  @Schema(name = "WebhookSaida")
  public record WebhookSaida(String status, String eventoId) {}
}
