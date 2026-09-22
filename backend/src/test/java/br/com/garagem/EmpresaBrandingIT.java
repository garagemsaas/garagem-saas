package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.garagem.shared.seguranca.Tokens;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class EmpresaBrandingIT extends br.com.garagem.suporte.IntegracaoBase {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder encoder;
  UUID a, b;
  String ta, tb, tm, slugA, refresh;
  static final String SENHA = "SenhaSegura123!";

  @BeforeEach
  void preparar() throws Exception {
    a = UUID.randomUUID();
    b = UUID.randomUUID();
    slugA = "a-" + a;
    empresa(a, slugA, "Empresa A");
    empresa(b, "b-" + b, "Empresa B");
    ta = login(slugA, "owner@test.local").path("accessToken").asText();
    tb = login("b-" + b, "owner@test.local").path("accessToken").asText();
    tm = login(slugA, "mecanico@test.local").path("accessToken").asText();
  }

  void empresa(UUID id, String slug, String nome) {
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", id, nome, slug);
    for (String role : List.of("OWNER", "MECANICO"))
      jdbc.update(
          "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,?)",
          UUID.randomUUID(),
          id,
          role,
          role.toLowerCase() + "@test.local",
          encoder.encode(SENHA),
          role);
  }

  JsonNode login(String slug, String email) throws Exception {
    return call(
        post("/api/v1/auth/login"),
        null,
        Map.of("oficina", slug, "email", email, "senha", SENHA),
        200);
  }

  JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int status)
      throws Exception {
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    var response = mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse();
    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    return response.getContentAsByteArray().length == 0
        ? json.nullNode()
        : json.readTree(response.getContentAsByteArray());
  }

  Map<String, Object> edicao(String nome, long revision) {
    return new HashMap<>(
        Map.of(
            "nomeExibicao",
            nome,
            "corPrimaria",
            "#123456",
            "corSecundaria",
            "#654321",
            "revisao",
            revision));
  }

  @Test
  void brandingEEdicaoSaoIsoladosEPayloadNaoTrocaTenant() throws Exception {
    assertThat(call(get("/api/v1/empresa"), ta, null, 200).at("/branding/nomeExibicao").asText())
        .isEqualTo("Empresa A");
    assertThat(call(get("/api/v1/empresa"), tb, null, 200).at("/branding/nomeExibicao").asText())
        .isEqualTo("Empresa B");
    var input = edicao("A alterada", 0);
    input.put("oficinaId", b);
    input.put("empresaId", b);
    input.put("modulos", List.of("REVENDA"));
    input.put("status", "INATIVA");
    var response = call(put("/api/v1/empresa"), ta, input, 200);
    assertThat(response.at("/branding/nomeExibicao").asText()).isEqualTo("A alterada");
    assertThat(response.path("status").asText()).isEqualTo("ATIVA");
    assertThat(response.path("modulos").toString()).isEqualTo("[\"OFICINA\"]");
    assertThat(
            call(get("/api/v1/empresa?oficinaId=" + b), ta, null, 200)
                .at("/branding/nomeExibicao")
                .asText())
        .isEqualTo("A alterada");
    assertThat(call(get("/api/v1/empresa"), tb, null, 200).at("/branding/nomeExibicao").asText())
        .isEqualTo("Empresa B");
    call(get("/api/v1/empresa/" + b), ta, null, 404);
    call(put("/api/v1/empresa/" + b), ta, edicao("B invadida", 0), 404);
    call(put("/api/v1/empresa"), tm, edicao("Proibido", 1), 403);
    call(put("/api/v1/empresa"), ta, edicao("Obsoleta", 0), 409);
    call(
        put("/api/v1/empresa"),
        ta,
        Map.of(
            "nomeExibicao",
            "A",
            "corPrimaria",
            "url(https://x)",
            "corSecundaria",
            "#111111",
            "revisao",
            1),
        400);
  }

  MockMultipartFile png(String filename) throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    return new MockMultipartFile("arquivo", filename, "image/png", bytes.toByteArray());
  }

  @Test
  void imagensPrivadasEPublicasPertencemAoTenantDoToken() throws Exception {
    var result =
        mvc.perform(
                multipart("/api/v1/empresa/imagens/logo")
                    .file(png("../../logo.png"))
                    .param("revisao", "0")
                    .header("Authorization", "Bearer " + ta))
            .andExpect(status().isOk())
            .andReturn();
    String id =
        json.readTree(result.getResponse().getContentAsByteArray()).at("/branding/logoId").asText();
    mvc.perform(get("/api/v1/empresa/imagens/logo/" + id).header("Authorization", "Bearer " + ta))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
    call(get("/api/v1/empresa/imagens/logo/" + id), tb, null, 404);
    assertThat(
            jdbc.queryForObject(
                "select oficina_id from empresa_imagem where id=?",
                UUID.class,
                UUID.fromString(id)))
        .isEqualTo(a);
    String token = publico(a);
    assertThat(
            call(get("/api/v1/publico/" + token + "/empresa?oficinaId=" + b), null, null, 200)
                .path("nomeExibicao")
                .asText())
        .isEqualTo("Empresa A");
    mvc.perform(get("/api/v1/publico/" + token + "/empresa/imagens/logo/" + id))
        .andExpect(status().isOk());
    call(get("/api/v1/publico/" + publico(b) + "/empresa/imagens/logo/" + id), null, null, 404);
    call(get("/api/v1/publico/" + "x".repeat(43) + "/empresa"), null, null, 404);
    mvc.perform(
            multipart("/api/v1/empresa/imagens/logo")
                .file(png("logo.png"))
                .param("revisao", "1")
                .header("Authorization", "Bearer " + tm))
        .andExpect(status().isForbidden());
  }

  @Test
  void uploadRecusaSvgConteudoFalsoMimeDivergenteETamanho() throws Exception {
    for (var file :
        List.of(
            new MockMultipartFile("arquivo", "x.svg", "image/svg+xml", "<svg/>".getBytes()),
            new MockMultipartFile("arquivo", "x.png", "image/png", "<script/>".getBytes()),
            new MockMultipartFile("arquivo", "x.jpg", "image/jpeg", png("x.png").getBytes())))
      mvc.perform(
              multipart("/api/v1/empresa/imagens/logo")
                  .file(file)
                  .param("revisao", "0")
                  .header("Authorization", "Bearer " + ta))
          .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            multipart("/api/v1/empresa/imagens/logo")
                .file(new MockMultipartFile("arquivo", "x.png", "image/png", new byte[2097153]))
                .param("revisao", "0")
                .header("Authorization", "Bearer " + ta))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void modulosSaoAutoridadeDoBackendEStatusBloqueiaSessoesELinks() throws Exception {
    call(get("/api/v1/ordens-servico"), ta, null, 200);
    String token = publico(a);
    jdbc.update("delete from empresa_modulo where oficina_id=?", a);
    jdbc.update("insert into empresa_modulo values(?,'REVENDA')", a);
    for (String path :
        List.of(
            "/ordens-servico",
            "/dashboard",
            "/dinheiro-esquecido/resumo",
            "/orcamento-versoes/" + UUID.randomUUID() + "/reavaliacao"))
      call(get("/api/v1" + path + "?modulo=OFICINA"), ta, null, 404);
    call(get("/api/v1/publico/" + token + "/empresa"), null, null, 404);
    call(get("/api/v1/empresa"), ta, null, 200);
    jdbc.update("insert into empresa_modulo values(?,'OFICINA')", a);
    assertThat(call(get("/api/v1/empresa"), ta, null, 200).path("modulos")).hasSize(2);
    call(get("/api/v1/ordens-servico"), ta, null, 200);
    var session = login(slugA, "owner@test.local");
    jdbc.update("update oficina set situacao='INATIVA' where id=?", a);
    call(get("/api/v1/clientes"), ta, null, 401);
    call(get("/api/v1/empresa"), ta, null, 401);
    call(get("/api/v1/publico/" + token + "/empresa"), null, null, 404);
    call(
        post("/api/v1/auth/login"),
        null,
        Map.of("oficina", slugA, "email", "owner@test.local", "senha", SENHA),
        401);
    call(
        post("/api/v1/auth/refresh"),
        null,
        Map.of("oficinaId", a, "refreshToken", session.path("refreshToken").asText()),
        401);
  }

  @Test
  void empresaNovaOperaSemAssinaturaEBillingNaoEstaExposto() throws Exception {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from assinatura where oficina_id=?", Integer.class, a))
        .isZero();
    call(post("/api/v1/clientes"), ta, Map.of("nome", "Cliente", "telefone", "11999999999"), 201);
    call(get("/api/v1/assinatura"), ta, null, 404);
    // O webhook do gateway era a única rota anônima de escrita da API. Saiu junto com a cobrança:
    // hoje o caminho não existe e, sem sessão, nem chega a ser resolvido.
    call(post("/api/v1/webhooks/pagamento"), null, Map.of(), 401);
  }

  @Test
  void revisaoDeBrandingSerializaEscritasConcorrentes() throws Exception {
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (int i = 0; i < 4; i++) {
        final int index = i;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          put("/api/v1/empresa")
                              .header("Authorization", "Bearer " + ta)
                              .contentType("application/json")
                              .content(json.writeValueAsBytes(edicao("Nome " + index, 0))))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      start.countDown();
      var statuses = new ArrayList<Integer>();
      for (var future : futures)
        statuses.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
      assertThat(Collections.frequency(statuses, 200)).isEqualTo(1);
      assertThat(Collections.frequency(statuses, 409)).isEqualTo(3);
    }
    assertThat(call(get("/api/v1/empresa"), ta, null, 200).at("/branding/revisao").asLong())
        .isEqualTo(1);
  }

  @Test
  void administracaoEhAuditadaEValidaSemEndpointGlobal() throws Exception {
    jdbc.queryForObject(
        "select administrar_empresa(?, 'INATIVA',array['REVENDA'],'operador','Contrato atualizado')",
        Object.class,
        slugA);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from empresa_administracao_evento where oficina_id=?",
                Integer.class,
                a))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select situacao from oficina where id=?", String.class, a))
        .isEqualTo("INATIVA");
    assertThatThrownBy(
            () -> jdbc.update("delete from empresa_administracao_evento where oficina_id=?", a))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.queryForObject(
                    "select administrar_empresa(?, 'ATIVA',array['INVALIDO'],'operador','Tentativa')",
                    Object.class,
                    slugA))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(jdbc.queryForObject("select situacao from oficina where id=?", String.class, a))
        .isEqualTo("INATIVA");
    call(put("/api/v1/empresa/modulos"), tb, Map.of("modulos", List.of("OFICINA", "REVENDA")), 404);
  }

  @Test
  void migracaoPreservaEmpresaEContratoExistentes() {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var source = java.util.Objects.requireNonNull(jdbc.getDataSource());
    org.flywaydb.core.Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .target("5")
        .load()
        .migrate();
    UUID id = UUID.randomUUID();
    jdbc.execute(
        (java.sql.Connection connection) -> {
          String previous = connection.getSchema();
          try {
            connection.setSchema(schema);
            try (var statement =
                connection.prepareStatement(
                    "insert into oficina(id,nome,slug) values(?, 'Empresa existente','existente')")) {
              statement.setObject(1, id);
              statement.executeUpdate();
            }
          } finally {
            connection.setSchema(previous);
          }
          return null;
        });
    org.flywaydb.core.Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    assertThat(
            jdbc.queryForObject(
                "select nome from " + schema + ".oficina where id=?", String.class, id))
        .isEqualTo("Empresa existente");
    assertThat(
            jdbc.queryForObject(
                "select modulo from " + schema + ".empresa_modulo where oficina_id=?",
                String.class,
                id))
        .isEqualTo("OFICINA");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + schema + ".assinatura where oficina_id=?",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  String publico(UUID tenant) {
    UUID cliente = UUID.randomUUID(),
        veiculo = UUID.randomUUID(),
        os = UUID.randomUUID(),
        link = UUID.randomUUID();
    jdbc.update(
        "insert into cliente(id,oficina_id,nome,telefone) values(?,?,?,?)",
        cliente,
        tenant,
        "Cliente",
        "11999999999");
    jdbc.update(
        "insert into veiculo(id,oficina_id,cliente_id,placa,marca,modelo,ano,km,cor) values(?,?,?,'ABC1D23','Marca','Modelo',2020,0,'Preto')",
        veiculo,
        tenant,
        cliente);
    jdbc.update(
        "insert into ordem_servico(id,oficina_id,cliente_id,veiculo_id,numero,status,km_entrada,relato) values(?,?,?,?,1,'RECEBIDO',0,'Relato')",
        os,
        tenant,
        cliente,
        veiculo);
    String token = Tokens.novo();
    jdbc.update(
        "insert into link_acesso_publico(id,oficina_id,ordem_servico_id,token_hash,expira_em) values(?,?,?,?,now()+interval '1 day')",
        link,
        tenant,
        os,
        Tokens.hash(token));
    return token;
  }
}
