package br.com.garagem.assinatura.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * Trilha financeira imutável, separada da timeline da OS. Metadados são texto curto de diagnóstico:
 * nunca recebem segredo, token, cartão ou credencial (ver {@code CobrancaService#registrar}).
 */
@Entity
@Table(name = "evento_cobranca")
public class EventoCobranca extends TenantEntity {
  public UUID assinaturaId;
  public UUID usuarioId;

  @Enumerated(EnumType.STRING)
  public TipoEventoCobranca tipo;

  public String provedor;
  public String providerEventId;
  public String mensagem;
  public String metadados;
}
