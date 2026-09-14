package br.com.garagem.ordemservico.domain;

import java.util.Set;

public enum StatusOs {
  RECEBIDO,
  DIAGNOSTICO,
  ORCAMENTO,
  AGUARDANDO_APROVACAO,
  EM_MANUTENCAO,
  AGUARDANDO_PECA,
  TESTE,
  PRONTO;

  public boolean permite(StatusOs destino) {
    return switch (this) {
      case RECEBIDO -> destino == DIAGNOSTICO;
      case DIAGNOSTICO -> destino == ORCAMENTO;
      case ORCAMENTO -> destino == AGUARDANDO_APROVACAO;
      case AGUARDANDO_APROVACAO -> Set.of(ORCAMENTO, EM_MANUTENCAO).contains(destino);
      case EM_MANUTENCAO -> Set.of(AGUARDANDO_PECA, TESTE).contains(destino);
      case AGUARDANDO_PECA -> destino == EM_MANUTENCAO;
      case TESTE -> destino == PRONTO;
      case PRONTO -> false;
    };
  }
}
