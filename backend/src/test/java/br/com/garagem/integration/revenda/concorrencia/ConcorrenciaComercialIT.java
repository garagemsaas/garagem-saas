package br.com.garagem.integration.revenda.concorrencia;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ConcorrenciaComercialIT extends RevendaIntegrationBase {
  @Test
  void dezReservasExatamenteUmaVence() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, true);
    var c = cliente(e);
    var p = reserva(e, s, c);
    disputar(e, Collections.nCopies(10, "/revenda/reservas"), Collections.nCopies(10, p));
    assertThat(ok(e, "GET", "/revenda/reservas", null, 200).path("total").asInt()).isEqualTo(1);
  }

  @Test
  void dezVendasExatamenteUmaVence() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, true);
    var c = cliente(e);
    var p = venda(e, s, c);
    disputar(e, Collections.nCopies(10, "/revenda/vendas"), Collections.nCopies(10, p));
    assertThat(ok(e, "GET", "/revenda/vendas", null, 200).path("total").asInt()).isEqualTo(1);
  }

  @Test
  void reservaContraVendaEstadoFinalConsistente() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, true);
    var c = cliente(e);
    disputar(
        e,
        List.of("/revenda/reservas", "/revenda/vendas"),
        List.of(reserva(e, s, c), venda(e, s, c)));
    var estado =
        ok(e, "GET", "/revenda/estoque/" + s.path("id").asText(), null, 200)
            .path("status")
            .asText();
    int reservas = ok(e, "GET", "/revenda/reservas", null, 200).path("total").asInt();
    int vendas = ok(e, "GET", "/revenda/vendas", null, 200).path("total").asInt();
    assertThat(reservas + vendas).isEqualTo(1);
    assertThat(estado).isEqualTo(vendas == 1 ? "VENDIDO" : "RESERVADO");
  }

  private void disputar(Empresa e, List<String> paths, List<? extends Map<String, Object>> payloads)
      throws Exception {
    int n = paths.size();
    var ready = new CountDownLatch(n);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(n)) {
      var futures = new ArrayList<Future<Integer>>();
      for (int i = 0; i < n; i++) {
        final int k = i;
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(20, TimeUnit.SECONDS))
                    throw new AssertionError("Barreira não liberada");
                  return req(e, "POST", paths.get(k), payloads.get(k))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var statuses = new ArrayList<Integer>();
      for (var future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));
      assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
      assertThat(statuses).filteredOn(s -> s == 409).hasSize(n - 1);
    }
  }
}
