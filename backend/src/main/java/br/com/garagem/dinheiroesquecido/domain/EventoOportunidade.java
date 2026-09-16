package br.com.garagem.dinheiroesquecido.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "evento_oportunidade")
public class EventoOportunidade extends TenantEntity {
  public UUID oportunidadeId;
  public UUID usuarioId;
  public String tipo;
  public String anterior;
  public String novo;
  public String observacao;
}
