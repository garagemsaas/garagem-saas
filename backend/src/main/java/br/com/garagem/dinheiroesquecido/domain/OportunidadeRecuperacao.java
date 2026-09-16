package br.com.garagem.dinheiroesquecido.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "oportunidade_recuperacao")
public class OportunidadeRecuperacao extends TenantEntity {
  @Version public long revisao;

  @Enumerated(EnumType.STRING)
  public TipoOportunidade tipo;

  @Enumerated(EnumType.STRING)
  public StatusOportunidade status = StatusOportunidade.ABERTA;

  public UUID ordemServicoId;
  public UUID orcamentoVersaoId;
  public UUID clienteId;
  public UUID veiculoId;
  public UUID responsavelId;

  @Column(precision = 19, scale = 2)
  public BigDecimal valorPotencial;

  public Instant elegivelDesde;
  public Instant ultimoContatoEm;
  public Instant proximoContatoEm;
  public Instant encerradaEm;
}
