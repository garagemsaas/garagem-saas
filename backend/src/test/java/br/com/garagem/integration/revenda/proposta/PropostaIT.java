package br.com.garagem.integration.revenda.proposta;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PropostaIT extends RevendaIntegrationBase {
  @Test
  void versoesImutaveisLeadEPropostaAceitaNaVenda() throws Exception {
    var e = empresa("REVENDA");
    var s = estoque(e, true);
    var c = cliente(e);
    var lead =
        ok(
            e,
            "POST",
            "/revenda/leads",
            Map.of(
                "clienteId",
                c.path("id").asText(),
                "veiculoId",
                s.path("veiculoId").asText(),
                "vendedorId",
                e.usuario(),
                "origem",
                "WHATSAPP"),
            201);
    var termos =
        new HashMap<String, Object>(
            Map.of(
                "valorNegociado",
                "61000.00",
                "entrada",
                "1000.00",
                "valorTroca",
                "0.00",
                "validade",
                Instant.now().plusSeconds(3600).toString()));
    var p =
        ok(
            e,
            "POST",
            "/revenda/propostas",
            Map.of(
                "estoqueId",
                s.path("id").asText(),
                "clienteId",
                c.path("id").asText(),
                "vendedorId",
                e.usuario(),
                "leadId",
                lead.path("id").asText(),
                "termos",
                termos),
            201);
    String path = "/revenda/propostas/" + p.path("id").asText();
    p = ok(e, "PUT", path + "/status", Map.of("revisao", 0, "status", "ENVIADA"), 200);
    termos.put("valorNegociado", "60000.00");
    p =
        ok(
            e,
            "POST",
            path + "/versoes",
            Map.of("revisao", p.path("revisao").asLong(), "termos", termos),
            201);
    assertThat(p.path("numeroVersao").asInt()).isEqualTo(2);
    var versoes = ok(e, "GET", path + "/versoes", null, 200);
    assertThat(versoes.path("itens").get(1).path("valorNegociado").decimalValue())
        .isEqualByComparingTo("61000.00");
    p =
        ok(
            e,
            "PUT",
            path + "/status",
            Map.of("revisao", p.path("revisao").asLong(), "status", "ENVIADA"),
            200);
    p =
        ok(
            e,
            "PUT",
            path + "/status",
            Map.of("revisao", p.path("revisao").asLong(), "status", "ACEITA"),
            200);
    var venda = venda(e, s, c);
    venda.put("propostaId", p.path("id").asText());
    venda.put("propostaVersaoId", p.path("versaoId").asText());
    ok(e, "POST", "/revenda/vendas", venda, 201);
    assertThat(
            ok(e, "GET", "/revenda/leads/" + lead.path("id").asText(), null, 200)
                .path("status")
                .asText())
        .isEqualTo("VENDIDO");
    ok(
        e,
        "POST",
        path + "/versoes",
        Map.of("revisao", p.path("revisao").asLong(), "termos", termos),
        409);
    UUID propostaId = UUID.fromString(p.path("id").asText());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update revenda_proposta_versao set valor_negociado=1 where proposta_id=?",
                    propostaId))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
