package br.com.garagem.integration.revenda.estoque;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class EstoqueIT extends RevendaIntegrationBase {
  @Test
  void aquisicaoCustosMargemPropriedadeEFiltros() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, false);
    String id = s.path("id").asText();
    assertThat(s.path("margemPrevista").decimalValue()).isEqualByComparingTo("15000");
    var v = ok(e, "GET", "/veiculos/" + s.path("veiculoId").asText(), null, 200);
    assertThat(v.path("propriedade").asText()).isEqualTo("EMPRESA");
    assertThat(v.path("clienteId").isNull()).isTrue();
    var historico =
        ok(e, "GET", "/veiculos/" + v.path("id").asText() + "/propriedade/historico", null, 200);
    assertThat(historico.path("total").asInt()).isEqualTo(2);
    s =
        ok(
            e,
            "POST",
            "/revenda/estoque/" + id + "/custos",
            Map.of(
                "revisao",
                0,
                "descricao",
                "Higienização",
                "categoria",
                "LIMPEZA",
                "valor",
                "250.25",
                "data",
                LocalDate.now().toString()),
            201);
    assertThat(s.path("custoTotal").decimalValue()).isEqualByComparingTo("50250.25");
    assertThat(s.path("margemPrevista").decimalValue()).isEqualByComparingTo("14749.75");
    ok(
        e,
        "PUT",
        "/revenda/estoque/" + id + "/status",
        Map.of("revisao", 0, "status", "DISPONIVEL"),
        409);
    ok(
        e,
        "PUT",
        "/revenda/estoque/" + id + "/status",
        Map.of("revisao", s.path("revisao").asLong(), "status", "DISPONIVEL"),
        200);
    assertThat(
            ok(
                    e,
                    "GET",
                    "/revenda/estoque?status=DISPONIVEL&marca=Toyota&precoDe=60000&tamanho=1",
                    null,
                    200)
                .path("total")
                .asInt())
        .isEqualTo(1);
    assertThat(ok(e, "GET", "/revenda/estoque?modelo=Inexistente", null, 200).path("total").asInt())
        .isZero();
    assertThat(ok(e, "GET", "/revenda/estoque?tamanho=101", null, 200).path("tamanho").asInt())
        .isEqualTo(100);
    ok(e, "GET", "/revenda/dashboard", null, 200);
  }
}
