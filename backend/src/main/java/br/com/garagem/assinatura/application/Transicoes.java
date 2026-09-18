package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.domain.Assinatura;
import br.com.garagem.assinatura.domain.StatusAssinatura;
import java.time.Instant;

/**
 * Transições de status da assinatura, com os carimbos que o banco exige.
 *
 * <p>A V4 impõe invariantes bicondicionais, entre eles {@code (status='SUSPENSA') = (suspensa_em is
 * not null)}. Quando cada serviço trocava o status na mão, era fácil esquecer de limpar o carimbo
 * anterior: cancelar uma assinatura SUSPENSA deixava {@code suspensa_em} preenchido e o banco
 * rejeitava a operação com um erro de violação de unicidade traduzido como "Dados duplicados" — a
 * oficina inadimplente ficava impedida de cancelar, com uma mensagem que não explicava nada.
 *
 * <p>Concentrar as transições aqui torna o invariante consequência da própria troca de estado, em
 * vez de disciplina de quem escreve o serviço.
 */
final class Transicoes {
  private Transicoes() {}

  /** Ativa: nada de inadimplência ou suspensão sobra para trás. */
  static void paraAtiva(Assinatura a, Instant agora) {
    a.status = StatusAssinatura.ATIVA;
    a.inadimplenteDesde = null;
    a.suspensaEm = null;
    a.atualizadoEm = agora;
  }

  /** Entra em atraso preservando o início, para a tolerância continuar contando do mesmo ponto. */
  static void paraInadimplente(Assinatura a, Instant agora) {
    a.status = StatusAssinatura.INADIMPLENTE;
    if (a.inadimplenteDesde == null) a.inadimplenteDesde = agora;
    a.suspensaEm = null;
    a.atualizadoEm = agora;
  }

  /** Suspensa exige o carimbo, e ele exige inadimplência registrada antes. */
  static void paraSuspensa(Assinatura a, Instant agora) {
    if (a.inadimplenteDesde == null) a.inadimplenteDesde = agora;
    a.status = StatusAssinatura.SUSPENSA;
    a.suspensaEm = agora;
    a.atualizadoEm = agora;
  }

  /**
   * Cancelada. Limpa o carimbo de suspensão — é exatamente a transição que o banco recusava — e
   * garante que a data efetiva exista, porque {@code cancelada_em} e {@code
   * cancelamento_efetivo_em} precisam ser ambos nulos ou ambos preenchidos.
   */
  static void paraCancelada(Assinatura a, Instant agora) {
    a.status = StatusAssinatura.CANCELADA;
    a.suspensaEm = null;
    if (a.canceladaEm == null) a.canceladaEm = agora;
    if (a.cancelamentoEfetivoEm == null) a.cancelamentoEfetivoEm = agora;
    a.atualizadoEm = agora;
  }

  /** Marca o cancelamento sem encerrar agora: o acesso segue até a data efetiva. */
  static void agendarCancelamento(Assinatura a, Instant quando, Instant agora) {
    a.canceladaEm = agora;
    a.cancelamentoEfetivoEm = quando;
    a.atualizadoEm = agora;
  }

  /** Revoga o cancelamento, deixando os dois carimbos nulos como o banco exige. */
  static void revogarCancelamento(Assinatura a, Instant agora) {
    a.canceladaEm = null;
    a.cancelamentoEfetivoEm = null;
    a.cancelamentoMotivo = null;
    a.canceladaPor = null;
    a.atualizadoEm = agora;
  }
}
