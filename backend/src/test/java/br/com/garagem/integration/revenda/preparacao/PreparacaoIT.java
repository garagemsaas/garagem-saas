package br.com.garagem.integration.revenda.preparacao;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import org.junit.jupiter.api.Test;

class PreparacaoIT extends RevendaIntegrationBase {
  @Test
  void osInternaSemClienteFicticioCustoImportadoUmaVez() throws Exception {
    var e = empresa("OFICINA", "REVENDA");
    var s = estoque(e, false);
    String path = "/revenda/estoque/" + s.path("id").asText();
    s =
        ok(
            e,
            "POST",
            path + "/preparacao/oficina",
            Map.of("revisao", 0, "km", 10000, "relato", "Revisão para revenda"),
            201);
    String os = "/ordens-servico/" + s.path("ordemServicoId").asText();
    var o = ok(e, "GET", os, null, 200);
    assertThat(o.path("clienteId").isNull()).isTrue();
    assertThat(o.path("tipo").asText()).isEqualTo("INTERNA");
    ok(e, "GET", "/ordens-servico?veiculoId=" + s.path("veiculoId").asText(), null, 200);
    ok(
        e,
        "PUT",
        path + "/status",
        Map.of("revisao", s.path("revisao").asLong(), "status", "DISPONIVEL"),
        409);
    ok(e, "POST", os + "/links", Map.of(), 409);
    for (String status : List.of("DIAGNOSTICO", "ORCAMENTO")) {
      ok(
          e,
          "POST",
          os + "/status",
          Map.of("revisao", o.path("revisao").asLong(), "status", status),
          200);
      o = ok(e, "GET", os, null, 200);
    }
    ok(
        e,
        "POST",
        os + "/orcamento/versoes",
        Map.of(
            "itens",
            List.of(
                Map.of(
                    "tipo",
                    "SERVICO",
                    "descricao",
                    "Revisão",
                    "quantidade",
                    "1.000",
                    "valorUnitario",
                    "600.25"))),
        201);
    o = ok(e, "GET", os, null, 200);
    ok(
        e,
        "POST",
        os + "/status",
        Map.of("revisao", o.path("revisao").asLong(), "status", "AGUARDANDO_APROVACAO"),
        409);
    for (String status : List.of("EM_MANUTENCAO", "TESTE", "PRONTO")) {
      ok(
          e,
          "POST",
          os + "/status",
          Map.of("revisao", o.path("revisao").asLong(), "status", status),
          200);
      o = ok(e, "GET", os, null, 200);
    }
    s =
        ok(
            e,
            "POST",
            path + "/preparacao/concluir",
            Map.of("revisao", s.path("revisao").asLong()),
            200);
    assertThat(s.path("custosPreparacao").decimalValue()).isEqualByComparingTo("600.25");
    s =
        ok(
            e,
            "POST",
            path + "/preparacao/concluir",
            Map.of("revisao", s.path("revisao").asLong()),
            200);
    assertThat(s.path("custosPreparacao").decimalValue()).isEqualByComparingTo("600.25");
    ok(
        e,
        "PUT",
        path + "/status",
        Map.of("revisao", s.path("revisao").asLong(), "status", "DISPONIVEL"),
        200);
  }
}
