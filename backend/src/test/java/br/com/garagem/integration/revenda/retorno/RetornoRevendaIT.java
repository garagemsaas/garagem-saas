package br.com.garagem.integration.revenda.retorno;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Retornos é o mesmo nome de tela nos dois módulos, com implementações separadas. O que se protege
 * aqui é a fronteira: quem só tem oficina não alcança os retornos da revenda, e vice-versa. Sem
 * isso, unificar o nome teria custado o gating de módulo.
 */
class RetornoRevendaIT extends RevendaIntegrationBase {
  @Test
  void moduloDecideQuemEnxergaCadaTipoDeRetorno() throws Exception {
    var revenda = empresa("REVENDA");
    var oficina = empresa("OFICINA");
    var hibrida = empresa("OFICINA", "REVENDA");

    ok(revenda, "GET", "/revenda/retornos", null, 200);
    ok(hibrida, "GET", "/revenda/retornos", null, 200);
    // Empresa sem o módulo não descobre nem que o recurso existe.
    ok(oficina, "GET", "/revenda/retornos", null, 404);

    // E o caminho contrário continua valendo: revenda não alcança o retorno da oficina.
    ok(oficina, "GET", "/dinheiro-esquecido/resumo", null, 200);
    ok(revenda, "GET", "/dinheiro-esquecido/resumo", null, 404);
    ok(hibrida, "GET", "/dinheiro-esquecido/resumo", null, 200);

    ok(null, "GET", "/revenda/retornos", null, 401);
  }

  @Test
  void listaTrazQuemEsperaRespostaESomenteDaPropriaEmpresa() throws Exception {
    var a = empresa("REVENDA");
    var b = empresa("REVENDA");
    var cliente = cliente(a);
    var estoque = estoque(a, true);

    // Recém-criados não são retorno: ninguém liga para quem procurou a loja hoje de manhã.
    ok(
        a,
        "POST",
        "/revenda/leads",
        Map.of(
            "clienteId", cliente.path("id").asText(), "vendedorId", a.usuario(), "origem", "SITE"),
        201);
    assertThat(ok(a, "GET", "/revenda/retornos", null, 200)).isEmpty();

    // Envelhecer o registro é o que o transforma em motivo de ligação.
    jdbc.update(
        "update revenda_lead set criado_em=now()-interval '20 days' where oficina_id=?", a.id());
    var retornos = ok(a, "GET", "/revenda/retornos", null, 200);
    assertThat(retornos).hasSize(1);
    assertThat(retornos.get(0).path("tipo").asText()).isEqualTo("INTERESSADO_SEM_RETORNO");
    assertThat(retornos.get(0).path("cliente").asText()).isEqualTo(cliente.path("nome").asText());
    assertThat(retornos.get(0).path("telefone").asText()).isNotBlank();

    // Uma reserva cancelada sobre um carro ainda não vendido também pede retorno.
    var reserva = ok(a, "POST", "/revenda/reservas", reserva(a, estoque, cliente), 201);
    ok(
        a,
        "POST",
        "/revenda/reservas/" + reserva.path("id").asText() + "/cancelamento",
        Map.of("revisao", reserva.path("revisao").asLong()),
        200);
    assertThat(ok(a, "GET", "/revenda/retornos", null, 200)).hasSize(2);

    // A empresa vizinha não enxerga nada disso.
    assertThat(ok(b, "GET", "/revenda/retornos", null, 200)).isEmpty();
  }
}
