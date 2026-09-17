package br.com.garagem.assinatura.domain;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;

public enum Periodicidade {
  MENSAL(Period.ofMonths(1)),
  ANUAL(Period.ofYears(1));

  private final Period periodo;

  Periodicidade(Period periodo) {
    this.periodo = periodo;
  }

  /** Avança um ciclo preservando o dia do mês sempre que o mês de destino o tiver. */
  public Instant proximo(Instant de) {
    return de.atZone(ZoneOffset.UTC).plus(periodo).toInstant();
  }
}
