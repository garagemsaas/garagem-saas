package br.com.garagem.ordemservico.checklist.item.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "checklist_item")
public class ChecklistItem extends TenantEntity {
  public UUID checklistEntradaId;
  public String descricao;
  public String condicao;
  public String observacao;
}
