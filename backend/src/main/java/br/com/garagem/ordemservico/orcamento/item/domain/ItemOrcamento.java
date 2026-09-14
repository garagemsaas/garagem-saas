package br.com.garagem.ordemservico.orcamento.item.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "item_orcamento")
public class ItemOrcamento extends TenantEntity {
  public UUID orcamentoVersaoId;
  public String tipo;
  public String descricao;
  public java.math.BigDecimal quantidade;
  public java.math.BigDecimal valorUnitario;
}
