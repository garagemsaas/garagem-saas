package br.com.garagem.ordemservico.acessopublico.aprovacao.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "aprovacao_orcamento")
public class AprovacaoOrcamento extends TenantEntity {
  public UUID orcamentoVersaoId;
  public UUID linkAcessoPublicoId;
  public boolean aprovado;
  public String canal = "LINK_PUBLICO";
  public String ipOrigem;
}
