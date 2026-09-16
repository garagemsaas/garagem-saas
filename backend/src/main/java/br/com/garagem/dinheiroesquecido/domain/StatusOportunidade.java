package br.com.garagem.dinheiroesquecido.domain;

public enum StatusOportunidade {
  ABERTA,
  EM_CONTATO,
  AGENDADA,
  RECUPERADA,
  PERDIDA,
  DESCARTADA;

  public boolean encerrada() {
    return this == RECUPERADA || this == PERDIDA || this == DESCARTADA;
  }
}
