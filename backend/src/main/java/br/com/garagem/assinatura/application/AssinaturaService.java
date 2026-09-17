package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.application.pagamento.PagamentoProvider;
import br.com.garagem.assinatura.domain.*;
import br.com.garagem.assinatura.repository.*;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.tenancy.TenantContext;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida da assinatura da oficina. Toda transição passa por aqui, sempre sob lock da linha e
 * sempre deixando evento de auditoria — inclusive as disparadas por webhook.
 *
 * <p>Regra que atravessa a classe: inadimplência e cancelamento retiram capacidade de crescer,
 * nunca dados. Nada neste serviço apaga registro operacional da oficina.
 */
@Service
@Transactional
public class AssinaturaService {
  /** Tolerância antes de suspender. Configurável; o padrão acompanha o ciclo de faturamento. */
  private final Duration tolerancia;

  private final AssinaturaRepository assinaturas;
  private final PlanoRepository planos;
  private final PlanoLimiteService limites;
  private final CobrancaService cobranca;
  private final PagamentoProvider provider;
  private final Clock clock;

  public AssinaturaService(
      AssinaturaRepository assinaturas,
      PlanoRepository planos,
      PlanoLimiteService limites,
      CobrancaService cobranca,
      PagamentoProvider provider,
      Clock clock,
      @org.springframework.beans.factory.annotation.Value("${app.pagamento.tolerancia:P7D}")
          Duration tolerancia) {
    this.assinaturas = assinaturas;
    this.planos = planos;
    this.limites = limites;
    this.cobranca = cobranca;
    this.provider = provider;
    this.clock = clock;
    this.tolerancia = tolerancia;
  }

  @Transactional(readOnly = true)
  public List<Plano> catalogo() {
    return planos.findByAtivoTrueOrderByOrdemAscCodigoAsc();
  }

