package br.com.garagem.veiculo.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "veiculo")
public class Veiculo extends TenantEntity {
  @Version public long revisao;
  public UUID clienteId;
  public String placa;
  public String marca;
  public String modelo;
  public int ano;
  public long km;
  public String cor;
}
