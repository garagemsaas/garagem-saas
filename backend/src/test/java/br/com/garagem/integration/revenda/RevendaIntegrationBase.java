package br.com.garagem.integration.revenda;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import br.com.garagem.suporte.IntegracaoBase;
import com.fasterxml.jackson.databind.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "app.seguranca.rate-limit.habilitado=false",
      "spring.datasource.hikari.maximum-pool-size=16"
    })
@AutoConfigureMockMvc
public abstract class RevendaIntegrationBase extends IntegracaoBase {
  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper json;
  @Autowired PasswordEncoder encoder;

  protected record Empresa(UUID id, UUID usuario, String token) {}

  protected Empresa empresa(String... modulos) throws Exception {
    String slug = "r-" + UUID.randomUUID();
    UUID id =
        jdbc.execute(
            (Connection c) -> {
              try (var s = c.prepareStatement("select provisionar_empresa(?,?,?,?,?)")) {
                s.setString(1, slug);
                s.setString(2, "Revenda teste");
                s.setArray(3, c.createArrayOf("text", modulos));
                s.setString(4, "teste");
                s.setString(5, "Fixture de empresa");
                try (var r = s.executeQuery()) {
                  r.next();
                  return r.getObject(1, UUID.class);
                }
              }
            });
    UUID usuario = UUID.randomUUID();
    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
        usuario,
        id,
        "Vendedor",
        "owner@test.local",
        encoder.encode("SenhaSegura123!"));
    var response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsBytes(
                            Map.of(
                                "oficina",
                                slug,
                                "email",
                                "owner@test.local",
                                "senha",
                                "SenhaSegura123!"))))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
    return new Empresa(
        id, usuario, json.readTree(response.getContentAsByteArray()).path("accessToken").asText());
  }

  protected ResultActions req(Empresa e, String method, String path, Object body) throws Exception {
    MockHttpServletRequestBuilder request =
        switch (method) {
          case "POST" -> post("/api/v1" + path);
          case "PUT" -> put("/api/v1" + path);
          default -> get("/api/v1" + path);
        };
    // Empresa nula é chamada anônima: o site público e as checagens de 401 precisam desse caminho.
    if (e != null) request.header("Authorization", "Bearer " + e.token());
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    return mvc.perform(request);
  }

  protected JsonNode ok(Empresa e, String method, String path, Object body, int status)
      throws Exception {
    var r = req(e, method, path, body).andReturn().getResponse();
    assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
    return r.getContentAsByteArray().length == 0
        ? json.nullNode()
        : json.readTree(r.getContentAsByteArray());
  }

  protected JsonNode cliente(Empresa e) throws Exception {
    return ok(
        e,
        "POST",
        "/clientes",
        Map.of("nome", "Cliente " + UUID.randomUUID(), "telefone", "11999999999"),
        201);
  }

  protected JsonNode veiculo(Empresa e, JsonNode cliente) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("clienteId", cliente == null ? null : cliente.path("id").asText());
    body.put("chassi", UUID.randomUUID().toString().replace("-", "").substring(0, 17));
    body.putAll(
        Map.of("marca", "Toyota", "modelo", "Corolla", "ano", 2023, "km", 10000, "cor", "Prata"));
    return ok(e, "POST", "/veiculos", body, 201);
  }

  protected JsonNode estoque(Empresa e, boolean disponivel) throws Exception {
    var v = veiculo(e, cliente(e));
    var stock =
        ok(
            e,
            "POST",
            "/revenda/estoque",
            Map.of(
                "veiculoId",
                v.path("id").asText(),
                "responsavelId",
                e.usuario(),
                "entrada",
                LocalDate.now().toString(),
                "origem",
                "COMPRA",
                "valorAquisicao",
                "50000.00",
                "precoAnunciado",
                "65000.00",
                "precoMinimo",
                "55000.00"),
            201);
    return disponivel
        ? ok(
            e,
            "PUT",
            "/revenda/estoque/" + stock.path("id").asText() + "/status",
            Map.of("revisao", stock.path("revisao").asLong(), "status", "DISPONIVEL"),
            200)
        : stock;
  }

  protected Map<String, Object> venda(Empresa e, JsonNode estoque, JsonNode cliente) {
    return new HashMap<>(
        Map.of(
            "estoqueId",
            estoque.path("id").asText(),
            "clienteId",
            cliente.path("id").asText(),
            "vendedorId",
            e.usuario(),
            "revisaoEstoque",
            estoque.path("revisao").asLong(),
            "valorVendido",
            "60000.00",
            "entrada",
            "1000.00",
            "valorTroca",
            "0.00"));
  }

  protected Map<String, Object> reserva(Empresa e, JsonNode estoque, JsonNode cliente) {
    return Map.of(
        "estoqueId",
        estoque.path("id").asText(),
        "clienteId",
        cliente.path("id").asText(),
        "vendedorId",
        e.usuario(),
        "revisaoEstoque",
        estoque.path("revisao").asLong(),
        "validade",
        Instant.now().plusSeconds(3600).toString());
  }

  protected JsonNode avaliacao(Empresa e, JsonNode v, JsonNode cliente) throws Exception {
    var p =
        new HashMap<String, Object>(
            Map.of(
                "veiculoId",
                v.path("id").asText(),
                "avaliadorId",
                e.usuario(),
                "data",
                LocalDate.now().toString(),
                "km",
                10000,
                "valorEstimado",
                "30000.00",
                "valorOferecido",
                "25000.00",
                "validade",
                Instant.now().plusSeconds(86400).toString()));
    if (cliente != null) p.put("clienteId", cliente.path("id").asText());
    return ok(e, "POST", "/revenda/avaliacoes", p, 201);
  }
}
