package br.com.garagem.dinheiroesquecido.domain;

import br.com.garagem.shared.persistence.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "acompanhamento_orcamento")
public class AcompanhamentoOrcamento extends TenantEntity {
  @Version public long revisao;
  public UUID orcamentoVersaoId;
  public Instant publicadoEm;
  public LocalDate reavaliarEm;
}
