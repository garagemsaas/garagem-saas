package br.com.garagem.ordemservico.checklist.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "checklist_entrada")
public class ChecklistEntrada extends TenantEntity {
  public UUID ordemServicoId;
  public UUID autorId;
  public String observacoes;
}
