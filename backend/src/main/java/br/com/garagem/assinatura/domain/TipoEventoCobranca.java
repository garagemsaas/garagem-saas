package br.com.garagem.assinatura.domain;

/** Vocabulário fechado da auditoria financeira; o frontend e o suporte decidem por estes nomes. */
public enum TipoEventoCobranca {
  SUBSCRIPTION_CREATED,
  SUBSCRIPTION_ACTIVATED,
  SUBSCRIPTION_CHANGED,
  SUBSCRIPTION_CANCELED,
  SUBSCRIPTION_REACTIVATED,
  PAYMENT_APPROVED,
  PAYMENT_FAILED,
  ACCOUNT_PAST_DUE,
  ACCOUNT_SUSPENDED,
  ACCOUNT_REACTIVATED,
  WEBHOOK_RECEIVED,
  WEBHOOK_PROCESSED,
  WEBHOOK_FAILED,
  PLAN_LIMIT_REACHED
}
