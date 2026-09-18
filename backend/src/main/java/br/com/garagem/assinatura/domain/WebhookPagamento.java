package br.com.garagem.assinatura.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Recebimento bruto do gateway. Fora do escopo de tenant de propósito: o evento chega antes de
 * sabermos a qual oficina pertence, e a unicidade de {@code providerEventId} é global.
 */
@Entity
@Table(name = "webhook_pagamento")
public class WebhookPagamento implements Persistable<UUID> {
  @Id public UUID id = UUID.randomUUID();

  /** Mesmo motivo de TenantEntity: id atribuído na construção faria o save virar merge. */
  @Transient private boolean persistida;

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return !persistida;
  }

  @PostLoad
  @PostPersist
  void marcarPersistida() {
    persistida = true;
  }

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
