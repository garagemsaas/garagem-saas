package br.com.garagem.integration.revenda.reserva;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * O ciclo de vida da reserva fora da disputa — a concorrência tem suíte própria.
 *
 * <p>O que se protege aqui é a coerência entre a reserva e o estoque: um veículo reservado não pode
 * seguir anunciado como disponível, e um cancelamento que não o devolva ao pátio prende mercadoria
 * sem ninguém perceber. São dois registros diferentes que precisam contar a mesma história.
 */
class ReservaIT extends RevendaIntegrationBase {
  @Test
  void reservaOcupaOVeiculoECancelamentoODevolveAoPatio() throws Exception {
    var e = empresa("REVENDA");
    var estoque = estoque(e, true);
    var cliente = cliente(e);
    String estoqueId = estoque.path("id").asText();

    var reserva = ok(e, "POST", "/revenda/reservas", reserva(e, estoque, cliente), 201);
    assertThat(reserva.path("status").asText()).isEqualTo("ATIVA");
    assertThat(ok(e, "GET", "/revenda/estoque/" + estoqueId, null, 200).path("status").asText())
        .isEqualTo("RESERVADO");

    // Reservado não é reservável de novo, nem para o mesmo cliente.
    var segunda = ok(e, "GET", "/revenda/estoque/" + estoqueId, null, 200);
    ok(e, "POST", "/revenda/reservas", reserva(e, segunda, cliente), 409);

    String reservaId = reserva.path("id").asText();
    var cancelada =
        ok(
            e,
            "POST",
            "/revenda/reservas/" + reservaId + "/cancelamento",
            Map.of("revisao", reserva.path("revisao").asLong()),
            200);
    assertThat(cancelada.path("status").asText()).isEqualTo("CANCELADA");
    assertThat(ok(e, "GET", "/revenda/estoque/" + estoqueId, null, 200).path("status").asText())
        .isEqualTo("DISPONIVEL");

    // Encerrada é encerrada: cancelar de novo não reabre nem mexe no estoque.
    ok(
        e,
        "POST",
        "/revenda/reservas/" + reservaId + "/cancelamento",
        Map.of("revisao", cancelada.path("revisao").asLong()),
        409);

    // E o veículo volta a ser reservável por outra pessoa, que é o ponto de devolver ao pátio.
    var terceira = ok(e, "GET", "/revenda/estoque/" + estoqueId, null, 200);
    var outro = cliente(e);
    assertThat(
            ok(e, "POST", "/revenda/reservas", reserva(e, terceira, outro), 201)
                .path("status")
                .asText())
        .isEqualTo("ATIVA");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from revenda_reserva where oficina_id=? and estoque_id=? and status='ATIVA'",
                Integer.class,
                e.id(),
                UUID.fromString(estoqueId)))
        .isEqualTo(1);
  }

  @Test
  void reservaExigeVeiculoDisponivelDaPropriaEmpresa() throws Exception {
    var a = empresa("REVENDA");
    var b = empresa("REVENDA");
    var emPreparacao = estoque(a, false);
    var clienteA = cliente(a);

    // Ainda em avaliação: não há o que reservar.
    ok(a, "POST", "/revenda/reservas", reserva(a, emPreparacao, clienteA), 409);

    // Conhecer o identificador de outra empresa não dá acesso: o recurso não existe para B.
    var disponivel = estoque(a, true);
    var clienteB = cliente(b);
    var corpo =
        new java.util.HashMap<String, Object>(
            Map.of(
                "estoqueId", disponivel.path("id").asText(),
                "clienteId", clienteB.path("id").asText(),
                "vendedorId", b.usuario(),
                "revisaoEstoque", disponivel.path("revisao").asLong(),
                "validade", java.time.Instant.now().plusSeconds(3600).toString()));
    ok(b, "POST", "/revenda/reservas", corpo, 404);
    assertThat(
            ok(a, "GET", "/revenda/estoque/" + disponivel.path("id").asText(), null, 200)
                .path("status")
                .asText())
        .isEqualTo("DISPONIVEL");
  }
}
