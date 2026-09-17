package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;

/** Fase 6: campos de observabilidade e autenticação sobre HTTP/SQL reais. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(Fase2IT.StorageEmMemoria.class)
class Fase6IT {
  static PostgreSQLContainer<?> postgres;

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
  static final Random RANDOM = new Random();
  static String hash;

  UUID oficinaA, oficinaB;
  String slugA, slugB;
  UUID ownerA, atendenteA, mecanicoA, inativoA, ownerB, mecanicoB;
  String tokenOwnerA, tokenAtendenteA, tokenMecanicoA, tokenOwnerB;

  @BeforeEach
  void setup() throws Exception {
    if (hash == null) hash = encoder.encode(SENHA);
    oficinaA = UUID.randomUUID();
    oficinaB = UUID.randomUUID();
    slugA = "a-" + oficinaA;
    slugB = "b-" + oficinaB;
    criarOficina(oficinaA, slugA);
    criarOficina(oficinaB, slugB);
    ownerA = criarUsuario(oficinaA, "owner@a.test", "OWNER", true);
    atendenteA = criarUsuario(oficinaA, "atendente@a.test", "ATENDENTE", true);
    mecanicoA = criarUsuario(oficinaA, "mecanico@a.test", "MECANICO", true);
    inativoA = criarUsuario(oficinaA, "inativo@a.test", "MECANICO", false);
    ownerB = criarUsuario(oficinaB, "owner@b.test", "OWNER", true);
    mecanicoB = criarUsuario(oficinaB, "mecanico@b.test", "MECANICO", true);
    tokenOwnerA = token(slugA, "owner@a.test");
    tokenAtendenteA = token(slugA, "atendente@a.test");
    tokenMecanicoA = token(slugA, "mecanico@a.test");
    tokenOwnerB = token(slugB, "owner@b.test");
  }

  // -------------------------------------------------------------- fixtures e utilitários

  void criarOficina(UUID id, String slug) {
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", id, "Oficina", slug);
  }

  UUID criarUsuario(UUID oficina, String email, String papel, boolean ativo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel,ativo) values(?,?,?,?,?,?,?)",
        id,
        oficina,
        "Pessoa " + papel,
        email,
        hash,
        papel,
        ativo);
    return id;
  }

  String token(String slug, String email) throws Exception {
    return login(slug, email, SENHA, 200).path("accessToken").asText();
  }

  JsonNode login(String slug, String email, String senha, int esperado) throws Exception {
    var body = new HashMap<String, Object>();
    if (slug != null) body.put("oficina", slug);
    if (email != null) body.put("email", email);
    if (senha != null) body.put("senha", senha);
    return post("/api/v1/auth/login", null, body, esperado);
  }

  /** Nomes dos campos de um objeto JSON, para afirmar a forma exata do DTO publicado. */
  static List<String> campos(JsonNode node) {
    var nomes = new ArrayList<String>();
    node.fieldNames().forEachRemaining(nomes::add);
    return nomes;
  }

  JsonNode corpo(MvcResult result) throws Exception {
    String texto = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    return texto.isEmpty() ? json.createObjectNode() : json.readTree(texto);
  }

  JsonNode post(String path, String token, Object body, int esperado) throws Exception {
    var req =
        MockMvcRequestBuilders.post(path)
            .contentType("application/json")
            .content(json.writeValueAsString(body));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return corpo(mvc.perform(req).andExpect(status().is(esperado)).andReturn());
  }

  JsonNode put(String path, String token, Object body, int esperado) throws Exception {
    var req =
        MockMvcRequestBuilders.put(path)
            .contentType("application/json")
            .content(json.writeValueAsString(body));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return corpo(mvc.perform(req).andExpect(status().is(esperado)).andReturn());
  }

  JsonNode get(String path, String token, int esperado) throws Exception {
    return getP(path, token, esperado);
  }

  /** GET com parâmetros fora da URL, para texto com espaço ou hífen chegar sem reencode. */
  JsonNode getP(String path, String token, int esperado, String... kv) throws Exception {
    var req = MockMvcRequestBuilders.get(path);
    for (int i = 0; i < kv.length; i += 2) req.param(kv[i], kv[i + 1]);
    if (token != null) req.header("Authorization", "Bearer " + token);
    return corpo(mvc.perform(req).andExpect(status().is(esperado)).andReturn());
  }

  String cliente(String token, String nome) throws Exception {
    return post("/api/v1/clientes", token, Map.of("nome", nome, "telefone", "11999999999"), 201)
        .path("id")
        .asText();
  }

  String veiculo(String token, String clienteId, String placa, String marca, String modelo)
      throws Exception {
    return post(
            "/api/v1/veiculos",
            token,
            Map.of(
                "clienteId", clienteId,
                "placa", placa,
                "marca", marca,
                "modelo", modelo,
                "ano", 2020,
                "km", 1000,
                "cor", "Prata"),
            201)
        .path("id")
        .asText();
  }

  String os(String token, String veiculoId) throws Exception {
    return os(token, veiculoId, 1000);
  }

  /**
   * A OS não aceita KM de entrada abaixo da cadastrada no veículo; por isso o valor é explícito.
   */
  String os(String token, String veiculoId, long kmEntrada) throws Exception {
    return post(
            "/api/v1/ordens-servico",
            token,
            Map.of("veiculoId", veiculoId, "kmEntrada", kmEntrada, "relato", "Ruído no motor"),
            201)
        .path("id")
        .asText();
  }

  String osCompleta(String token) throws Exception {
    String c = cliente(token, "Cliente " + UUID.randomUUID());
    return os(token, veiculo(token, c, placaUnica(), "Fiat", "Uno"));
  }

  static String placaUnica() {
    return "ABC" + String.format("%04d", RANDOM.nextInt(10000));
  }

  long revisao(String token, String os) throws Exception {
    return get("/api/v1/ordens-servico/" + os, token, 200).path("revisao").asLong();
  }

  JsonNode mudarStatus(String token, String os, String status, int esperado) throws Exception {
    return post(
        "/api/v1/ordens-servico/" + os + "/status",
        token,
        Map.of("status", status, "revisao", revisao(token, os)),
        esperado);
  }

  static class Captura extends ListAppender<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent e) {
      e.prepareForDeferredProcessing();
      super.append(e);
    }
  }

  Captura captura() {
    var a = new Captura();
    a.start();
    ((Logger)
            LoggerFactory.getLogger(br.com.garagem.shared.observability.RequestLoggingFilter.class))
        .addAppender(a);
    return a;
  }

  void fechar(Captura a) {
    ((Logger)
            LoggerFactory.getLogger(br.com.garagem.shared.observability.RequestLoggingFilter.class))
        .detachAppender(a);
    a.stop();
  }

  ILoggingEvent ultimo(Captura a) {
    return a.list.getLast();
  }

  Map<String, Object> camposLog(ILoggingEvent e) {
    Map<String, Object> m = new HashMap<>();
    e.getKeyValuePairs().forEach(k -> m.put(k.key, k.value));
    return m;
  }

  @Test
  void correlacaoCodigoRotaContextoESemCredenciais() throws Exception {
    var a = captura();
    try {
      var result =
          mvc.perform(
                  MockMvcRequestBuilders.get("/api/v1/clientes/" + UUID.randomUUID())
                      .header("Authorization", "Bearer " + tokenOwnerA)
                      .header("X-Request-Id", "fase6-request-001"))
              .andExpect(status().isNotFound())
              .andReturn();
      var body = corpo(result);
      var e = ultimo(a);
      assertThat(body.path("requestId").asText())
          .isEqualTo(result.getResponse().getHeader("X-Request-Id"))
          .isEqualTo(e.getMDCPropertyMap().get("request_id"));
      assertThat(e.getMDCPropertyMap())
          .containsEntry("erro_code", "NOT_FOUND")
          .containsEntry("oficina_id", oficinaA.toString())
          .containsEntry("usuario_id", ownerA.toString());
      assertThat(camposLog(e))
          .containsEntry("status", 404)
          .containsEntry("caminho", "/api/v1/clientes/{id}")
          .containsKeys("metodo", "duracao_ms", "request_lento");
      assertThat(e.getLevel())
          .isEqualTo(
              Boolean.TRUE.equals(camposLog(e).get("request_lento"))
                  ? ch.qos.logback.classic.Level.WARN
                  : ch.qos.logback.classic.Level.INFO);
      assertThat(e.getFormattedMessage() + e.getMDCPropertyMap() + camposLog(e))
          .doesNotContain(tokenOwnerA, SENHA, hash);
      get("/api/v1/clientes", null, 401);
      assertThat(ultimo(a).getMDCPropertyMap())
          .containsEntry("oficina_id", "anonimo")
          .containsEntry("usuario_id", "anonimo")
          .containsEntry("erro_code", "UNAUTHORIZED");
    } finally {
      fechar(a);
    }
  }

  @Test
  void categoriasDeFalhaSemIdentificadoresNaoConfiaveis() throws Exception {
    var a = captura();
    try {
      for (String slug : List.of(slugA, "oficina-inexistente")) {
        login(slug, "owner@a.test", "senha-incorreta-secreta", 401);
        assertThat(ultimo(a).getMDCPropertyMap()).containsEntry("auth_falha", "LOGIN_RECUSADO");
      }
      login(slugA, "inativo@a.test", SENHA, 401);
      assertThat(ultimo(a).getMDCPropertyMap()).containsEntry("auth_falha", "LOGIN_RECUSADO");
      get("/api/v1/clientes", "token-malformado-secreto", 401);
      assertThat(ultimo(a).getMDCPropertyMap())
          .containsEntry("auth_falha", "BEARER_INVALIDO_OU_EXPIRADO");
      post("/api/v1/clientes", tokenMecanicoA, Map.of("nome", "Teste", "telefone", "123"), 403);
      assertThat(ultimo(a).getMDCPropertyMap()).containsEntry("auth_falha", "PAPEL_NAO_AUTORIZADO");
      jdbc.update("update usuario set ativo=false where id=?", ownerA);
      get("/api/v1/clientes", tokenOwnerA, 401);
      assertThat(ultimo(a).getMDCPropertyMap())
          .containsEntry("auth_falha", "SESSAO_DESATUALIZADA_OU_INATIVA")
          .containsEntry("oficina_id", "anonimo");
      assertThat(
              a.list.stream()
                  .map(e -> e.getFormattedMessage() + e.getMDCPropertyMap() + camposLog(e))
                  .toList()
                  .toString())
          .doesNotContain(
              "senha-incorreta-secreta", "token-malformado-secreto", SENHA, "owner@a.test");
    } finally {
      fechar(a);
    }
  }

  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

  @Test
  void tokenExpiradoRecusadoSemVazarCredencial() throws Exception {
    var agora = java.time.Instant.now();
    var claims =
        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
            .issuer("garagem-api")
            .subject(ownerA.toString())
            .audience(List.of("garagem-api"))
            .issuedAt(agora.minusSeconds(7200))
            .expiresAt(agora.minusSeconds(3600))
            .claim("oficina_id", oficinaA.toString())
            .claim("papel", "OWNER")
            .build();
    String expirado =
        jwtEncoder
            .encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                    org.springframework.security.oauth2.jwt.JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();
    var a = captura();
    try {
      get("/api/v1/clientes", expirado, 401);
      assertThat(ultimo(a).getMDCPropertyMap())
          .containsEntry("auth_falha", "BEARER_INVALIDO_OU_EXPIRADO")
          .containsEntry("erro_code", "UNAUTHORIZED");
      assertThat(ultimo(a).getMDCPropertyMap().toString()).doesNotContain(expirado);
    } finally {
      fechar(a);
    }
  }

  @Test
  void refreshRevogadoDiagnosticavelELogoutIdempotente() throws Exception {
    var sessao = login(slugA, "owner@a.test", SENHA, 200);
    var input = Map.of("oficinaId", oficinaA, "refreshToken", sessao.path("refreshToken").asText());
    post("/api/v1/auth/logout", null, input, 204);
    post("/api/v1/auth/logout", null, input, 204);
    var a = captura();
    try {
      post("/api/v1/auth/refresh", null, input, 401);
      assertThat(ultimo(a).getMDCPropertyMap())
          .containsEntry("auth_falha", "REFRESH_RECUSADO")
          .containsEntry("erro_code", "UNAUTHORIZED");
    } finally {
      fechar(a);
    }
  }

  @Test
  void codigos400409415EPublicoMascarado() throws Exception {
    var a = captura();
    try {
      post("/api/v1/clientes", tokenOwnerA, Map.of(), 400);
      assertThat(ultimo(a).getMDCPropertyMap()).containsEntry("erro_code", "VALIDATION_ERROR");
      String os = osCompleta(tokenOwnerA);
      mudarStatus(tokenOwnerA, os, "PRONTO", 409);
      assertThat(ultimo(a).getMDCPropertyMap()).containsEntry("erro_code", "CONFLICT");
      post("/api/v1/ordens-servico/" + os + "/fotos", tokenOwnerA, Map.of(), 415);
      assertThat(ultimo(a).getMDCPropertyMap())
          .containsEntry("erro_code", "UNSUPPORTED_MEDIA_TYPE");
      get("/api/v1/publico/" + "s".repeat(43) + "/segredo-extra", null, 404);
      assertThat(camposLog(ultimo(a)).get("caminho").toString())
          .doesNotContain("s".repeat(43), "segredo-extra");
    } finally {
      fechar(a);
    }
  }
}
