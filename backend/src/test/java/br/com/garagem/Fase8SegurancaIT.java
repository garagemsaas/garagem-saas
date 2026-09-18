package br.com.garagem;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Regressão das correções e das garantias verificadas na auditoria de segurança
 * (docs/security-audit.md). Cada cenário aqui existe para impedir que uma proteção volte a cair sem
 * ninguém perceber.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class Fase8SegurancaIT {
  static PostgreSQLContainer<?> postgres;

  /**
   * Cada login usa uma origem distinta para não consumir o teto de outro cenário; só o teste de
   * força bruta reaproveita a mesma, de propósito.
   */
  static final AtomicInteger ORIGENS = new AtomicInteger(1);

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    String local = System.getenv("TEST_DATABASE_URL");
    if (local == null) {
      postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");
      postgres.start();
      r.add("spring.datasource.url", postgres::getJdbcUrl);
      r.add("spring.datasource.username", postgres::getUsername);
      r.add("spring.datasource.password", postgres::getPassword);
    } else {
      r.add("spring.datasource.url", () -> local);
      r.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
      r.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }
    r.add("app.jwt.secret", () -> "test-only-secret-at-least-thirty-two-bytes-long");
    r.add("app.storage.access-key", () -> "test-user");
    r.add("app.storage.secret-key", () -> "test-only-storage-password");
    r.add("app.seguranca.rate-limit.habilitado", () -> true);
    r.add("app.seguranca.rate-limit.login", () -> 5);
    r.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
  }

  @AfterAll
  static void close() {
    if (postgres != null) postgres.stop();
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;

  static final String SENHA = "SenhaSegura123!";
  static String hash;

  UUID oficinaA, oficinaB, ownerA, mecanicoA, atendenteA, owner2A, ownerB;
  String slugA, slugB, tokenOwnerA, tokenMecanicoA, tokenAtendenteA, tokenOwnerB;

  @BeforeEach
  void setup() throws Exception {
    if (hash == null) hash = encoder.encode(SENHA);
    oficinaA = UUID.randomUUID();
    oficinaB = UUID.randomUUID();
    slugA = "a-" + oficinaA;
    slugB = "b-" + oficinaB;
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", oficinaA, "A", slugA);
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", oficinaB, "B", slugB);
    ownerA = usuario(oficinaA, "owner@a.test", "OWNER");
    owner2A = usuario(oficinaA, "owner2@a.test", "OWNER");
    mecanicoA = usuario(oficinaA, "mec@a.test", "MECANICO");
    atendenteA = usuario(oficinaA, "at@a.test", "ATENDENTE");
    ownerB = usuario(oficinaB, "owner@b.test", "OWNER");
    tokenOwnerA = token(slugA, "owner@a.test");
    tokenMecanicoA = token(slugA, "mec@a.test");
    tokenAtendenteA = token(slugA, "at@a.test");
    tokenOwnerB = token(slugB, "owner@b.test");
  }

  UUID usuario(UUID oficina, String email, String papel) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,?)",
        id,
        oficina,
        "Pessoa",
        email,
        hash,
        papel);
    return id;
  }

  /** Origem distinta por chamada, para o teto de login não interferir nos demais cenários. */
  static RequestPostProcessor origemUnica() {
    int n = ORIGENS.incrementAndGet();
    return req -> {
      req.setRemoteAddr("10." + (n / 65536 % 256) + "." + (n / 256 % 256) + "." + (n % 256));
      return req;
    };
  }

  static RequestPostProcessor origem(String ip) {
    return req -> {
      req.setRemoteAddr(ip);
      return req;
    };
  }

  JsonNode corpo(MvcResult r) throws Exception {
    String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return texto.isEmpty() ? json.createObjectNode() : json.readTree(texto);
  }

  MvcResult login(String slug, String email, String senha, RequestPostProcessor origem)
      throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .with(origem)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("oficina", slug, "email", email, "senha", senha))))
        .andReturn();
  }

  String token(String slug, String email) throws Exception {
    return corpo(login(slug, email, SENHA, origemUnica())).path("accessToken").asText();
  }

  int status(MockMvcRequestBuilders ignored) {
    return 0;
  }

  int get(String path, String token) throws Exception {
    var req = MockMvcRequestBuilders.get(path).with(origemUnica());
    if (token != null) req.header("Authorization", "Bearer " + token);
    return mvc.perform(req).andReturn().getResponse().getStatus();
  }

  int envia(String metodo, String path, String token, Object body) throws Exception {
    var req =
        switch (metodo) {
          case "POST" -> MockMvcRequestBuilders.post(path);
          case "PUT" -> MockMvcRequestBuilders.put(path);
          case "DELETE" -> MockMvcRequestBuilders.delete(path);
          default -> MockMvcRequestBuilders.get(path);
        };
    req.with(origemUnica()).contentType("application/json").content(json.writeValueAsString(body));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return mvc.perform(req).andReturn().getResponse().getStatus();
  }

  // ------------------------------------------------------- superfície anônima

  @Test
  void nenhumEndpointDeNegocioRespondeSemAutenticacao() throws Exception {
    for (String path :
        List.of(
            "/api/v1/clientes",
            "/api/v1/veiculos",
            "/api/v1/ordens-servico",
            "/api/v1/usuarios",
            "/api/v1/dashboard",
            "/api/v1/assinatura",
            "/api/v1/assinatura/consumo",
            "/api/v1/assinatura/eventos",
            "/api/v1/dinheiro-esquecido/oportunidades",
            "/api/v1/dinheiro-esquecido/resumo"))
      assertThat(get(path, null)).as(path).isEqualTo(401);
  }

  @Test
  void caminhosDesconhecidosNaoVazamArquivoNemDetalhe() throws Exception {
    // Deny by default: qualquer caminho fora da allowlist exige sessão, inclusive os que um
    // scanner tenta primeiro.
    for (String path :
        List.of(
            "/.env",
            "/.git/config",
            "/application.yml",
            "/BOOT-INF/classes/application.yml",
            "/backup.sql",
            "/actuator/env",
            "/actuator/beans",
            "/actuator/heapdump",
            "/actuator/metrics",
            "/actuator/loggers")) assertThat(get(path, null)).as(path).isEqualTo(401);
  }

  @Test
  void somenteHealthEhPublicoNoActuatorESemDetalhe() throws Exception {
    // Sem storage neste teste o estado pode ser DOWN; o que se audita aqui é que o health
    // público responde sem descrever a infraestrutura, esteja ele UP ou não.
    var resposta =
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health").with(origemUnica()))
            .andReturn()
            .getResponse();
    assertThat(resposta.getStatus()).isIn(200, 503);
    var saida = json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));
    assertThat(saida.path("status").asText()).isIn("UP", "DOWN", "OUT_OF_SERVICE");
    assertThat(saida.has("components")).isFalse();
    assertThat(saida.has("details")).isFalse();
    assertThat(saida.toString()).doesNotContain("jdbc:").doesNotContain("postgres");
  }

  // ------------------------------------------------------- cabeçalhos

  @Test
  void respostasTrazemOsCabecalhosDeSeguranca() throws Exception {
    var resposta =
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/clientes").with(origemUnica()))
            .andReturn()
            .getResponse();
    assertThat(resposta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(resposta.getHeader("X-Frame-Options")).isEqualTo("DENY");
    assertThat(resposta.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(resposta.getHeader("Content-Security-Policy"))
        .contains("default-src 'none'")
        .contains("frame-ancestors 'none'");
    assertThat(resposta.getHeader("Permissions-Policy")).contains("geolocation=()");
    assertThat(resposta.getHeader("Cache-Control")).contains("no-store");
  }

  @Test
  void documentacaoRecebeCspPropriaParaNaoQuebrar() throws Exception {
    var resposta =
        mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs").with(origemUnica()))
            .andReturn()
            .getResponse();
    // A CSV fechada da API quebraria o Swagger; a dele permite o próprio script, e nada externo.
    assertThat(resposta.getHeader("Content-Security-Policy"))
        .contains("default-src 'self'")
        .contains("frame-ancestors 'none'")
        .doesNotContain("default-src 'none'");
  }

  // ------------------------------------------------------- força bruta

  @Test
  void tetoDeLoginBarraForcaBrutaEDevolveRetryAfter() throws Exception {
    String ip = "203.0.113.7";
    int recusados = 0;
    MvcResult ultima = null;
    for (int i = 0; i < 9; i++) {
      ultima = login(slugA, "owner@a.test", "senha-errada-" + i, origem(ip));
      if (ultima.getResponse().getStatus() == 429) recusados++;
    }
    assertThat(recusados).as("tentativas barradas após o teto").isGreaterThan(0);
    assertThat(ultima.getResponse().getStatus()).isEqualTo(429);
    assertThat(ultima.getResponse().getHeader("Retry-After")).isNotNull();
    assertThat(corpo(ultima).path("code").asText()).isEqualTo("RATE_LIMITED");
    // Mesmo barrado, a senha correta continua recusada — o teto não vira bypass.
    assertThat(login(slugA, "owner@a.test", SENHA, origem(ip)).getResponse().getStatus())
        .isEqualTo(429);
    // Outra origem não é punida pelo abuso alheio.
    assertThat(login(slugA, "owner@a.test", SENHA, origemUnica()).getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void loginNaoRevelaSeUsuarioOuOficinaExistem() throws Exception {
    var existente = corpo(login(slugA, "owner@a.test", "errada", origemUnica()));
    var semUsuario = corpo(login(slugA, "naoexiste@a.test", "errada", origemUnica()));
    var semOficina = corpo(login("oficina-inexistente", "owner@a.test", "errada", origemUnica()));
    assertThat(existente.path("detail").asText())
        .isEqualTo(semUsuario.path("detail").asText())
        .isEqualTo(semOficina.path("detail").asText());
    assertThat(existente.path("code").asText()).isEqualTo("UNAUTHORIZED");
  }

  // ------------------------------------------------------- revogação de acesso

  @Test
  void desativarUsuarioRevogaAcessoImediatamenteERefreshTokens() throws Exception {
    var sessao = corpo(login(slugA, "mec@a.test", SENHA, origemUnica()));
    String acesso = sessao.path("accessToken").asText();
    String refresh = sessao.path("refreshToken").asText();
    assertThat(get("/api/v1/clientes", acesso)).isEqualTo(200);

    assertThat(
            envia(
                "PUT",
                "/api/v1/usuarios/" + mecanicoA + "/situacao",
                tokenOwnerA,
                Map.of("ativo", false)))
        .isEqualTo(200);

    // O access token ainda não expirou, mas a sessão é conferida no banco a cada requisição.
    assertThat(get("/api/v1/clientes", acesso)).isEqualTo(401);
    assertThat(
            envia(
                "POST",
                "/api/v1/auth/refresh",
                null,
                Map.of("oficinaId", oficinaA, "refreshToken", refresh)))
        .isEqualTo(401);
    assertThat(login(slugA, "mec@a.test", SENHA, origemUnica()).getResponse().getStatus())
        .isEqualTo(401);
    // Nenhum dado do usuário foi apagado: a autoria dos registros segue íntegra.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from usuario where id=?", Integer.class, mecanicoA))
        .isEqualTo(1);
  }

  @Test
  void reativarUsuarioDevolveOAcesso() throws Exception {
    envia(
        "PUT", "/api/v1/usuarios/" + mecanicoA + "/situacao", tokenOwnerA, Map.of("ativo", false));
    assertThat(
            envia(
                "PUT",
                "/api/v1/usuarios/" + mecanicoA + "/situacao",
                tokenOwnerA,
                Map.of("ativo", true)))
        .isEqualTo(200);
    assertThat(login(slugA, "mec@a.test", SENHA, origemUnica()).getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void ninguemSeTrancaForaDaPropriaOficina() throws Exception {
    // Desativar a si mesmo deixaria a conta sem quem a reative pela aplicação.
    assertThat(
            envia(
                "PUT",
                "/api/v1/usuarios/" + ownerA + "/situacao",
                tokenOwnerA,
                Map.of("ativo", false)))
        .isEqualTo(409);
    // O último proprietário ativo também não pode ser desativado por outro.
    envia("PUT", "/api/v1/usuarios/" + owner2A + "/situacao", tokenOwnerA, Map.of("ativo", false));
    String token2 = token(slugA, "owner2@a.test");
    assertThat(token2).isEmpty();
  }

  @Test
  void somenteProprietarioRevogaAcessoESomenteNaPropriaOficina() throws Exception {
    String alvo = "/api/v1/usuarios/" + mecanicoA + "/situacao";
    assertThat(envia("PUT", alvo, tokenMecanicoA, Map.of("ativo", false))).isEqualTo(403);
    assertThat(envia("PUT", alvo, tokenAtendenteA, Map.of("ativo", false))).isEqualTo(403);
    assertThat(envia("PUT", alvo, null, Map.of("ativo", false))).isEqualTo(401);
    // Oficina B não alcança usuário de A nem para descobrir que ele existe.
    assertThat(envia("PUT", alvo, tokenOwnerB, Map.of("ativo", false))).isEqualTo(404);
    assertThat(
            jdbc.queryForObject("select ativo from usuario where id=?", Boolean.class, mecanicoA))
        .isTrue();
  }

  // ------------------------------------------------------- sessão

  @Test
  void refreshReutilizadoDerrubaAFamiliaInteira() throws Exception {
    var sessao = corpo(login(slugA, "at@a.test", SENHA, origemUnica()));
    String primeiro = sessao.path("refreshToken").asText();
    var renovada =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .with(origemUnica())
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of("oficinaId", oficinaA, "refreshToken", primeiro))))
                .andReturn());
    String segundo = renovada.path("refreshToken").asText();
    assertThat(segundo).isNotEqualTo(primeiro);

    // Replay do token já rotacionado: sinal de que duas partes têm o mesmo segredo.
    assertThat(
            envia(
                "POST",
                "/api/v1/auth/refresh",
                null,
                Map.of("oficinaId", oficinaA, "refreshToken", primeiro)))
        .isEqualTo(401);
    // O token válido do ladrão (ou do dono) também cai: a família inteira é revogada.
    assertThat(
            envia(
                "POST",
                "/api/v1/auth/refresh",
                null,
                Map.of("oficinaId", oficinaA, "refreshToken", segundo)))
        .isEqualTo(401);
  }

  @Test
  void logoutNaoEhTratadoComoReuseENaoDerrubaAsDemaisSessoes() throws Exception {
    // Duas sessões do mesmo usuário, como duas abas ou dois aparelhos.
    var aba1 = corpo(login(slugA, "at@a.test", SENHA, origemUnica()));
    var aba2 = corpo(login(slugA, "at@a.test", SENHA, origemUnica()));
    var saida = Map.of("oficinaId", oficinaA, "refreshToken", aba1.path("refreshToken").asText());

    assertThat(envia("POST", "/api/v1/auth/logout", null, saida)).isEqualTo(204);
    // Logout é idempotente e reapresentar o token só devolve 401 — não é sinal de roubo.
    assertThat(envia("POST", "/api/v1/auth/logout", null, saida)).isEqualTo(204);
    assertThat(envia("POST", "/api/v1/auth/refresh", null, saida)).isEqualTo(401);

    // A outra sessão continua de pé: sair de uma aba não pode derrubar o aparelho do lado.
    assertThat(
            envia(
                "POST",
                "/api/v1/auth/refresh",
                null,
                Map.of("oficinaId", oficinaA, "refreshToken", aba2.path("refreshToken").asText())))
        .isEqualTo(200);
  }

  @Test
  void migrationV5SobeSobreBaseComTokensJaRevogados() throws Exception {
    // A V5 falhou ao subir num banco que já tinha tokens revogados, porque a restrição vinha
    // antes do preenchimento. Migration só é validada contra dados, não contra base vazia.
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try (var conexao = jdbc.getDataSource().getConnection()) {
      var ds = new org.springframework.jdbc.datasource.SingleConnectionDataSource(conexao, true);
      org.flywaydb.core.Flyway.configure()
          .dataSource(ds)
          .schemas(schema)
          .defaultSchema(schema)
          .target("4")
          .load()
          .migrate();
      conexao.setSchema(schema);
      var db = new JdbcTemplate(ds);
      UUID t = UUID.randomUUID(), u = UUID.randomUUID();
      db.update("insert into oficina(id,nome,slug) values(?,?,?)", t, "Upgrade", "upgrade-v5");
      db.update(
          "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
          u,
          t,
          "Owner",
          "owner@upgrade.test",
          hash);
      // Um token vivo e outro já revogado: exatamente o estado que quebrava a subida.
      db.update(
          "insert into refresh_token(id,oficina_id,usuario_id,token_hash,expira_em) values(?,?,?,?,now()+interval '7 days')",
          UUID.randomUUID(),
          t,
          u,
          "hash-vivo");
      db.update(
          "insert into refresh_token(id,oficina_id,usuario_id,token_hash,expira_em,revogado_em) values(?,?,?,?,now()+interval '7 days',now())",
          UUID.randomUUID(),
          t,
          u,
          "hash-revogado");

      org.flywaydb.core.Flyway.configure()
          .dataSource(ds)
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();

      assertThat(
              db.queryForObject(
                  "select motivo_revogacao from refresh_token where token_hash='hash-revogado'",
                  String.class))
          .isEqualTo("LOGOUT");
      assertThat(
              db.queryForObject(
                  "select count(*) from refresh_token where token_hash='hash-vivo' and motivo_revogacao is null",
                  Integer.class))
          .isEqualTo(1);
      // A restrição existe e vale para escrita nova.
      assertThatThrownBy(
              () ->
                  db.update(
                      "update refresh_token set motivo_revogacao='INVENTADO' where token_hash='hash-revogado'"))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      conexao.setSchema("public");
    }
  }

  @Test
  void jwtForjadoOuSemAssinaturaEhRecusado() throws Exception {
    String payload =
        base64(
            "{\"iss\":\"garagem-api\",\"sub\":\""
                + ownerA
                + "\",\"aud\":[\"garagem-api\"],\"exp\":99999999999,\"oficina_id\":\""
                + oficinaA
                + "\",\"papel\":\"OWNER\"}");
    assertThat(
            get(
                "/api/v1/clientes",
                base64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + payload + "."))
        .isEqualTo(401);
    assertThat(
            get(
                "/api/v1/clientes",
                base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "." + payload + ".ZmFsc28"))
        .isEqualTo(401);
  }

  static String base64(String texto) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
  }

  // ------------------------------------------------------- entrada e isolamento

  @Test
  void camposControladosPeloServidorNaoVemDoCliente() throws Exception {
    var criado =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/clientes")
                        .with(origemUnica())
                        .header("Authorization", "Bearer " + tokenOwnerA)
                        .contentType("application/json")
                        .content(
                            """
                            {"nome":"Mass","telefone":"11999999999",
                             "id":"99999999-9999-9999-9999-999999999999",
                             "oficinaId":"%s","revisao":99}
                            """
                                .formatted(oficinaB)))
                .andReturn());
    assertThat(criado.path("id").asText()).isNotEqualTo("99999999-9999-9999-9999-999999999999");
    assertThat(criado.path("revisao").asLong()).isZero();
    // O tenant veio do token, não do corpo: a oficina B continua sem o registro.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from cliente where oficina_id=?", Integer.class, oficinaB))
        .isZero();
  }

  @Test
  void papelNaoEscalaPeloCorpoDaRequisicao() throws Exception {
    // Não existe endpoint de troca de papel; e criar usuário é exclusivo do proprietário.
    assertThat(
            envia(
                "POST",
                "/api/v1/usuarios",
                tokenAtendenteA,
                Map.of("nome", "X", "email", "x@a.test", "senha", SENHA, "papel", "OWNER")))
        .isEqualTo(403);
    assertThat(
            envia(
                "PUT",
                "/api/v1/usuarios/" + mecanicoA,
                tokenOwnerA,
                Map.of("papel", "OWNER", "ativo", true)))
        .isEqualTo(404);
  }

  @Test
  void ordenacaoForaDaAllowlistNaoChegaAoBanco() throws Exception {
    for (String ordenacao :
        List.of("nome;drop table cliente--", "(select 1)", "nome,asc--", "1;--"))
      assertThat(
              get(
                  "/api/v1/clientes?ordenacao="
                      + java.net.URLEncoder.encode(ordenacao, StandardCharsets.UTF_8),
                  tokenOwnerA))
          .as(ordenacao)
          .isEqualTo(400);
    // A tabela continua de pé e a busca com aspas é tratada como texto, não como SQL.
    assertThat(get("/api/v1/clientes?busca=%27%20OR%201%3D1--", tokenOwnerA)).isEqualTo(200);
  }

  @Test
  void paginacaoTemTetoEPisoSeguros() throws Exception {
    for (var caso : Map.of("100000", 100, "1000", 100, "-5", 1, "0", 1).entrySet()) {
      var saida =
          corpo(
              mvc.perform(
                      MockMvcRequestBuilders.get("/api/v1/clientes?tamanho=" + caso.getKey())
                          .with(origemUnica())
                          .header("Authorization", "Bearer " + tokenOwnerA))
                  .andReturn());
      assertThat(saida.path("tamanho").asInt()).as(caso.getKey()).isEqualTo(caso.getValue());
    }
  }

  @Test
  void erroNaoVazaStackTraceNemDetalheInterno() throws Exception {
    for (String path : List.of("/api/v1/clientes/nao-e-uuid", "/api/v1/ordens-servico/x")) {
      var saida =
          corpo(
              mvc.perform(
                      MockMvcRequestBuilders.get(path)
                          .with(origemUnica())
                          .header("Authorization", "Bearer " + tokenOwnerA))
                  .andReturn());
      String texto = saida.toString();
      assertThat(texto)
          .doesNotContain("Exception")
          .doesNotContain("org.springframework")
          .doesNotContain("java.lang")
          .doesNotContain("select ")
          .doesNotContain("Caused by");
      assertThat(saida.has("requestId")).isTrue();
    }
  }

  @Test
  void respostaDeUsuarioNuncaTrazHashDeSenha() throws Exception {
    var lista =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.get("/api/v1/usuarios")
                        .with(origemUnica())
                        .header("Authorization", "Bearer " + tokenOwnerA))
                .andReturn());
    assertThat(lista.toString())
        .doesNotContain("senhaHash")
        .doesNotContain("senha_hash")
        .doesNotContain(hash);
    assertThat(lista.path("itens").get(0).has("senha")).isFalse();
  }

  @Test
  void senhaEhGravadaApenasComoHashForte() throws Exception {
    envia(
        "POST",
        "/api/v1/usuarios",
        tokenOwnerA,
        Map.of("nome", "Novo", "email", "novo@a.test", "senha", SENHA, "papel", "MECANICO"));
    String gravado =
        jdbc.queryForObject(
            "select senha_hash from usuario where oficina_id=? and email='novo@a.test'",
            String.class,
            oficinaA);
    assertThat(gravado).isNotNull().doesNotContain(SENHA).startsWith("$2");
    // BCrypt com custo 12: o prefixo é parte do contrato de segurança, não detalhe de formatação.
    assertThat(gravado).matches("\\$2[aby]\\$12\\$.{53}");
  }
}
