package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.application.pagamento.PagamentoProvider;
import br.com.garagem.assinatura.domain.*;
import br.com.garagem.assinatura.repository.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.TenantContext;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisões comerciais da assinatura: plano, cancelamento, reativação e confirmação de pagamento.
 *
 * <p>Carregar e normalizar a assinatura é responsabilidade de {@link CicloAssinatura}; trocar de
 * status com os carimbos que o banco exige é de {@link Transicoes}. Aqui ficam só as regras.
 *
 * <p>Regra que atravessa a classe: inadimplência e cancelamento retiram capacidade de crescer,
 * nunca dados. Nada neste serviço apaga registro operacional da oficina.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.legacy-billing.enabled",
    havingValue = "true")
@Service
@Transactional
public class AssinaturaService {
  private final AssinaturaRepository assinaturas;
  private final PlanoRepository planos;
  private final PlanoLimiteService limites;
  private final CobrancaService cobranca;
  private final CicloAssinatura ciclo;
  private final PagamentoProvider provider;
  private final Clock clock;

  public AssinaturaService(
      AssinaturaRepository assinaturas,
      PlanoRepository planos,
      PlanoLimiteService limites,
      CobrancaService cobranca,
      CicloAssinatura ciclo,
      PagamentoProvider provider,
      Clock clock) {
    this.assinaturas = assinaturas;
    this.planos = planos;
    this.limites = limites;
    this.cobranca = cobranca;
    this.ciclo = ciclo;
    this.provider = provider;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Plano> catalogo() {
    return planos.findByAtivoTrueOrderByOrdemAscCodigoAsc();
  }

  /** Assinatura da oficina, travada e com o estado temporal já aplicado. */
  public Assinatura atual() {
    return ciclo.atual();
  }

  private Assinatura bloquear(long revisao) {
    var a = ciclo.atual();
    if (a.revisao != revisao)
      throw ApiException.conflict("A assinatura mudou. Atualize a página antes de decidir.");
    return a;
  }

  /** Mudança de plano solicitada pela oficina. Reduzir plano exige caber no limite menor. */
  public Assinatura mudarPlano(String codigoPlano, long revisao) {
    var a = bloquear(revisao);
    if (a.status == StatusAssinatura.CANCELADA)
      throw ApiException.conflict("Reative a assinatura antes de mudar de plano.");
    var destino = porCodigo(codigoPlano);
    var atual = planos.findById(a.planoId).orElseThrow(ApiException::missing);
    if (destino.id.equals(atual.id)) return a;
    conferirCabeNoPlano(destino);
    provider.mudarPlano(a, destino);
    a.planoId = destino.id;
    a.atualizadoEm = clock.instant();
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_CHANGED,
        "Plano alterado de " + atual.codigo + " para " + destino.codigo + ".",
        UsuarioAutenticado.id(),
        Map.of("de", atual.codigo, "para", destino.codigo));
    return a;
  }

  /**
   * Trocar para um plano menor do que o uso atual deixaria a oficina permanentemente acima do
   * limite. Recusar na hora é mais honesto do que aceitar e bloquear todas as operações seguintes.
   */
  private void conferirCabeNoPlano(Plano destino) {
    var uso = limites.consumo();
    var excedidos = new ArrayList<String>();
    if (uso.usuarios() > destino.maxUsuarios)
      excedidos.add(
          "usuários (%d ativos, limite %d)".formatted(uso.usuarios(), destino.maxUsuarios));
    if (uso.armazenamentoBytes() > destino.maxArmazenamentoBytes)
      excedidos.add(
          "armazenamento (%s usados, limite %s)"
              .formatted(
                  PlanoLimiteService.legivel(uso.armazenamentoBytes()),
                  PlanoLimiteService.legivel(destino.maxArmazenamentoBytes)));
    if (destino.maxVeiculos != null && uso.veiculos() > destino.maxVeiculos)
      excedidos.add(
          "veículos (%d cadastrados, limite %d)".formatted(uso.veiculos(), destino.maxVeiculos));
    if (!excedidos.isEmpty())
      throw new ApiException(
          HttpStatus.CONFLICT,
          ErrorCodes.PLAN_LIMIT_REACHED,
          "Seu uso atual não cabe no plano escolhido: " + String.join("; ", excedidos) + ".");
  }

  /** Pagamento confirmado: o único caminho que leva uma assinatura a ATIVA. */
  public Assinatura confirmarPagamento(Assinatura a, String origem) {
    var anterior = a.status;
    var plano = planos.findById(a.planoId).orElseThrow(ApiException::missing);
    boolean renovar = a.periodoFim.isBefore(clock.instant());
    Transicoes.paraAtiva(a, clock.instant());
    if (renovar) {
      a.periodoInicio = a.periodoFim;
      a.periodoFim = plano.periodicidade.proximo(a.periodoFim);
    }
    cobranca.registrar(
        a,
        TipoEventoCobranca.PAYMENT_APPROVED,
        "Pagamento confirmado via " + origem + ".",
        null,
        Map.of("origem", origem, "plano", plano.codigo));
    if (anterior != StatusAssinatura.ATIVA)
      cobranca.registrar(
          a,
          anterior == StatusAssinatura.SUSPENSA || anterior == StatusAssinatura.INADIMPLENTE
              ? TipoEventoCobranca.ACCOUNT_REACTIVATED
              : TipoEventoCobranca.SUBSCRIPTION_ACTIVATED,
          "Assinatura ativa. Acesso do plano " + plano.codigo + " restaurado.",
          null,
          Map.of("anterior", anterior.name()));
    return a;
  }

