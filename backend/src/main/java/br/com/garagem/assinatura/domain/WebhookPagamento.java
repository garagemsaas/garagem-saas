package br.com.garagem.assinatura.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Recebimento bruto do gateway. Fora do escopo de tenant de propósito: o evento chega antes de
 * sabermos a qual oficina pertence, e a unicidade de {@code providerEventId} é global.
 */
@Entity
@Table(name = "webhook_pagamento")
public class WebhookPagamento {
  @Id public UUID id = UUID.randomUUID();

  public Instant criadoEm = Instant.now();
  public String provedor;
  public String providerEventId;
  public String tipo;
  public String payload;
  public UUID assinaturaId;
  public UUID oficinaId;
  public boolean processado;
  public Instant processadoEm;
  public String erro;
}
