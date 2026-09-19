package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.application.pagamento.PagamentoProvider;
import br.com.garagem.assinatura.domain.*;
import br.com.garagem.assinatura.repository.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.tenancy.TenantContext;
import java.time.Clock;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Recebimento de eventos do gateway. Três garantias, nesta ordem: a origem é autenticada, o evento
 * é gravado antes de ser interpretado e o mesmo {@code providerEventId} nunca é processado duas
 * vezes.
 *
 * <p>A idempotência é do banco, não de memória: o unique {@code (provedor, provider_event_id)} é
 * quem decide o empate entre duas entregas simultâneas do mesmo evento.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.legacy-billing.enabled",
    havingValue = "true")
@Service
public class WebhookPagamentoService {
  /** Resultado do processamento, para o controller escolher o status HTTP sem reinterpretar. */
  public enum Resultado {
    PROCESSADO,
    DUPLICADO,
    IGNORADO
  }

  private final WebhookPagamentoRepository webhooks;
  private final CicloAssinatura ciclo;
  private final AssinaturaService service;
  private final CobrancaService cobranca;
  private final PagamentoProvider provider;
  private final JdbcTemplate jdbc;
  private final org.springframework.transaction.support.TransactionTemplate tx;
  private final Clock clock;

  public WebhookPagamentoService(
      WebhookPagamentoRepository webhooks,
      CicloAssinatura ciclo,
      AssinaturaService service,
      CobrancaService cobranca,
      PagamentoProvider provider,
      JdbcTemplate jdbc,
      org.springframework.transaction.PlatformTransactionManager transacoes,
      Clock clock) {
    this.webhooks = webhooks;
    this.ciclo = ciclo;
    this.service = service;
    this.cobranca = cobranca;
    this.provider = provider;
    this.jdbc = jdbc;
    this.tx = new org.springframework.transaction.support.TransactionTemplate(transacoes);
    this.clock = clock;
  }

  public boolean origemValida(String corpoCru, Map<String, String> cabecalhos) {
    return provider.assinaturaValida(corpoCru, cabecalhos);
  }

  /**
   * Grava o recebimento em transação própria. Se o processamento falhar depois, o registro
   * permanece para investigação e reprocessamento, em vez de sumir com o rollback.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WebhookPagamento registrarRecebimento(String corpoCru) {
    var evento = provider.lerEvento(corpoCru);
    if (evento == null)
      throw ApiException.invalid("Payload do webhook inválido: informe id e tipo do evento.");
    // Insere de forma tolerante à corrida e relê. Consultar-e-inserir deixaria duas entregas
    // simultâneas colidirem no unique, e capturar a violação não resolve: a transação já fica
    // marcada para rollback, e o gateway receberia 409 num evento que na verdade foi registrado.
    jdbc.update(
        """
        insert into webhook_pagamento(id,criado_em,provedor,provider_event_id,tipo,payload,processado)
        values(?,?,?,?,?,?,false) on conflict(provedor,provider_event_id) do nothing
        """,
        UUID.randomUUID(),
        java.sql.Timestamp.from(clock.instant()),
        provider.nome(),
        evento.id(),
        evento.tipo(),
        corpoCru.length() > 20000 ? corpoCru.substring(0, 20000) : corpoCru);
    return webhooks
        .findByProvedorAndProviderEventId(provider.nome(), evento.id())
        .orElseThrow(ApiException::missing);
  }

  /**
   * Interpreta o evento já persistido. O tenant não vem da requisição: é descoberto pela assinatura
   * remota e só então instalado no contexto, o que impede um payload de escolher a oficina alvo por
   * conta própria.
   */
  public Resultado processar(WebhookPagamento w, String corpoCru) {
    var evento = provider.lerEvento(corpoCru);
    UUID oficina = oficinaDa(evento.providerSubscriptionId());
    if (oficina == null) {
      marcar(w, false, "Assinatura externa desconhecida.");
      return Resultado.IGNORADO;
    }
    UUID anterior = TenantContext.resolvedOrEmpty();
    try {
      // O tenant precisa estar instalado antes de a sessão do Hibernate abrir: @TenantId é
      // capturado na abertura, então uma transação iniciada antes disto não enxergaria a
      // assinatura. Por isso a transação é aberta aqui, à mão, e não por @Transactional.
      TenantContext.set(oficina);
      return tx.execute(status -> aplicarEvento(w, oficina));
    } finally {
      if (TenantContext.UNRESOLVED.equals(anterior)) TenantContext.clear();
      else TenantContext.set(anterior);
    }
  }

