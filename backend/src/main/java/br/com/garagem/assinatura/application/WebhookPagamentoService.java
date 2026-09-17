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
@Service
public class WebhookPagamentoService {
  /** Resultado do processamento, para o controller escolher o status HTTP sem reinterpretar. */
  public enum Resultado {
    PROCESSADO,
    DUPLICADO,
    IGNORADO
  }

  private final WebhookPagamentoRepository webhooks;
  private final AssinaturaRepository assinaturas;
  private final AssinaturaService service;
  private final CobrancaService cobranca;
  private final PagamentoProvider provider;
  private final JdbcTemplate jdbc;
  private final org.springframework.transaction.support.TransactionTemplate tx;
  private final Clock clock;

  public WebhookPagamentoService(
      WebhookPagamentoRepository webhooks,
      AssinaturaRepository assinaturas,
      AssinaturaService service,
      CobrancaService cobranca,
      PagamentoProvider provider,
      JdbcTemplate jdbc,
      org.springframework.transaction.PlatformTransactionManager transacoes,
      Clock clock) {
    this.webhooks = webhooks;
    this.assinaturas = assinaturas;
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
    var existente = webhooks.findByProvedorAndProviderEventId(provider.nome(), evento.id());
    if (existente.isPresent()) return existente.get();
    var w = new WebhookPagamento();
    w.criadoEm = clock.instant();
    w.provedor = provider.nome();
    w.providerEventId = evento.id();
    w.tipo = evento.tipo();
    // Corpo cru truncado: o payload é prova do recebido, não um depósito ilimitado.
    w.payload = corpoCru.length() > 20000 ? corpoCru.substring(0, 20000) : corpoCru;
    return webhooks.save(w);
  }

  /**
   * Interpreta o evento já persistido. O tenant não vem da requisição: é descoberto pela assinatura
   * remota e só então instalado no contexto, o que impede um payload de escolher a oficina alvo por
   * conta própria.
   */
  public Resultado processar(WebhookPagamento w, String corpoCru) {
    if (w.processado) return Resultado.DUPLICADO;
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
    var a = assinaturas.lock(oficina).orElseThrow(ApiException::missing);
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
    marcar(w, true, tratado ? null : "Tipo sem tratamento nesta versão.");
    return tratado ? Resultado.PROCESSADO : Resultado.IGNORADO;
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
        a.inadimplenteDesde = a.inadimplenteDesde == null ? clock.instant() : a.inadimplenteDesde;
        a.status = StatusAssinatura.INADIMPLENTE;
        service.aplicarTolerancia(a);
        yield true;
      }
      case "assinatura.cancelada" -> {
        a.status = StatusAssinatura.CANCELADA;
        a.canceladaEm = a.canceladaEm == null ? clock.instant() : a.canceladaEm;
        a.cancelamentoEfetivoEm =
            a.cancelamentoEfetivoEm == null ? clock.instant() : a.cancelamentoEfetivoEm;
        a.cancelamentoMotivo =
            a.cancelamentoMotivo == null ? "Cancelada pelo provedor." : a.cancelamentoMotivo;
        a.atualizadoEm = clock.instant();
        cobranca.registrar(
            a,
            TipoEventoCobranca.SUBSCRIPTION_CANCELED,
            "Cancelamento informado pelo provedor. Dados preservados.",
            null,
            null);
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
