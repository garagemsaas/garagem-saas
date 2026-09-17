package br.com.garagem.assinatura.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Catálogo da plataforma, não da oficina: não estende {@link
 * br.com.garagem.shared.persistence.TenantEntity} porque o mesmo plano vale para todos os tenants.
 *
 * <p>Todo limite do produto mora aqui. Nenhum número de plano deve aparecer como constante no
 * código: alterar um limite é alterar esta linha.
 */
@Entity
@Table(name = "plano")
public class Plano {
  @Id public UUID id = UUID.randomUUID();

  @Version public long revisao;

  public Instant criadoEm = Instant.now();
  public String codigo;
  public String nome;
  public String descricao;
  public long valorCentavos;
  public String moeda = "BRL";

  @Enumerated(EnumType.STRING)
  public Periodicidade periodicidade = Periodicidade.MENSAL;

  public int maxUsuarios;
  public long maxArmazenamentoBytes;

  /** Nulo significa ilimitado, e é lido como ilimitado em todo o serviço de limites. */
  public Integer maxOrdensServicoMes;

  public Integer maxVeiculos;

  public boolean ativo = true;
  public int ordem;

  /** Identificador do preço no gateway. Nulo enquanto nenhum gateway estiver configurado. */
  public String providerPriceId;
}
