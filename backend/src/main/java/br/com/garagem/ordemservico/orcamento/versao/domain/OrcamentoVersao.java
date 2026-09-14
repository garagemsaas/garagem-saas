package br.com.garagem.ordemservico.orcamento.versao.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "orcamento_versao")
public class OrcamentoVersao extends TenantEntity {
  public UUID orcamentoId;
  public int numero;
  public UUID autorId;
  public String observacoes;
  public java.math.BigDecimal total;
}
