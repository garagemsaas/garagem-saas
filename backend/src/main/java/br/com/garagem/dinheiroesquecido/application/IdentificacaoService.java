package br.com.garagem.dinheiroesquecido.application;

import br.com.garagem.dinheiroesquecido.api.RecuperacaoDtos.IdentificacaoSaida;
import br.com.garagem.dinheiroesquecido.repository.ConsultaRecuperacao;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Varredura explícita, paginada por chave, transações pequenas e repetição idempotente. */
@Service
public class IdentificacaoService {
  private final ConsultaRecuperacao consultas;
  private final RecuperacaoService service;
  private final Clock clock;

  public IdentificacaoService(
      ConsultaRecuperacao consultas, RecuperacaoService service, Clock clock) {
    this.consultas = consultas;
    this.service = service;
    this.clock = clock;
  }

  public IdentificacaoSaida identificar() {
    var agora = clock.instant();
    UUID cursor = new UUID(0, 0);
    long criadas = 0, descartadas = 0;
    while (true) {
      var ids = consultas.proximasOrigens(cursor, agora);
      if (ids.isEmpty()) break;
      for (UUID id : ids) {
        var r = service.reconciliar(id, agora);
        criadas += r.criadas();
        descartadas += r.descartadas();
      }
      cursor = ids.getLast();
    }
    return new IdentificacaoSaida(criadas, descartadas);
  }
}
