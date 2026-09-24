package br.com.garagem.integration.revenda.preparacao;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import org.junit.jupiter.api.Test;

class PreparacaoIT extends RevendaIntegrationBase {
  @Test
  void preparacaoComCustosSemAcessoAOficina() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, false);
    String path = "/revenda/estoque/" + s.path("id").asText();
    ok(
        e,
        "POST",
        path + "/preparacao/oficina",
        Map.of("revisao", 0, "km", 10000, "relato", "Preparar"),
        404);
    ok(e, "POST", path + "/preparacao/concluir", Map.of("revisao", 0), 404);
    s =
        ok(
            e,
            "POST",
            path + "/custos",
            Map.of(
                "revisao",
                s.path("revisao").asLong(),
                "descricao",
                "Revisao externa",
                "categoria",
                "MECANICA",
                "valor",
                600.25,
                "data",
                java.time.LocalDate.now().toString()),
            201);
    assertThat(s.path("custosPreparacao").decimalValue()).isEqualByComparingTo("600.25");
    ok(
        e,
        "PUT",
        path + "/status",
        Map.of("revisao", s.path("revisao").asLong(), "status", "DISPONIVEL"),
        200);
  }
}
