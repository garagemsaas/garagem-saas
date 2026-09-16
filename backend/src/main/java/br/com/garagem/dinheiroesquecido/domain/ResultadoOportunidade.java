package br.com.garagem.dinheiroesquecido.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "resultado_oportunidade")
public class ResultadoOportunidade extends TenantEntity {
  public UUID oportunidadeId;
  public UUID usuarioId;
  public UUID ordemServicoId;

  @Column(precision = 19, scale = 2)
  public BigDecimal valorRecuperado;

  public String observacao;
}
