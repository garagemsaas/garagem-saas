package br.com.garagem.integration.revenda.lead;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * O pipeline é deliberadamente pequeno, mas não é uma etiqueta: há transições que não existem.
 *
 * <p>Os dois estados de fim, {@code PROPOSTA} e {@code VENDIDO}, não são escolhidos à mão — quem os
 * define é o fato comercial correspondente. Deixar um vendedor marcar "vendido" sem venda
 * registrada produziria um funil que fecha melhor que o caixa, que é justamente o número em que
 * ninguém deveria poder mexer.
 */
class LeadIT extends RevendaIntegrationBase {
  @Test
  void pipelineAvancaPorEtapasValidasEFechamentoSoVemDoFatoComercial() throws Exception {
    var e = empresa("REVENDA");
    var cliente = cliente(e);
    var lead =
        ok(
            e,
            "POST",
            "/revenda/leads",
            Map.of(
                "clienteId",
                cliente.path("id").asText(),
                "vendedorId",
                e.usuario(),
                "origem",
                "WHATSAPP",
                "observacoes",
                "Procura hatch 2021"),
            201);
    String id = lead.path("id").asText();
    assertThat(lead.path("status").asText()).isEqualTo("NOVO");
    assertThat(lead.path("clienteNome").asText()).isEqualTo(cliente.path("nome").asText());

    // Pular etapa não é permitido: de NOVO não se salta para negociação.
    ok(e, "PUT", "/revenda/leads/" + id + "/status", transicao(lead, "NEGOCIACAO"), 409);
    // Nem se declara fechamento à mão.
    ok(e, "PUT", "/revenda/leads/" + id + "/status", transicao(lead, "VENDIDO"), 409);

    var contato = avancar(e, id, lead, "CONTATO_REALIZADO");
    var interessado = avancar(e, id, contato, "INTERESSADO");
    var negociacao = avancar(e, id, interessado, "NEGOCIACAO");

    // Quem cria a proposta é que leva o lead a PROPOSTA — não o vendedor, digitando.
    var estoque = estoque(e, true);
    ok(
        e,
        "POST",
        "/revenda/propostas",
        Map.of(
            "estoqueId", estoque.path("id").asText(),
            "clienteId", cliente.path("id").asText(),
            "vendedorId", e.usuario(),
            "leadId", id,
            "termos",
                Map.of(
                    "valorNegociado", "60000.00",
                    "entrada", "5000.00",
                    "valorTroca", "0.00",
                    "validade", java.time.Instant.now().plusSeconds(86400).toString())),
        201);
    assertThat(ok(e, "GET", "/revenda/leads/" + id, null, 200).path("status").asText())
        .isEqualTo("PROPOSTA");

    // A revisão antiga já não serve: escrita concorrente sobre leitura velha é recusada.
    ok(e, "PUT", "/revenda/leads/" + id + "/status", transicao(negociacao, "PERDIDO"), 409);
    var atual = ok(e, "GET", "/revenda/leads/" + id, null, 200);
    assertThat(
            ok(e, "PUT", "/revenda/leads/" + id + "/status", transicao(atual, "PERDIDO"), 200)
                .path("status")
                .asText())
        .isEqualTo("PERDIDO");
    // Encerrado não volta atrás.
    var perdido = ok(e, "GET", "/revenda/leads/" + id, null, 200);
    ok(e, "PUT", "/revenda/leads/" + id + "/status", transicao(perdido, "INTERESSADO"), 409);
  }

  @Test
  void leadDeOutraEmpresaNaoExisteMesmoComOIdentificadorEmMaos() throws Exception {
    var a = empresa("REVENDA");
    var b = empresa("REVENDA");
    var lead =
        ok(
            a,
            "POST",
            "/revenda/leads",
            Map.of(
                "clienteId", cliente(a).path("id").asText(),
                "vendedorId", a.usuario(),
                "origem", "SITE"),
            201);
    String id = lead.path("id").asText();
    ok(b, "GET", "/revenda/leads/" + id, null, 404);
    ok(b, "PUT", "/revenda/leads/" + id + "/status", transicao(lead, "CONTATO_REALIZADO"), 404);
    assertThat(ok(a, "GET", "/revenda/leads/" + id, null, 200).path("status").asText())
        .isEqualTo("NOVO");
    // A listagem de B também não o alcança por outro caminho.
    assertThat(ok(b, "GET", "/revenda/leads", null, 200).path("total").asInt()).isZero();
  }

  private com.fasterxml.jackson.databind.JsonNode avancar(
      Empresa e, String id, com.fasterxml.jackson.databind.JsonNode atual, String status)
      throws Exception {
    var resposta = ok(e, "PUT", "/revenda/leads/" + id + "/status", transicao(atual, status), 200);
    assertThat(resposta.path("status").asText()).isEqualTo(status);
    return resposta;
  }

  private static Map<String, Object> transicao(
      com.fasterxml.jackson.databind.JsonNode atual, String status) {
    return Map.of("revisao", atual.path("revisao").asLong(), "status", status);
  }
}
