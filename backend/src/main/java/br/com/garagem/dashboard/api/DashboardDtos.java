package br.com.garagem.dashboard.api;

import br.com.garagem.ordemservico.domain.StatusOs;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class DashboardDtos {
  private DashboardDtos() {}

  @Schema(
      description =
          "Indicadores operacionais da oficina, calculados no banco. Substitui o download da"
              + " listagem completa de OS só para somar números na tela.")
  public record DashboardSaida(
      @Schema(description = "Instante em que os números foram apurados") Instant geradoEm,
      @Schema(
              description =
                  "Quantidade de OS em cada status. Todos os status aparecem, inclusive com zero.")
          Map<StatusOs, Long> porStatus,
      @Schema(description = "OS que ainda não chegaram a PRONTO", example = "12") long emAndamento,
      @Schema(description = "OS em PRONTO aguardando retirada", example = "3") long prontas,
      @Schema(description = "OS concluídas nos últimos sete dias", example = "9")
          long concluidasSeteDias,
      @Schema(description = "OS abertas hoje no fuso consultado", example = "2") long entradasHoje,
      @Schema(description = "OS não concluídas cuja previsão de entrega já passou", example = "1")
          long atrasadas,
      @Schema(description = "OS não concluídas sem mecânico responsável", example = "4")
          long semResponsavel,
      @Schema(description = "Orçamentos na versão atual ainda sem aprovação ou recusa")
          OrcamentosPendentes orcamentosAguardandoDecisao) {}

  @Schema(
      description =
          "Orçamentos aguardando decisão do cliente. O total é a soma das versões atuais e não"
              + " representa receita realizada.")
  public record OrcamentosPendentes(
      @Schema(description = "Quantidade de orçamentos aguardando decisão", example = "5")
          long quantidade,
      @Schema(description = "Soma dos totais dessas versões", example = "4820.50")
          BigDecimal total) {}
}