  /** Falha de pagamento: entra em tolerância, nunca suspende direto. */
  public Assinatura registrarFalhaDePagamento(Assinatura a, String motivo) {
    cobranca.registrar(
        a,
        TipoEventoCobranca.PAYMENT_FAILED,
        "Pagamento não confirmado: " + motivo,
        null,
        Map.of("status", a.status.name()));
    if (a.status == StatusAssinatura.CANCELADA) return a;
    if (a.status != StatusAssinatura.INADIMPLENTE && a.status != StatusAssinatura.SUSPENSA) {
      Transicoes.paraInadimplente(a, clock.instant());
      cobranca.registrar(
          a,
          TipoEventoCobranca.ACCOUNT_PAST_DUE,
          "Assinatura em atraso. Tolerância de "
              + ciclo.tolerancia().toDays()
              + " dia(s) antes da suspensão; nenhum dado é removido.",
          null,
          Map.of("tolerancia_dias", ciclo.tolerancia().toDays()));
    }
    return a;
  }

  /** Suspensão por tolerância vencida, para quem opera fora do contexto autenticado (webhook). */
  public Assinatura aplicarTolerancia(Assinatura a) {
    return ciclo.aplicarTolerancia(a);
  }

  /** Força a suspensão quando o próprio provedor informa que a assinatura foi suspensa. */
  public Assinatura suspenderPorInformeDoProvedor(Assinatura a) {
    if (a.status == StatusAssinatura.CANCELADA) return a;
    Transicoes.paraSuspensa(a, clock.instant());
    cobranca.registrar(
        a,
        TipoEventoCobranca.ACCOUNT_SUSPENDED,
        "Suspensão informada pelo provedor. Leitura, exportação e pagamento seguem liberados.",
        null,
        null);
    return a;
  }

  /** Cancelamento informado pelo provedor: encerra sem passar pelas validações da oficina. */
  public Assinatura cancelarPorInformeDoProvedor(Assinatura a) {
    if (a.status == StatusAssinatura.CANCELADA) return a;
    Transicoes.paraCancelada(a, clock.instant());
    if (a.cancelamentoMotivo == null) a.cancelamentoMotivo = "Cancelada pelo provedor.";
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_CANCELED,
        "Cancelamento informado pelo provedor. Dados preservados.",
        null,
        null);
    return a;
  }

  /**
   * Cancelamento. Imediato encerra o acesso agora; agendado preserva o que já foi pago até o fim do
   * período. Nenhum dado é apagado em qualquer um dos dois.
   */
  public Assinatura cancelar(boolean imediato, String motivo, long revisao) {
    var a = bloquear(revisao);
    if (a.status == StatusAssinatura.CANCELADA)
      throw ApiException.conflict("A assinatura já está cancelada.");
    if (motivo == null || motivo.isBlank())
      throw ApiException.invalid("Informe o motivo do cancelamento.");
    provider.cancelarAssinatura(a, imediato);
    var agora = clock.instant();
    Transicoes.agendarCancelamento(a, imediato ? agora : a.periodoFim, agora);
    a.cancelamentoMotivo = motivo.trim();
    a.canceladaPor = UsuarioAutenticado.id();
    // Cancelar imediato limpa o carimbo de suspensão junto com a troca de status. Sem isso o banco
    // recusava a operação, e a oficina suspensa — a que mais tem motivo para cancelar — ficava
    // presa.
    if (imediato) Transicoes.paraCancelada(a, agora);
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_CANCELED,
        (imediato ? "Cancelamento imediato" : "Cancelamento ao fim do período")
            + " solicitado. Acesso até "
            + a.cancelamentoEfetivoEm
            + ". Os dados da oficina são preservados.",
        a.canceladaPor,
        Map.of("imediato", imediato, "motivo", a.cancelamentoMotivo));
    return a;
  }

  /**
   * Reativação pedida pela oficina. Desfaz um cancelamento agendado sem cobrança nova; a partir de
   * CANCELADA volta para tolerância, porque quem confirma pagamento é o webhook, não esta chamada.
   */
  public Assinatura reativar(long revisao) {
    var a = bloquear(revisao);
    if (a.status != StatusAssinatura.CANCELADA && a.canceladaEm == null)
      throw ApiException.conflict("A assinatura não está cancelada.");
    provider.reativarAssinatura(a);
    boolean agendado = a.status != StatusAssinatura.CANCELADA;
    var agora = clock.instant();
    Transicoes.revogarCancelamento(a, agora);
    if (!agendado) Transicoes.paraInadimplente(a, agora);
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_REACTIVATED,
        agendado
            ? "Cancelamento agendado revogado; a assinatura segue no ciclo atual."
            : "Assinatura reativada. O acesso completo volta na confirmação do pagamento.",
        UsuarioAutenticado.id(),
        Map.of("agendado", agendado));
    return a;
  }

  private Plano porCodigo(String codigo) {
    return planos
        .findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT))
        .filter(p -> p.ativo)
        .orElseThrow(() -> ApiException.invalid("Plano inexistente ou indisponível."));
  }

  /** Leitura crua, sem normalizar: usada só por teste e diagnóstico. */
  @Transactional(readOnly = true)
  public Optional<Assinatura> semNormalizar() {
    return assinaturas.findByOficinaId(TenantContext.current());
  }
}
