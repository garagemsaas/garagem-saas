package br.com.garagem.integration.revenda.retorno;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import org.junit.jupiter.api.Test;

class RetornoRevendaIT extends RevendaIntegrationBase {
  @Test
  void origemComercialGeraAgendaSemDuplicacaoNemVazamento() throws Exception {
    var a = empresa("REVENDA");
    var b = empresa("REVENDA");
    var c = cliente(a);
    UUID lead = UUID.randomUUID();
    jdbc.update(
        "insert into revenda_lead(id,oficina_id,cliente_id,vendedor_id,origem,criado_em) values(?,?,?,?,'TELEFONE',now()-interval '8 days')",
        lead,
        a.id(),
        UUID.fromString(c.path("id").asText()),
        a.usuario());
    assertThat(ok(a, "POST", "/retornos/sugestoes", null, 200).path("criados").asInt())
        .isEqualTo(1);
    assertThat(ok(a, "POST", "/retornos/sugestoes", null, 200).path("criados").asInt()).isZero();
    assertThat(ok(b, "GET", "/retornos", null, 200).path("total").asInt()).isZero();
    var retorno = ok(a, "GET", "/retornos", null, 200).path("itens").get(0);
    assertThat(retorno.path("clienteId").asText()).isEqualTo(c.path("id").asText());
    ok(
        a,
        "PUT",
        "/retornos/" + retorno.path("id").asText() + "/situacao",
        Map.of("revisao", 0, "status", "CANCELADO", "resultado", "Cliente pediu encerramento"),
        200);
    assertThat(ok(a, "POST", "/retornos/sugestoes", null, 200).path("criados").asInt()).isZero();
    assertThat(ok(a, "GET", "/retornos", null, 200).path("total").asInt()).isZero();
  }

  @Test
  void listaAntigaFoiRemovidaEOficinaNaoImportaInteressesDaRevenda() throws Exception {
    var revenda = empresa("REVENDA");
    var oficina = empresa("OFICINA");
    ok(revenda, "GET", "/revenda/retornos", null, 404);
    ok(oficina, "GET", "/revenda/retornos", null, 404);
    ok(revenda, "GET", "/dinheiro-esquecido/resumo", null, 404);
    assertThat(ok(oficina, "POST", "/retornos/sugestoes", null, 200).path("criados").asInt())
        .isZero();
    ok(null, "GET", "/retornos", null, 401);
  }
}
