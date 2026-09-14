package br.com.garagem.ordemservico.timeline.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "evento_ordem_servico")
public class EventoOrdemServico extends TenantEntity {
  public UUID ordemServicoId;
  public UUID autorId;
  public String origem;
  public String tipo;
  public String descricao;
}
