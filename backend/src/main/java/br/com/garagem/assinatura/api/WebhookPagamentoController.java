package br.com.garagem.assinatura.api;

import br.com.garagem.assinatura.api.AssinaturaDtos.WebhookSaida;
import br.com.garagem.assinatura.application.WebhookPagamentoService;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.tenancy.SemModulo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Entrada de eventos do gateway. Não é autenticada por JWT — o gateway não tem sessão — e por isso
 * a autenticidade vem inteiramente da assinatura criptográfica do corpo.
 *
 * <p>Responde 200 para evento duplicado ou de tipo desconhecido: reentregar é o comportamento
 * normal de um gateway, e devolver erro faria o provedor repetir indefinidamente um evento que já
 * está resolvido.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.legacy-billing.enabled",
    havingValue = "true")
@RestController
@SemModulo
@RequestMapping("/api/v1/webhooks/pagamento")
@Tag(
    name = "Webhooks de pagamento",
    description =
        "Recebimento assinado de eventos do gateway. Idempotente por provider_event_id; nenhuma"
            + " confirmação de pagamento entra na aplicação por outro caminho.")
public class WebhookPagamentoController {
  private final WebhookPagamentoService service;

  public WebhookPagamentoController(WebhookPagamentoService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Receber evento de pagamento",
      description =
          "Exige X-Garagem-Signature: HMAC-SHA256 hexadecimal do corpo cru com"
              + " PAYMENT_WEBHOOK_SECRET. Sem segredo configurado, todo evento é recusado."
              + " Repetir o mesmo id devolve 200 sem reprocessar.")
  public WebhookSaida receber(@RequestBody String corpoCru, HttpServletRequest requisicao) {
    var cabecalhos = new HashMap<String, String>();
    for (var nomes = requisicao.getHeaderNames(); nomes.hasMoreElements(); ) {
      String nome = nomes.nextElement();
      cabecalhos.put(nome.toLowerCase(Locale.ROOT), requisicao.getHeader(nome));
    }
    if (!service.origemValida(corpoCru, cabecalhos)) {
      // Sem detalhar o motivo: quem não tem o segredo não descobre daqui se errou a chave ou o
      // corpo.
      LoggerFactory.getLogger(WebhookPagamentoController.class)
          .atWarn()
          .addKeyValue("webhook_rejeitado", "ASSINATURA_INVALIDA")
          .log("webhook_pagamento_recusado");
      throw ApiException.unauthorized();
    }
    var recebido = service.registrarRecebimento(corpoCru);
    var resultado = service.processar(recebido, corpoCru);
    return new WebhookSaida(resultado.name(), recebido.providerEventId);
  }
}