  private Assinatura bloquear(long revisao) {
    var a =
        assinaturas
            .lock(TenantContext.current())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCodes.SUBSCRIPTION_INACTIVE,
                        "Esta oficina não possui assinatura configurada. Fale com o suporte."));
    if (a.revisao != revisao)
      throw ApiException.conflict("A assinatura mudou. Atualize a página antes de decidir.");
    return a;
  }

  /**
   * Cria a assinatura da oficina no gateway e a deixa pronta para cobrar. Idempotente por oficina:
   * uma segunda chamada devolve a existente em vez de criar cliente duplicado no provedor.
   */
  public Assinatura contratar(String codigoPlano, String nomeOficina, String emailResponsavel) {
    var existente = assinaturas.findByOficinaId(TenantContext.current());
    if (existente.isPresent()) return existente.get();
    var plano = porCodigo(codigoPlano);
    var a = new Assinatura();
    a.criadoEm = clock.instant();
    a.planoId = plano.id;
    a.status = StatusAssinatura.TRIAL;
    a.provedor = provider.nome();
    a.periodoInicio = clock.instant();
    a.periodoFim = plano.periodicidade.proximo(clock.instant());
    a.trialInicio = a.periodoInicio;
    a.trialFim = a.periodoFim;
    a.atualizadoEm = clock.instant();
    assinaturas.save(a);
    a.providerCustomerId = provider.criarCliente(a, nomeOficina, emailResponsavel);
    a.providerSubscriptionId = provider.criarAssinatura(a, plano);
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_CREATED,
        "Assinatura criada no plano " + plano.codigo + ".",
        OsService.autor(),
        Map.of("plano", plano.codigo, "provedor", a.provedor));
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
        OsService.autor(),
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
    a.status = StatusAssinatura.ATIVA;
    a.inadimplenteDesde = null;
    a.suspensaEm = null;
    if (a.periodoFim.isBefore(clock.instant())) {
      a.periodoInicio = a.periodoFim;
      a.periodoFim = plano.periodicidade.proximo(a.periodoFim);
    }
    a.atualizadoEm = clock.instant();
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
      a.status = StatusAssinatura.INADIMPLENTE;
      a.inadimplenteDesde = clock.instant();
      a.atualizadoEm = clock.instant();
      cobranca.registrar(
          a,
          TipoEventoCobranca.ACCOUNT_PAST_DUE,
          "Assinatura em atraso. Tolerância de "
              + tolerancia.toDays()
              + " dia(s) antes da suspensão; nenhum dado é removido.",
          null,
          Map.of("tolerancia_dias", tolerancia.toDays()));
    }
    return a;
  }

  /**
   * Aplica a tolerância vencida. Idempotente e sem relógio próprio: pode ser chamada por webhook,
   * por leitura da própria oficina ou por rotina futura sem duplicar suspensão.
   */
  public Assinatura aplicarTolerancia(Assinatura a) {
    if (a.status != StatusAssinatura.INADIMPLENTE || a.inadimplenteDesde == null) return a;
    if (clock.instant().isBefore(a.inadimplenteDesde.plus(tolerancia))) return a;
    a.status = StatusAssinatura.SUSPENSA;
    a.suspensaEm = clock.instant();
    a.atualizadoEm = clock.instant();
    cobranca.registrar(
        a,
        TipoEventoCobranca.ACCOUNT_SUSPENDED,
        "Assinatura suspensa após a tolerância. Leitura, exportação e pagamento seguem liberados.",
        null,
        Map.of("inadimplente_desde", a.inadimplenteDesde.toString()));
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
    a.canceladaEm = clock.instant();
    a.cancelamentoEfetivoEm = imediato ? clock.instant() : a.periodoFim;
    a.cancelamentoMotivo = motivo.trim();
    a.canceladaPor = OsService.autor();
    a.atualizadoEm = clock.instant();
    // Agendado mantém o status atual até a data efetiva: a oficina segue operando o que pagou.
    if (imediato) a.status = StatusAssinatura.CANCELADA;
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

  /** Encerra um cancelamento agendado cuja data já passou. Idempotente. */
  public Assinatura aplicarCancelamentoAgendado(Assinatura a) {
    if (a.cancelamentoEfetivoEm == null || a.status == StatusAssinatura.CANCELADA) return a;
    if (clock.instant().isBefore(a.cancelamentoEfetivoEm)) return a;
    a.status = StatusAssinatura.CANCELADA;
    a.atualizadoEm = clock.instant();
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_CANCELED,
        "Cancelamento agendado entrou em vigor. Dados preservados para consulta e exportação.",
        null,
        null);
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
    a.canceladaEm = null;
    a.cancelamentoEfetivoEm = null;
    a.cancelamentoMotivo = null;
    a.canceladaPor = null;
    if (!agendado) {
      a.status = StatusAssinatura.INADIMPLENTE;
      a.inadimplenteDesde = clock.instant();
      a.suspensaEm = null;
    }
    a.atualizadoEm = clock.instant();
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_REACTIVATED,
        agendado
            ? "Cancelamento agendado revogado; a assinatura segue no ciclo atual."
            : "Assinatura reativada. O acesso completo volta na confirmação do pagamento.",
        OsService.autor(),
        Map.of("agendado", agendado));
    return a;
  }

  /**
   * Normaliza o estado no momento da leitura: aplica tolerância vencida e cancelamento agendado.
   * Evita que a oficina veja um estado defasado só porque nenhuma rotina passou por ali.
   */
  public Assinatura atual() {
    var a = assinaturas.lock(TenantContext.current()).orElseThrow(ApiException::missing);
    return aplicarCancelamentoAgendado(aplicarTolerancia(a));
  }

  private Plano porCodigo(String codigo) {
    return planos
        .findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT))
        .filter(p -> p.ativo)
        .orElseThrow(() -> ApiException.invalid("Plano inexistente ou indisponível."));
  }
}
