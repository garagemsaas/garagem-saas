package br.com.garagem.integration.revenda.seguranca;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import org.junit.jupiter.api.Test;

class RevendaSegurancaIT extends RevendaIntegrationBase {
  @Test
  void modulosInatividadeEPapeisProtegemTodosDominios() throws Exception {
    var oficina = empresa("OFICINA");
    var revenda = empresa("REVENDA");
    var outraRevenda = empresa("REVENDA");
    for (String path :
        List.of(
            "/revenda/estoque",
            "/revenda/avaliacoes",
            "/revenda/leads",
            "/revenda/propostas",
            "/revenda/reservas",
            "/revenda/vendas",
            "/revenda/dashboard")) {
      ok(oficina, "GET", path, null, 404);
      ok(revenda, "GET", path, null, 200);
      ok(outraRevenda, "GET", path, null, 200);
    }
    ok(revenda, "GET", "/ordens-servico", null, 404);
    var s = estoque(revenda, false);
    ok(
        revenda,
        "POST",
        "/revenda/estoque/" + s.path("id").asText() + "/preparacao/oficina",
        Map.of("revisao", 0, "km", 10000, "relato", "Preparar"),
        404);
    jdbc.update("update usuario set papel='MECANICO' where id=?", revenda.usuario());
    // Existing JWT's role no longer matches the active user: rejected before authorization.
    ok(revenda, "GET", "/revenda/estoque", null, 401);
    jdbc.update("update oficina set situacao='INATIVA' where id=?", outraRevenda.id());
    for (String path :
        List.of(
            "/revenda/estoque",
            "/revenda/avaliacoes",
            "/revenda/leads",
            "/revenda/propostas",
            "/revenda/reservas",
            "/revenda/vendas",
            "/revenda/dashboard")) ok(outraRevenda, "GET", path, null, 401);
  }

  @Test
  void tenantDoPayloadIgnoradoERelacoesExternasSempre404() throws Exception {
    var a = empresa("REVENDA");
    var b = empresa("REVENDA");
    var stock = estoque(a, true);
    var clienteB = cliente(b);
    var va = veiculo(a, cliente(a));
    var av = avaliacao(a, va, clientePorVeiculo(a, va));
    for (String path :
        List.of(
            "/revenda/estoque/" + stock.path("id").asText(),
            "/revenda/avaliacoes/" + av.path("id").asText(),
            "/revenda/historico/ESTOQUE/" + stock.path("id").asText(),
            "/revenda/fotos/ESTOQUE/" + stock.path("id").asText())) ok(b, "GET", path, null, 404);
    var body = venda(a, stock, clienteB);
    body.put("oficinaId", b.id());
    ok(a, "POST", "/revenda/vendas", body, 404);
    var lead =
        new HashMap<String, Object>(
            Map.of(
                "clienteId",
                cliente(a).path("id").asText(),
                "vendedorId",
                a.usuario(),
                "origem",
                "SITE",
                "oficinaId",
                b.id()));
    var l = ok(a, "POST", "/revenda/leads", lead, 201);
    ok(b, "GET", "/revenda/leads/" + l.path("id").asText(), null, 404);
    assertThat(ok(b, "GET", "/revenda/estoque", null, 200).path("total").asInt()).isZero();
  }

  private com.fasterxml.jackson.databind.JsonNode clientePorVeiculo(
      Empresa e, com.fasterxml.jackson.databind.JsonNode v) throws Exception {
    return ok(e, "GET", "/clientes/" + v.path("clienteId").asText(), null, 200);
  }
}
