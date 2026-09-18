package br.com.garagem.shared.persistence;

import br.com.garagem.tenancy.TenantContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;

/**
 * Base de toda entidade de oficina: id próprio, tenant carimbado e instante de criação.
 *
 * <p>Implementa {@link Persistable} porque o id é atribuído na construção. Sem isso o Spring Data
 * decide "é nova?" por id nulo, conclui que toda entidade já existe e chama {@code merge()} em vez
 * de {@code persist()}. Duas consequências reais, medidas neste projeto:
 *
 * <ul>
 *   <li>um {@code SELECT} desnecessário antes de cada {@code INSERT};
 *   <li>o argumento de {@code save()} continua <em>detached</em> — quem alterasse o objeto depois
 *       de salvar perdia a alteração em silêncio. Foi assim que {@code provider_event_id} nunca
 *       chegou ao banco na trilha de cobrança.
 * </ul>
 *
 * <p>{@code @Version} continua valendo: o controle otimista é do banco e independe desta decisão.
 * Não há {@code equals}/{@code hashCode} customizados aqui, então nada muda para coleções.
 */
@MappedSuperclass
public abstract class TenantEntity implements Persistable<UUID> {
  @Id public UUID id = UUID.randomUUID();

  @TenantId
  @Column(nullable = false, updatable = false)
  public UUID oficinaId;

  @Column(nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  /**
   * Marcado como já persistido depois que o JPA carrega ou grava a linha. {@code @Transient} porque
   * é estado do ciclo de vida em memória, não coluna.
   */
  @Transient private boolean persistida;

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return !persistida;
  }

  /** Após carregar do banco a entidade existe, ainda que nada tenha sido gravado nesta sessão. */
  @PostLoad
  @PostPersist
  void marcarPersistida() {
    persistida = true;
  }

  @PrePersist
  void requireTenant() {
    UUID current = TenantContext.current();
    if (oficinaId != null && !oficinaId.equals(current))
      throw new IllegalStateException("Oficina inválida");
    oficinaId = current;
  }
}
