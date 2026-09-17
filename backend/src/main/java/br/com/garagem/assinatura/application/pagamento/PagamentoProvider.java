package br.com.garagem.assinatura.application.pagamento;

import br.com.garagem.assinatura.domain.Assinatura;
import br.com.garagem.assinatura.domain.Plano;

/**
 * Fronteira com o gateway. Todo código específico de provedor vive atrás desta interface: o resto
 * da aplicação conhece assinatura e plano, nunca o vocabulário de um fornecedor.
 *
 * <p>Nenhum método aqui decide status: quem confirma pagamento é o webhook, e o retorno destas
 * chamadas é apenas o identificador remoto a ser guardado.
 */
public interface PagamentoProvider {
  /** Nome curto gravado em {@code assinatura.provedor} e no log de cobrança. */
  String nome();

  /** Identificador do cliente no gateway, criado uma vez por oficina. */
  String criarCliente(Assinatura assinatura, String nomeOficina, String emailResponsavel);

  /** Identificador da assinatura remota. Nulo quando o provedor não tem esse conceito. */
  String criarAssinatura(Assinatura assinatura, Plano plano);

  void cancelarAssinatura(Assinatura assinatura, boolean imediato);

  void reativarAssinatura(Assinatura assinatura);

  void mudarPlano(Assinatura assinatura, Plano destino);

  /** Situação remota, para conciliação manual. Vazio quando o provedor não souber responder. */
  java.util.Optional<String> consultarAssinatura(Assinatura assinatura);

  /**
   * Confere a autenticidade do recebimento. Recebe o corpo cru, porque qualquer reserialização
   * invalidaria a assinatura HMAC.
   */
  boolean assinaturaValida(String corpoCru, java.util.Map<String, String> cabecalhos);

  /** Extrai do corpo o identificador único e o tipo do evento, sem interpretar o negócio. */
  EventoExterno lerEvento(String corpoCru);

  record EventoExterno(String id, String tipo, String providerSubscriptionId) {}
}
