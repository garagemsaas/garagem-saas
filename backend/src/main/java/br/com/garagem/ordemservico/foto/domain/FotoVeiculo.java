package br.com.garagem.ordemservico.foto.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "foto_veiculo")
public class FotoVeiculo extends TenantEntity {
  public UUID ordemServicoId;
  public UUID checklistItemId;
  public UUID diagnosticoItemId;
  public UUID autorId;
  public String objeto;
  public String contentType;
  public String finalidade;
  public String descricao;
  public long tamanho;
}
