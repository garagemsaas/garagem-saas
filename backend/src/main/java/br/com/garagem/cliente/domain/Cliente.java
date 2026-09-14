package br.com.garagem.cliente.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "cliente")
public class Cliente extends TenantEntity {
  @Version public long revisao;
  public String nome;
  public String telefone;
  public String email;
}
