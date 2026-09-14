package br.com.garagem.ordemservico.orcamento.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "orcamento")
public class Orcamento extends TenantEntity {
  public UUID ordemServicoId;
}