  private Resultado aplicarEvento(WebhookPagamento w, UUID oficina) {
    // Reivindica o evento no banco, não em memória. O UPDATE condicional é atômico: entregas
    // simultâneas do mesmo id bloqueiam nesta linha, e só a primeira transação a confirmar
    // enxerga processado=false. As demais atualizam zero linhas e saem como duplicadas.
    //
    // A reivindicação vive na MESMA transação do efeito: se aplicar o evento falhar, o rollback
    // devolve o evento para reprocessamento em vez de marcá-lo como resolvido.
    if (!reivindicar(w)) return Resultado.DUPLICADO;
    var a = ciclo.de(oficina);
    w.oficinaId = oficina;
    w.assinaturaId = a.id;
    cobranca.registrarWebhook(
        a,
        TipoEventoCobranca.WEBHOOK_RECEIVED,
        w.providerEventId,
        "Evento " + w.tipo + " recebido.");
    boolean tratado = aplicar(a, w.tipo);
    cobranca.registrarWebhook(
        a,
        tratado ? TipoEventoCobranca.WEBHOOK_PROCESSED : TipoEventoCobranca.WEBHOOK_FAILED,
        w.providerEventId,
        tratado ? "Evento " + w.tipo + " aplicado." : "Evento " + w.tipo + " sem tratamento.");
    concluir(w, oficina, a.id, tratado ? null : "Tipo sem tratamento nesta versão.");
    return tratado ? Resultado.PROCESSADO : Resultado.IGNORADO;
  }

  /**
   * Marca o evento como processado apenas se ainda não estava. Devolve true para quem conseguiu.
   */
  private boolean reivindicar(WebhookPagamento w) {
    int linhas =
        jdbc.update(
            "update webhook_pagamento set processado=true, processado_em=? where id=? and processado=false",
            java.sql.Timestamp.from(clock.instant()),
            w.id);
    if (linhas == 1) w.processado = true;
    return linhas == 1;
  }

  /** Completa o registro do evento já reivindicado, sem mexer no campo que serve de trava. */
  private void concluir(WebhookPagamento w, UUID oficina, UUID assinatura, String erro) {
    w.oficinaId = oficina;
    w.assinaturaId = assinatura;
    w.erro = erro;
    jdbc.update(
        "update webhook_pagamento set erro=?, oficina_id=?, assinatura_id=? where id=?",
        erro,
        oficina,
        assinatura,
        w.id);
    LoggerFactory.getLogger(WebhookPagamentoService.class)
        .atInfo()
        .addKeyValue("webhook_id", w.id)
        .addKeyValue("webhook_tipo", w.tipo)
        .addKeyValue("webhook_processado", true)
        .log("webhook_pagamento");
  }

  /**
   * Vocabulário mínimo e explícito. Um tipo desconhecido é registrado e ignorado — jamais tratado
   * como pagamento aprovado por omissão.
   */
  private boolean aplicar(Assinatura a, String tipo) {
    return switch (tipo) {
      case "pagamento.aprovado", "assinatura.renovada" -> {
        service.confirmarPagamento(a, "webhook " + tipo);
        yield true;
      }
      case "pagamento.falhou" -> {
        service.registrarFalhaDePagamento(a, "evento " + tipo);
        service.aplicarTolerancia(a);
        yield true;
      }
      case "assinatura.suspensa" -> {
        service.registrarFalhaDePagamento(a, "suspensão informada pelo provedor");
        service.suspenderPorInformeDoProvedor(a);
        yield true;
      }
      case "assinatura.cancelada" -> {
        service.cancelarPorInformeDoProvedor(a);
        yield true;
      }
      default -> false;
    };
  }

  /** Marcação fora do JPA: o registro do webhook não pertence a tenant algum. */
  private void marcar(WebhookPagamento w, boolean processado, String erro) {
    w.processado = processado;
    w.processadoEm = clock.instant();
    w.erro = erro;
    jdbc.update(
        "update webhook_pagamento set processado=?, processado_em=?, erro=?, oficina_id=?, assinatura_id=? where id=?",
        processado,
        java.sql.Timestamp.from(w.processadoEm),
        erro,
        w.oficinaId,
        w.assinaturaId,
        w.id);
    LoggerFactory.getLogger(WebhookPagamentoService.class)
        .atInfo()
        .addKeyValue("webhook_id", w.id)
        .addKeyValue("webhook_tipo", w.tipo)
        .addKeyValue("webhook_processado", processado)
        .log("webhook_pagamento");
  }

  private UUID oficinaDa(String providerSubscriptionId) {
    if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) return null;
    var encontrados =
        jdbc.query(
            "select oficina_id from assinatura where provedor=? and provider_subscription_id=?",
            (rs, n) -> rs.getObject(1, UUID.class),
            provider.nome(),
            providerSubscriptionId);
    return encontrados.size() == 1 ? encontrados.getFirst() : null;
  }
}
