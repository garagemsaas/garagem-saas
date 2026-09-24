package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PolimentoIT extends RevendaIntegrationBase {
  @Autowired PasswordEncoder passwords;

  String plataforma(String papel) throws Exception {
    var id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into plataforma_usuario(id,nome,email,senha_hash,papel) values(?,?,?,?,?)",
        id,
        "Equipe",
        email,
        passwords.encode("SenhaSegura123!"),
        papel);
    return chamada(
            null,
            post("/api/v1/plataforma/auth/login"),
            Map.of("email", email, "senha", "SenhaSegura123!"),
            200)
        .path("accessToken")
        .asText();
  }

  JsonNode chamada(String token, MockHttpServletRequestBuilder request, Object body, int status)
      throws Exception {
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    var result = mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse();
    return result.getContentAsByteArray().length == 0
        ? json.nullNode()
        : json.readTree(result.getContentAsByteArray());
  }

  Map<String, Object> novaEmpresa() {
    return Map.of(
        "slug",
        "empresa-" + UUID.randomUUID(),
        "nome",
        "Empresa real",
        "operacao",
        "OFICINA",
        "proprietario",
        "Responsavel",
        "email",
        "responsavel@example.test",
        "senha",
        "SenhaSegura123!");
  }

  @Test
  void cicloEmpresaPermissoesERevogacaoImediata() throws Exception {
    String admin = plataforma("ADMIN_PLATAFORMA"), dev = plataforma("DESENVOLVEDOR");
    var entrada = novaEmpresa();
    var criada = chamada(admin, post("/api/v1/plataforma/empresas"), entrada, 201);
    String id = criada.at("/empresa/id").asText(), path = "/api/v1/plataforma/empresas/" + id;
    var login =
        chamada(
            null,
            post("/api/v1/auth/login"),
            Map.of(
                "oficina",
                entrada.get("slug"),
                "email",
                entrada.get("email"),
                "senha",
                entrada.get("senha")),
            200);
    String owner = login.path("accessToken").asText();
    chamada(owner, get("/api/v1/plataforma/empresas"), null, 403);
    chamada(owner, put(path), Map.of(), 403);
    chamada(dev, get("/api/v1/clientes"), null, 404);
    chamada(
        admin, put(path + "/identidade"), Map.of("nomeExibicao", "Nome novo", "revisao", 0), 403);
    chamada(dev, put(path + "/identidade"), Map.of("nomeExibicao", "Nome novo", "revisao", 0), 200);
    assertThat(
            chamada(owner, get("/api/v1/empresa"), null, 200).at("/branding/nomeExibicao").asText())
        .isEqualTo("Nome novo");
    for (String status : List.of("SUSPENSA", "INATIVA", "ATIVA")) {
      long revisao = chamada(admin, get(path), null, 200).at("/empresa/revisao").asLong();
      chamada(
          admin,
          put(path),
          Map.of(
              "nome",
              "Empresa real",
              "operacao",
              "REVENDA",
              "status",
              status,
              "revisao",
              revisao,
              "motivo",
              "Revisao administrativa"),
          200);
      chamada(owner, get("/api/v1/empresa"), null, 401);
    }
    String novaSessao =
        chamada(
                null,
                post("/api/v1/auth/login"),
                Map.of(
                    "oficina",
                    entrada.get("slug"),
                    "email",
                    entrada.get("email"),
                    "senha",
                    entrada.get("senha")),
                200)
            .path("accessToken")
            .asText();
    chamada(novaSessao, get("/api/v1/ordens-servico"), null, 404);
    chamada(novaSessao, get("/api/v1/revenda/estoque"), null, 200);
    assertThat(chamada(admin, get(path + "/auditoria"), null, 200)).hasSize(5);
    chamada(admin, post("/api/v1/plataforma/auth/logout"), null, 204);
    chamada(admin, get(path), null, 401);
  }

  @Test
  void retornosPersistemValidamTenantRevisaoEEncerramento() throws Exception {
    var a = empresa("OFICINA");
    var b = empresa("REVENDA");
    var c = cliente(a);
    var cb = cliente(b);
    var body =
        new HashMap<String, Object>(
            Map.of(
                "clienteId",
                c.path("id").asText(),
                "responsavelId",
                a.usuario(),
                "motivo",
                "Confirmar agendamento",
                "agendadoEm",
                "2026-10-01T15:00:00Z",
                "prioridade",
                "ALTA",
                "revisao",
                0));
    var retorno = ok(a, "POST", "/retornos", body, 201);
    String path = "/retornos/" + retorno.path("id").asText();
    assertThat(ok(a, "GET", "/retornos?meus=true&busca=Confirmar", null, 200).path("total").asInt())
        .isEqualTo(1);
    ok(b, "GET", path, null, 404);
    ok(b, "PUT", path, body, 404);
    body.put("clienteId", cb.path("id").asText());
    ok(a, "POST", "/retornos", body, 404);
    body.put("clienteId", c.path("id").asText());
    body.put("agendadoEm", "2026-10-02T15:00:00Z");
    ok(a, "PUT", path, body, 200);
    ok(a, "PUT", path, body, 409);
    ok(
        a,
        "PUT",
        path + "/situacao",
        Map.of("revisao", 1, "status", "CONCLUIDO", "resultado", "Cliente confirmou"),
        200);
    ok(
        a,
        "PUT",
        path + "/situacao",
        Map.of("revisao", 2, "status", "CANCELADO", "resultado", "Duplicado"),
        409);
    assertThat(ok(a, "GET", path + "/historico", null, 200)).hasSize(3);
    assertThat(ok(a, "GET", "/retornos", null, 200).path("total").asInt()).isZero();
    assertThat(ok(a, "GET", "/retornos?status=CONCLUIDO", null, 200).path("total").asInt())
        .isEqualTo(1);
    jdbc.update("update empresa_modulo set modulo='REVENDA' where oficina_id=?", a.id());
    ok(a, "GET", path, null, 404);
    assertThat(ok(a, "GET", "/retornos?status=TODOS", null, 200).path("total").asInt()).isZero();
  }

  @Test
  void sugestoesNaoDuplicamContatoEEncerradosNaoReaparecem() throws Exception {
    var a = empresa("REVENDA");
    var c = cliente(a);
    var lead =
        ok(
            a,
            "POST",
            "/revenda/leads",
            Map.of(
                "clienteId",
                c.path("id").asText(),
                "vendedorId",
                a.usuario(),
                "origem",
                "TELEFONE"),
            201);
    UUID id = UUID.fromString(lead.path("id").asText());
    // Eventos recentes protegem de falsos atrasos; só o cadastro antigo não basta.
    jdbc.update("update revenda_lead set criado_em=now()-interval '8 days' where id=?", id);
    assertThat(ok(a, "POST", "/retornos/sugestoes", null, 200).path("criados").asInt()).isZero();
    var oficina = empresa("OFICINA");
    assertThat(ok(oficina, "POST", "/retornos/sugestoes", null, 200).path("criados").asInt())
        .isZero();
  }

  @Test
  void usuarioEditadoRevogaSessaoSemPermitirAutoElevacao() throws Exception {
    var a = empresa("OFICINA");
    var b = empresa("OFICINA");
    ok(
        a,
        "PUT",
        "/usuarios/" + b.usuario(),
        Map.of("nome", "Intruso", "email", "x@example.test", "papel", "OWNER"),
        404);
    ok(
        a,
        "PUT",
        "/usuarios/" + a.usuario(),
        Map.of("nome", "Owner", "email", "owner@test.local", "papel", "ATENDENTE"),
        409);
    ok(
        a,
        "PUT",
        "/usuarios/" + a.usuario(),
        Map.of(
            "nome",
            "Owner novo",
            "email",
            "owner@test.local",
            "papel",
            "OWNER",
            "senha",
            "NovaSenhaSegura123!"),
        200);
    ok(a, "GET", "/usuarios", null, 401);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from usuario_auditoria where oficina_id=?", Integer.class, a.id()))
        .isEqualTo(1);
  }

  @Test
  void siteEEdicaoDeIdentidadeAntigosNaoExistem() throws Exception {
    var a = empresa("OFICINA");
    ok(a, "GET", "/site/empresa", null, 404);
    ok(a, "PUT", "/empresa/site", Map.of(), 404);
    ok(a, "PUT", "/empresa", Map.of("nomeExibicao", "Intruso", "revisao", 0), 405);
    assertThat(ok(a, "GET", "/empresa", null, 200).has("site")).isFalse();
  }
}
