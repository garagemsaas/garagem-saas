package br.com.garagem.ordemservico.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ordem_servico")
public class OrdemServico extends TenantEntity {
  @Version public long revisao;
  public long numero;
  public UUID veiculoId;
  public UUID clienteId;
  public UUID mecanicoId;

  @Enumerated(EnumType.STRING)
  public StatusOs status = StatusOs.RECEBIDO;

  public long kmEntrada;
  public String relato;
  public Instant previsaoEntrega;
  public Instant concluidaEm;
  public java.time.LocalDate proximaRevisaoEm;
}
