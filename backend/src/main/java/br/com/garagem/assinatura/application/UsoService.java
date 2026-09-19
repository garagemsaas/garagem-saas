package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.api.AssinaturaDtos.*;
import br.com.garagem.assinatura.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduz plano e consumo para a resposta que a oficina lê. Um limite só aparece aqui se existir no
 * plano, de modo que acrescentar um limite novo em {@link Plano} baste para ele surgir na tela.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.legacy-billing.enabled",
    havingValue = "true")
@Service
public class UsoService {
  private final PlanoLimiteService limites;
  private final Clock clock;

  public UsoService(PlanoLimiteService limites, Clock clock) {
    this.limites = limites;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ConsumoSaida consumo(Assinatura a, Plano plano) {
    var uso = limites.consumo();
    var itens = new ArrayList<LimiteUso>();
    itens.add(
        limite("usuarios", "Usuários ativos", uso.usuarios(), (long) plano.maxUsuarios, false));
    itens.add(
        limite(
            "armazenamento",
            "Armazenamento",
            uso.armazenamentoBytes(),
            plano.maxArmazenamentoBytes,
            true));
    itens.add(
        limite(
            "veiculos",
            "Veículos cadastrados",
            uso.veiculos(),
            plano.maxVeiculos == null ? null : plano.maxVeiculos.longValue(),
            false));
    itens.add(
        limite(
            "ordensServicoMes",
            "Ordens de serviço no mês",
            uso.ordensNoMes(),
            plano.maxOrdensServicoMes == null ? null : plano.maxOrdensServicoMes.longValue(),
            false));
    return new ConsumoSaida(clock.instant(), plano.codigo, a.status, List.copyOf(itens));
  }

  private static LimiteUso limite(
      String chave, String rotulo, long usado, Long maximo, boolean emBytes) {
    return new LimiteUso(
        chave,
        rotulo,
        usado,
        maximo,
        emBytes ? PlanoLimiteService.legivel(usado) : String.valueOf(usado),
        maximo == null
            ? "Ilimitado"
            : emBytes ? PlanoLimiteService.legivel(maximo) : String.valueOf(maximo),
        PlanoLimiteService.percentual(usado, maximo),
        maximo != null && usado >= maximo);
  }
}
