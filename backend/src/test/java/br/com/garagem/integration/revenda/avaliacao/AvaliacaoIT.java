package br.com.garagem.integration.revenda.avaliacao;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import org.junit.jupiter.api.Test;

class AvaliacaoIT extends RevendaIntegrationBase {
  @Test
  void aceiteAdquireUmaVezEHistoricoPreservaDono() throws Exception {
    var e = empresa("REVENDA");
    var c = cliente(e);
    var v = veiculo(e, c);
    var a = avaliacao(e, v, c);
    String id = a.path("id").asText();
    var aceite =
        Map.of(
            "revisao",
            0,
            "responsavelId",
            e.usuario(),
            "precoAnunciado",
            "32000.00",
            "precoMinimo",
            "28000.00");
    var s = ok(e, "POST", "/revenda/avaliacoes/" + id + "/aceite", aceite, 200);
    assertThat(s.path("valorAquisicao").decimalValue()).isEqualByComparingTo("25000.00");
    ok(e, "POST", "/revenda/avaliacoes/" + id + "/aceite", aceite, 409);
    assertThat(
            ok(
                    e,
                    "GET",
                    "/revenda/avaliacoes?status=ACEITA&veiculoId=" + v.path("id").asText(),
                    null,
                    200)
                .path("total")
                .asInt())
        .isEqualTo(1);
    var hist =
        ok(e, "GET", "/veiculos/" + v.path("id").asText() + "/propriedade/historico", null, 200);
    assertThat(hist.path("total").asInt()).isEqualTo(2);
  }

  @Test
  void recusaEExpiracaoImpedemAquisicao() throws Exception {
    var e = empresa("REVENDA");
    var v = veiculo(e, null);
    var a = avaliacao(e, v, null);
    String id = a.path("id").asText();
    ok(
        e,
        "PUT",
        "/revenda/avaliacoes/" + id + "/status",
        Map.of("revisao", 0, "status", "EXPIRADA"),
        409);
    ok(
        e,
        "PUT",
        "/revenda/avaliacoes/" + id + "/status",
        Map.of("revisao", 0, "status", "RECUSADA"),
        200);
    ok(
        e,
        "POST",
        "/revenda/avaliacoes/" + id + "/aceite",
        Map.of(
            "revisao",
            1,
            "responsavelId",
            e.usuario(),
            "precoAnunciado",
            "30000.00",
            "precoMinimo",
            "0"),
        409);
  }
}
