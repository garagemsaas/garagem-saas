package br.com.garagem.integration.revenda.venda;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import org.junit.jupiter.api.Test;

class VendaIT extends RevendaIntegrationBase {
  @Test
  void vendaComTrocaTransfereAmbosEPreservaCustos() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, true);
    var comprador = cliente(e);
    var troca = veiculo(e, comprador);
    var a = avaliacao(e, troca, comprador);
    var body = venda(e, s, comprador);
    body.put("avaliacaoTrocaId", a.path("id").asText());
    body.put("valorTroca", "25000.00");
    var venda = ok(e, "POST", "/revenda/vendas", body, 201);
    assertThat(venda.path("margemBruta").decimalValue()).isEqualByComparingTo("10000.00");
    var vendido = ok(e, "GET", "/veiculos/" + s.path("veiculoId").asText(), null, 200);
    assertThat(vendido.path("clienteId").asText()).isEqualTo(comprador.path("id").asText());
    assertThat(
            ok(e, "GET", "/veiculos/" + troca.path("id").asText(), null, 200)
                .path("propriedade")
                .asText())
        .isEqualTo("EMPRESA");
    var recebido =
        ok(e, "GET", "/revenda/estoque/" + venda.path("estoqueTrocaId").asText(), null, 200);
    assertThat(recebido.path("status").asText()).isEqualTo("EM_PREPARACAO");
    assertThat(recebido.path("origem").asText()).isEqualTo("TROCA");
    ok(e, "POST", "/revenda/vendas", body, 409);
    assertThat(
            ok(e, "GET", "/revenda/vendas?clienteId=" + comprador.path("id").asText(), null, 200)
                .path("total")
                .asInt())
        .isEqualTo(1);
    assertThat(ok(e, "GET", "/revenda/dashboard", null, 200).path("vendasPeriodo").asInt())
        .isEqualTo(1);
  }

  @Test
  void reservaDeOutroClienteBloqueiaEVendaComTrocaInvalidaNaoDeixaMetade() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, true);
    var c = cliente(e);
    var outro = cliente(e);
    ok(e, "POST", "/revenda/reservas", reserva(e, s, outro), 201);
    s = ok(e, "GET", "/revenda/estoque/" + s.path("id").asText(), null, 200);
    ok(e, "POST", "/revenda/vendas", venda(e, s, c), 409);
    var troca = veiculo(e, c);
    var a = avaliacao(e, troca, c);
    var body = venda(e, s, outro);
    body.put("avaliacaoTrocaId", a.path("id").asText());
    body.put("valorTroca", "25000.00");
    ok(e, "POST", "/revenda/vendas", body, 409);
    assertThat(
            ok(e, "GET", "/revenda/reservas?estoqueId=" + s.path("id").asText(), null, 200)
                .path("itens")
                .get(0)
                .path("status")
                .asText())
        .isEqualTo("ATIVA");
    assertThat(
            ok(e, "GET", "/veiculos/" + troca.path("id").asText(), null, 200)
                .path("clienteId")
                .asText())
        .isEqualTo(c.path("id").asText());
    assertThat(ok(e, "GET", "/revenda/vendas", null, 200).path("total").asInt()).isZero();
  }
}
