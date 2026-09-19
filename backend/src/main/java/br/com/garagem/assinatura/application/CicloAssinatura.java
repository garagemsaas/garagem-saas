package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.application.pagamento.PagamentoProvider;
import br.com.garagem.assinatura.domain.*;
import br.com.garagem.assinatura.repository.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta única para obter a assinatura da oficina em estado correto.
 *
 * <p>Resolve três problemas que antes se manifestavam separados:
 *
 * <ul>
 *   <li><b>Vínculo com o provedor.</b> A assinatura nasce pelo gatilho do banco, junto com a
 *       oficina, e nascia sem identificador externo. Como o webhook localiza a oficina por esse
 *       identificador, nenhum pagamento podia ser confirmado. O vínculo passa a ser garantido aqui,
 *       de forma idempotente, na primeira vez que a assinatura é usada.
 *   <li><b>Estado temporal.</b> A passagem de INADIMPLENTE para SUSPENSA só acontecia quando alguém
 *       abria a tela de cobrança. Agora acontece em qualquer caminho que consulte a assinatura,
 *       inclusive na checagem de limite.
 *   <li><b>Concorrência de limites.</b> A leitura trava a linha da assinatura ({@code
 *       PESSIMISTIC_WRITE}). Como toda checagem de limite passa por aqui antes de contar, as
 *       criações concorrentes da mesma oficina serializam no banco — vale para várias instâncias da
 *       API, sem lock em memória.
 * </ul>
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.legacy-billing.enabled",
    havingValue = "true")
@Service
public class CicloAssinatura {
  private final AssinaturaRepository assinaturas;
  private final PlanoRepository planos;
  private final CobrancaService cobranca;
  private final PagamentoProvider provider;
  private final JdbcTemplate jdbc;
  private final EntityManager em;
  private final org.springframework.transaction.support.TransactionTemplate proprias;
  private final Clock clock;
  private final Duration tolerancia;

  public CicloAssinatura(
      AssinaturaRepository assinaturas,
      PlanoRepository planos,
      CobrancaService cobranca,
      PagamentoProvider provider,
      JdbcTemplate jdbc,
      EntityManager em,
      org.springframework.transaction.PlatformTransactionManager transacoes,
      Clock clock,
      @Value("${app.pagamento.tolerancia:P7D}") Duration tolerancia) {
    this.assinaturas = assinaturas;
    this.planos = planos;
    this.cobranca = cobranca;
    this.provider = provider;
    this.jdbc = jdbc;
    this.em = em;
    var template = new org.springframework.transaction.support.TransactionTemplate(transacoes);
    template.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.proprias = template;
    this.clock = clock;
    this.tolerancia = tolerancia;
  }

  public Duration tolerancia() {
    return tolerancia;
  }

  /**
   * Assinatura da oficina autenticada, travada e normalizada. Exige transação de escrita: pode
   * gravar o vínculo com o provedor e aplicar suspensão ou cancelamento vencidos.
   */
  @Transactional
  public Assinatura atual() {
    return de(TenantContext.current());
  }

  /**
   * Assinatura travada e normalizada, para uma oficina conhecida.
   *
   * <p>A normalização roda em transação própria e confirma antes de devolver. O motivo é concreto:
   * quem chama para conferir limite costuma terminar lançando 402, e o rollback dessa recusa
   * desfazia a suspensão que acabara de ser aplicada — a oficina era barrada, mas continuava
   * gravada como apenas inadimplente, e na tentativa seguinte tudo se repetia. Passagem de tempo é
   * fato consumado, não parte da operação que o usuário tentou.
   *
   * <p>Depois de normalizar, a linha é travada de novo na transação de quem chamou: é esse lock que
   * serializa as checagens de limite concorrentes da mesma oficina.
   */
  @Transactional
  public Assinatura de(java.util.UUID oficina) {
    proprias.executeWithoutResult(status -> normalizar(bloquear(oficina)));
    return bloquear(oficina);
  }

  private Assinatura bloquear(java.util.UUID oficina) {
    return assinaturas
        .lock(oficina)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCodes.SUBSCRIPTION_INACTIVE,
                    "Esta oficina não possui assinatura configurada. Fale com o suporte."));
  }

  private Assinatura normalizar(Assinatura a) {
    garantirVinculoComProvedor(a);
    aplicarTolerancia(a);
    aplicarCancelamentoAgendado(a);
    // Normalizar pode gravar, e gravar incrementa @Version. Sem descarregar agora, quem lê devolve
    // a revisão anterior ao próprio efeito — e a primeira ação da tela cairia em 409 na cara do
    // usuário, por um conflito que não existiu.
    em.flush();
    return a;
  }

  public Plano plano(Assinatura a) {
    return planos.findById(a.planoId).orElseThrow(ApiException::missing);
  }

  /**
   * Registra a oficina no provedor na primeira utilização e guarda os identificadores. Idempotente:
   * só age quando falta o vínculo, e o provedor manual devolve identificadores determinísticos.
   */
  public void garantirVinculoComProvedor(Assinatura a) {
    if (a.providerSubscriptionId != null && a.providerCustomerId != null) return;
    var dados =
        jdbc.query(
            """
            select o.nome, (select u.email from usuario u
                             where u.oficina_id=o.id and u.papel='OWNER' and u.ativo=true
                             order by u.criado_em limit 1)
              from oficina o where o.id=?
            """,
            (rs, n) -> new String[] {rs.getString(1), rs.getString(2)},
            a.oficinaId);
    String nome = dados.isEmpty() ? "" : dados.getFirst()[0];
    String email = dados.isEmpty() ? null : dados.getFirst()[1];
    if (a.providerCustomerId == null) a.providerCustomerId = provider.criarCliente(a, nome, email);
    if (a.providerSubscriptionId == null)
      a.providerSubscriptionId = provider.criarAssinatura(a, plano(a));
    a.provedor = provider.nome();
    a.atualizadoEm = clock.instant();
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_CREATED,
        "Assinatura vinculada ao provedor " + a.provedor + ".",
        null,
        Map.of("provedor", a.provedor, "plano", plano(a).codigo));
  }

  /**
   * Suspende quando a tolerância venceu. Idempotente e sem relógio próprio: pode ser chamada por
   * webhook, por leitura ou por checagem de limite sem duplicar suspensão.
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

  /** Encerra um cancelamento agendado cuja data já passou. Idempotente. */
  public Assinatura aplicarCancelamentoAgendado(Assinatura a) {
    if (a.cancelamentoEfetivoEm == null || a.status == StatusAssinatura.CANCELADA) return a;
    if (clock.instant().isBefore(a.cancelamentoEfetivoEm)) return a;
    Transicoes.paraCancelada(a, clock.instant());
    cobranca.registrar(
        a,
        TipoEventoCobranca.SUBSCRIPTION_CANCELED,
        "Cancelamento agendado entrou em vigor. Dados preservados para consulta e exportação.",
        null,
        null);
    return a;
  }
}
