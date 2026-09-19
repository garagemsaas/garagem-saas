package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.domain.*;
import br.com.garagem.assinatura.repository.*;
import br.com.garagem.tenancy.TenantContext;
import java.time.Clock;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auditoria financeira. Único lugar que grava {@link EventoCobranca}, para que nenhum caminho de
 * cobrança fique sem rastro e para que a higiene do conteúdo seja garantida em um ponto só.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.legacy-billing.enabled",
    havingValue = "true")
@Service
@Transactional
public class CobrancaService {
  /**
   * Chaves que jamais entram no log, em qualquer capitalização. Um metadado que bata com uma delas
   * é descartado inteiro: é preferível perder diagnóstico a gravar segredo.
   */
  private static final Set<String> PROIBIDAS =
      Set.of(
          "senha",
          "password",
          "token",
          "secret",
          "segredo",
          "authorization",
          "cartao",
          "card",
          "cardnumber",
          "cvv",
          "cvc",
          "apikey",
          "api_key",
          "private_key",
          "chave_privada",
          "signature");

  private final EventoCobrancaRepository eventos;
  private final Clock clock;

  public CobrancaService(EventoCobrancaRepository eventos, Clock clock) {
    this.eventos = eventos;
    this.clock = clock;
  }

  public EventoCobranca registrar(
      Assinatura assinatura,
      TipoEventoCobranca tipo,
      String mensagem,
      UUID usuarioId,
      Map<String, ?> metadados) {
    return registrar(assinatura, tipo, mensagem, usuarioId, metadados, null);
  }

  /**
   * Grava um fato financeiro. Todos os campos são definidos <b>antes</b> de salvar: a tabela é
   * imutável por gatilho, então qualquer alteração depois do insert seria recusada pelo banco — e,
   * enquanto {@code save()} caía em {@code merge()}, era pior ainda: silenciosamente perdida.
   */
  public EventoCobranca registrar(
      Assinatura assinatura,
      TipoEventoCobranca tipo,
      String mensagem,
      UUID usuarioId,
      Map<String, ?> metadados,
      String eventoExternoId) {
    var e = new EventoCobranca();
    e.criadoEm = clock.instant();
    e.assinaturaId = assinatura == null ? null : assinatura.id;
    e.usuarioId = usuarioId;
    e.tipo = tipo;
    e.provedor = assinatura == null ? null : assinatura.provedor;
    e.providerEventId = eventoExternoId;
    e.mensagem = mensagem;
    e.metadados = higienizar(metadados);
    eventos.save(e);
    LoggerFactory.getLogger(CobrancaService.class)
        .atInfo()
        .addKeyValue("evento_cobranca", tipo.name())
        .addKeyValue("assinatura_id", e.assinaturaId)
        .log("cobranca_registrada");
    return e;
  }

  public EventoCobranca registrarWebhook(
      Assinatura assinatura, TipoEventoCobranca tipo, String eventoExternoId, String mensagem) {
    return registrar(assinatura, tipo, mensagem, null, null, eventoExternoId);
  }

  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<EventoCobranca> listar(
      org.springframework.data.domain.Pageable pageable) {
    return eventos.findByOficinaIdOrderByCriadoEmDescIdDesc(TenantContext.current(), pageable);
  }

  /**
   * Serializa metadados como {@code chave=valor} legível, recusando chaves sensíveis e cortando
   * valores longos. Não usa JSON para não convidar a despejar payload de gateway inteiro aqui.
   */
  public static String higienizar(Map<String, ?> metadados) {
    if (metadados == null || metadados.isEmpty()) return null;
    var partes = new ArrayList<String>();
    for (var entrada : new TreeMap<>(metadados).entrySet()) {
      String chave = entrada.getKey();
      if (chave == null || entrada.getValue() == null) continue;
      String normalizada = chave.toLowerCase(Locale.ROOT).replace("-", "_");
      if (PROIBIDAS.stream().anyMatch(normalizada::contains)) continue;
      String valor = String.valueOf(entrada.getValue());
      if (valor.length() > 200) valor = valor.substring(0, 200) + "…";
      partes.add(chave + "=" + valor);
    }
    if (partes.isEmpty()) return null;
    String texto = String.join("; ", partes);
    return texto.length() > 4000 ? texto.substring(0, 4000) : texto;
  }
}
