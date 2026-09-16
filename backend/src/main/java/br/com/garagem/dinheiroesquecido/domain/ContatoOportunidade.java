package br.com.garagem.dinheiroesquecido.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "contato_oportunidade")
public class ContatoOportunidade extends TenantEntity {
  public UUID oportunidadeId;
  public UUID usuarioId;

  @Enumerated(EnumType.STRING)
  public CanalContato canal;

  @Enumerated(EnumType.STRING)
  public ResultadoContato resultado;

  public String observacao;
  public Instant proximoContatoEm;
}
