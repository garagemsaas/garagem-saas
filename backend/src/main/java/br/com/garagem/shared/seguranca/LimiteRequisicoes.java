package br.com.garagem.shared.seguranca;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Janela deslizante em memória, por chave. Conta tentativas dentro de um intervalo e recusa a
 * partir do teto.
 *
 * <p>Em memória de propósito: o projeto é um monólito de instância única e não tem Redis. A
 * consequência precisa ficar explícita — com duas instâncias atrás de um balanceador, cada uma
 * conta separadamente e o teto efetivo dobra. Continua sendo muito melhor do que teto nenhum, mas
 * escalar horizontalmente exige trocar esta classe por um contador compartilhado.
 */
public final class LimiteRequisicoes {
  /** Janela fechada de contagem, reiniciada quando expira. */
  private static final class Janela {
    final long inicioNanos;
    final AtomicInteger tentativas = new AtomicInteger();

    Janela(long agora) {
      this.inicioNanos = agora;
    }
  }

  private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
  private final int teto;
  private final long janelaNanos;

  public LimiteRequisicoes(int teto, Duration janela) {
    if (teto < 1) throw new IllegalArgumentException("Teto deve ser positivo");
    this.teto = teto;
    this.janelaNanos = janela.toNanos();
  }

  /**
   * Registra uma tentativa e diz se ela cabe no teto. Só conta quando cabe: um cliente já barrado
   * não empurra a janela para frente a cada nova tentativa, o que faria o bloqueio virar permanente
   * sob ataque contínuo.
   */
  public boolean permitir(String chave) {
    long agora = System.nanoTime();
    var janela =
        janelas.compute(
            chave,
            (k, atual) ->
                atual == null || agora - atual.inicioNanos >= janelaNanos
                    ? new Janela(agora)
                    : atual);
    return janela.tentativas.incrementAndGet() <= teto;
  }

  /** Segundos até a janela da chave expirar, para o cabeçalho Retry-After. */
  public long esperaSegundos(String chave) {
    var janela = janelas.get(chave);
    if (janela == null) return 0;
    long restante = janelaNanos - (System.nanoTime() - janela.inicioNanos);
    return restante <= 0 ? 0 : Math.max(1, Duration.ofNanos(restante).toSeconds());
  }

  /** Zera a contagem após um sucesso, para que quem acertou a senha não carregue as falhas. */
  public void liberar(String chave) {
    janelas.remove(chave);
  }

  /**
   * Descarta janelas expiradas. Sem isto, um ataque com IP rotativo faria o mapa crescer sem limite
   * — a defesa viraria o vetor de exaustão de memória.
   */
  public int limpar() {
    long agora = System.nanoTime();
    janelas.entrySet().removeIf(e -> agora - e.getValue().inicioNanos >= janelaNanos);
    return janelas.size();
  }

  public int monitoradas() {
    return janelas.size();
  }
}
