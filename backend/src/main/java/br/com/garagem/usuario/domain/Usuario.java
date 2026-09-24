package br.com.garagem.usuario.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class Usuario extends TenantEntity {
  public String nome;
  public String email;
  public String senhaHash;

  @Enumerated(EnumType.STRING)
  public Papel papel;

  public boolean ativo = true;
  public long versaoSessao;
}
