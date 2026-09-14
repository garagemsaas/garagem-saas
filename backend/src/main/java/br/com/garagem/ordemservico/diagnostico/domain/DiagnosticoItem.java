package br.com.garagem.ordemservico.diagnostico.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "diagnostico_item")
public class DiagnosticoItem extends TenantEntity {
  public UUID ordemServicoId;
  public UUID autorId;
  public String descricao;

  @Enumerated(EnumType.STRING)
  public Classificacao classificacao;
}
