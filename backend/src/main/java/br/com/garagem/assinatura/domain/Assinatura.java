package br.com.garagem.assinatura.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A assinatura é da oficina, nunca de um usuário: uma linha por tenant, garantida por unique. */
@Entity
@Table(name = "assinatura")
public class Assinatura extends TenantEntity {
  @Version public long revisao;

  public UUID planoId;

  @Enumerated(EnumType.STRING)
  public StatusAssinatura status = StatusAssinatura.TRIAL;

  public String provedor = "MANUAL";
  public String providerCustomerId;
  public String providerSubscriptionId;

  public Instant periodoInicio;
  public Instant periodoFim;
  public Instant trialInicio;
  public Instant trialFim;

  public Instant inadimplenteDesde;
  public Instant suspensaEm;
  public Instant canceladaEm;

  /** Data em que o acesso termina de fato; num cancelamento agendado é o fim do período pago. */
  public Instant cancelamentoEfetivoEm;

  public String cancelamentoMotivo;
  public UUID canceladaPor;
  public Instant atualizadoEm = Instant.now();
}
