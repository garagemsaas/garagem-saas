package br.com.garagem.assinatura.domain;

/**
 * Ciclo de vida comercial da oficina. Equivalência com a nomenclatura usual de gateways: TRIAL,
 * ATIVA=ACTIVE, INADIMPLENTE=PAST_DUE, SUSPENSA=SUSPENDED, CANCELADA=CANCELED.
 *
 * <p>Nada aqui se confunde com {@code oficina.situacao}, que é o ciclo de vida da conta e continua
 * governando o login. Inadimplência jamais tira o acesso de leitura.
 */
public enum StatusAssinatura {
  TRIAL,
  ATIVA,
  INADIMPLENTE,
  SUSPENSA,
  CANCELADA;

  /**
   * Períodos em que a oficina pode consumir mais recursos do que já consome.
   *
   * <p>INADIMPLENTE entra aqui de propósito: é a tolerância entre a falha de pagamento e a
   * suspensão. Cortar a operação já no atraso esvaziaria a tolerância, que existe justamente para a
   * oficina se regularizar sem parar de trabalhar. Quem bloqueia é SUSPENSA.
   */
  public boolean permiteCrescer() {
    return this == TRIAL || this == ATIVA || this == INADIMPLENTE;
  }

  public boolean encerrada() {
    return this == CANCELADA;
  }
}
