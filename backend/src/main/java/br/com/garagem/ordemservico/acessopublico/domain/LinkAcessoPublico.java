package br.com.garagem.ordemservico.acessopublico.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "link_acesso_publico")
public class LinkAcessoPublico extends TenantEntity {
  public UUID ordemServicoId;
  public String tokenHash;
  public Instant expiraEm;
  public Instant revogadoEm;
}
