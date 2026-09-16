package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
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
import org.testcontainers.containers.PostgreSQLContainer;

/** Integração da Fase 5 contra PostgreSQL real, sem alterar históricos para simular tempo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(Fase5IT.RelogioConfig.class)
class Fase5IT {
  static final String BASE = "/api/v1/dinheiro-esquecido";
  static final String OPS = BASE + "/oportunidades";
  static final Relogio RELOGIO = new Relogio();

  static class Relogio extends java.time.Clock {
    volatile java.time.Instant agora = java.time.Instant.now();

    public java.time.Instant instant() {
      return agora;
    }

    public java.time.ZoneId getZone() {
      return java.time.ZoneOffset.UTC;
    }

    public java.time.Clock withZone(java.time.ZoneId zone) {
      return java.time.Clock.fixed(agora, zone);
    }
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class RelogioConfig {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    java.time.Clock relogioTeste() {
      return RELOGIO;
    }
  }

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
  static final java.util.concurrent.atomic.AtomicInteger PLACAS =
      new java.util.concurrent.atomic.AtomicInteger();
  static String hash;

  UUID oficinaA, oficinaB;
  String slugA, slugB;
  UUID ownerA, atendenteA, mecanicoA, inativoA, ownerB, mecanicoB;
  String tokenOwnerA, tokenAtendenteA, tokenMecanicoA, tokenOwnerB;

  @BeforeEach
  void setup() throws Exception {
    RELOGIO.agora = java.time.Instant.now();
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

  /** Sequencial, não sorteada: placa repetida dava 409 no cadastro e quebrava o cenário. */
  static String placaUnica() {
    int n = PLACAS.getAndIncrement();
    return ""
        + (char) ('A' + n / 676 % 26)
        + (char) ('A' + n / 26 % 26)
        + (char) ('A' + n % 26)
        + "0A00";
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

  record Orc(String os, String versao) {}

  Orc orcamento(String token) throws Exception {
    String os = osCompleta(token);
    mudarStatus(token, os, "DIAGNOSTICO", 200);
    mudarStatus(token, os, "ORCAMENTO", 200);
    return new Orc(os, versao(token, os));
  }

  String versao(String token, String os) throws Exception {
    return post(
            "/api/v1/ordens-servico/" + os + "/orcamento/versoes",
            token,
            Map.of(
                "itens",
                List.of(
                    Map.of(
                        "tipo",
                        "SERVICO",
                        "descricao",
                        "Revisão",
                        "quantidade",
                        1,
                        "valorUnitario",
                        2000))),
            201)
        .path("id")
        .asText();
  }

  java.time.Instant publicar(String token, Orc o) throws Exception {
    mudarStatus(token, o.os(), "AGUARDANDO_APROVACAO", 200);
    return jdbc.queryForObject(
            "select publicado_em from acompanhamento_orcamento where orcamento_versao_id=?",
            java.sql.Timestamp.class,
            UUID.fromString(o.versao()))
        .toInstant();
  }

  java.time.Instant decidir(String token, Orc o, boolean aprovado) throws Exception {
    String link =
        post("/api/v1/ordens-servico/" + o.os() + "/links", token, Map.of(), 201)
            .path("token")
            .asText();
    post(
        "/api/v1/publico/" + link + "/decisao",
        null,
        Map.of("versaoId", o.versao(), "aprovado", aprovado),
        200);
    return jdbc.queryForObject(
            "select criado_em from aprovacao_orcamento where orcamento_versao_id=?",
            java.sql.Timestamp.class,
            UUID.fromString(o.versao()))
        .toInstant();
  }

  JsonNode identificar(String token) throws Exception {
    return post(BASE + "/identificar", token, Map.of(), 200);
  }

  JsonNode lista(String token) throws Exception {
    return get(OPS, token, 200).path("itens");
  }

  JsonNode oportunidade() throws Exception {
    var o = orcamento(tokenOwnerA);
    RELOGIO.agora = publicar(tokenOwnerA, o).plus(java.time.Duration.ofDays(7));
    identificar(tokenOwnerA);
    return lista(tokenOwnerA).get(0);
  }

  Map<String, Object> contato(long revisao, String resultado) {
    return Map.of(
        "canal",
        "WHATSAPP",
        "resultado",
        resultado,
        "observacao",
        "Ligação manual",
        "proximoContatoEm",
        RELOGIO.instant().plusSeconds(86400).toString(),
        "revisao",
        revisao);
  }

  JsonNode contatar(JsonNode o, String resultado) throws Exception {
    return post(
        OPS + "/" + o.path("id").asText() + "/contatos",
        tokenOwnerA,
        contato(o.path("revisao").asLong(), resultado),
        201);
  }

  String completa(Orc o) throws Exception {
    publicar(tokenOwnerA, o);
    decidir(tokenOwnerA, o, true);
    mudarStatus(tokenOwnerA, o.os(), "TESTE", 200);
    mudarStatus(tokenOwnerA, o.os(), "PRONTO", 200);
    return o.os();
  }

  java.time.LocalDate hoje() {
    return java.time.Instant.now().atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate();
  }

  JsonNode programar(String os, java.time.LocalDate dia) throws Exception {
    return put(
        "/api/v1/ordens-servico/" + os + "/proxima-revisao",
        tokenOwnerA,
        Map.of("data", dia.toString(), "revisao", revisao(tokenOwnerA, os)),
        200);
  }

  @Test
  void seteDiasExatosPublicacaoEIdempotencia() throws Exception {
    var o = orcamento(tokenOwnerA);
    var publicado = publicar(tokenOwnerA, o);
    RELOGIO.agora = publicado.plusSeconds(7 * 86400 - 1);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    RELOGIO.agora = publicado.plusSeconds(7 * 86400);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isEqualTo(1);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    var op = lista(tokenOwnerA).get(0);
    assertThat(op.path("tipo").asText()).isEqualTo("ORCAMENTO_ESQUECIDO");
    assertThat(op.path("valorPotencial").decimalValue()).isEqualByComparingTo("2000");
    assertThat(op.path("origem").path("orcamentoVersaoId").asText()).isEqualTo(o.versao());
    assertThat(op.path("diasEmAberto").asLong()).isZero();
  }

  @Test
  void criacaoDaVersaoNaoEquivaleAPublicacao() throws Exception {
    var o = orcamento(tokenOwnerA);
    RELOGIO.agora = java.time.Instant.now().plusSeconds(90 * 86400);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    var publicado = publicar(tokenOwnerA, o);
    RELOGIO.agora = publicado.plusSeconds(6 * 86400);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    mudarStatus(tokenOwnerA, o.os(), "ORCAMENTO", 200);
    publicar(tokenOwnerA, o);
    assertThat(
            jdbc.queryForObject(
                    "select publicado_em from acompanhamento_orcamento where orcamento_versao_id=?",
                    java.sql.Timestamp.class,
                    UUID.fromString(o.versao()))
                .toInstant())
        .isEqualTo(publicado);
  }

  @Test
  void aprovadosRecusadosESubstituidosNaoSaoEsquecidos() throws Exception {
    var aprovado = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, aprovado);
    decidir(tokenOwnerA, aprovado, true);
    var recusado = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, recusado);
    decidir(tokenOwnerA, recusado, false);
    var substituido = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, substituido);
    versao(tokenOwnerA, substituido.os());
    RELOGIO.agora = java.time.Instant.now().plusSeconds(8 * 86400);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    assertThat(lista(tokenOwnerA)).isEmpty();
  }

  @Test
  void reavaliacaoTrintaDiasExatosEOverride() throws Exception {
    var o = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, o);
    var recusa = decidir(tokenOwnerA, o, false);
    RELOGIO.agora = recusa.plusSeconds(30 * 86400 - 1);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    RELOGIO.agora = recusa.plusSeconds(30 * 86400);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isEqualTo(1);
    assertThat(lista(tokenOwnerA).get(0).path("tipo").asText()).isEqualTo("REAVALIACAO_PENDENTE");
    var outro = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, outro);
    decidir(tokenOwnerA, outro, false);
    String url = "/api/v1/orcamento-versoes/" + outro.versao() + "/reavaliacao";
    put(url, tokenOwnerA, Map.of("data", hoje().plusDays(60).toString(), "revisao", 0), 200);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    long revision = get(url, tokenOwnerA, 200).path("revisao").asLong();
    put(url, tokenOwnerA, Map.of("data", hoje().plusDays(1).toString(), "revisao", revision), 200);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isEqualTo(1);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
  }

  @Test
  void novaNegociacaoConcluidaImpedeReavaliacao() throws Exception {
    var o = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, o);
    decidir(tokenOwnerA, o, false);
    var nova = new Orc(o.os(), versao(tokenOwnerA, o.os()));
    publicar(tokenOwnerA, nova);
    decidir(tokenOwnerA, nova, true);
    RELOGIO.agora = java.time.Instant.now().plusSeconds(40 * 86400);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
  }

  @Test
  void revisaoPorDataSemValorInventado() throws Exception {
    String os = completa(orcamento(tokenOwnerA));
    programar(os, hoje().plusDays(10));
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    RELOGIO.agora =
        hoje().plusDays(10).atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant();
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isEqualTo(1);
    var op = lista(tokenOwnerA).get(0);
    assertThat(op.path("valorPotencial").isNull()).isTrue();
    assertThat(op.path("tipo").asText()).isEqualTo("REVISAO_ATRASADA");
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    put(
        "/api/v1/ordens-servico/" + os + "/proxima-revisao",
        tokenOwnerA,
        Map.of("data", hoje().plusDays(11).toString(), "revisao", revisao(tokenOwnerA, os)),
        409);
  }

  @Test
  void atendimentoPosteriorResolveRevisaoExistente() throws Exception {
    String os = completa(orcamento(tokenOwnerA));
    programar(os, hoje());
    identificar(tokenOwnerA);
    String veiculo =
        get("/api/v1/ordens-servico/" + os, tokenOwnerA, 200).path("veiculoId").asText();
    String nova = os(tokenOwnerA, veiculo);
    mudarStatus(tokenOwnerA, nova, "DIAGNOSTICO", 200);
    mudarStatus(tokenOwnerA, nova, "ORCAMENTO", 200);
    completa(new Orc(nova, versao(tokenOwnerA, nova)));
    assertThat(identificar(tokenOwnerA).path("descartadas").asLong()).isEqualTo(1);
    assertThat(lista(tokenOwnerA).get(0).path("status").asText()).isEqualTo("DESCARTADA");
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
  }

  @Test
  void fluxoCompletoContatosResultadoResumoEAuditoria() throws Exception {
    JsonNode op = oportunidade();
    String id = op.path("id").asText();
    String os = op.path("origem").path("ordemServicoId").asText();
    op =
        put(
            OPS + "/" + id + "/responsavel",
            tokenAtendenteA,
            Map.of("responsavelId", atendenteA, "revisao", 0),
            200);
    op = contatar(op, "SEM_RESPOSTA");
    assertThat(op.path("status").asText()).isEqualTo("EM_CONTATO");
    op = contatar(op, "AGENDADO");
    assertThat(op.path("status").asText()).isEqualTo("AGENDADA");
    var result =
        Map.of(
            "valorRecuperado",
            1450,
            "ordemServicoId",
            os,
            "revisao",
            op.path("revisao").asLong(),
            "observacao",
            "Serviço contratado");
    op = post(OPS + "/" + id + "/resultados", tokenAtendenteA, result, 201);
    assertThat(op.path("status").asText()).isEqualTo("RECUPERADA");
    assertThat(op.path("proximoContatoEm").isNull()).isTrue();
    post(OPS + "/" + id + "/resultados", tokenOwnerA, result, 409);
    var detalhe = get(OPS + "/" + id, tokenOwnerA, 200);
    assertThat(detalhe.path("contatos")).hasSize(2);
    assertThat(detalhe.path("resultado").path("valorRecuperado").decimalValue())
        .isEqualByComparingTo("1450");
    assertThat(detalhe.path("resultado").path("usuarioId").asText())
        .isEqualTo(atendenteA.toString());
    assertThat(detalhe.path("auditoria").findValuesAsText("tipo"))
        .contains(
            "OPORTUNIDADE_CRIADA",
            "STATUS_ALTERADO",
            "CONTATO_REGISTRADO",
            "PROXIMO_CONTATO_ALTERADO",
            "RESPONSAVEL_ALTERADO",
            "RESULTADO_REGISTRADO",
            "VALOR_RECUPERADO",
            "ENCERRAMENTO");
    assertThat(detalhe.toString()).doesNotContain("oficinaId", "senha", "token_hash");
    var resumo = get(BASE + "/resumo", tokenOwnerA, 200);
    assertThat(resumo.path("valorRecuperado").decimalValue()).isEqualByComparingTo("1450");
    assertThat(resumo.path("quantidadeRecuperada").asLong()).isEqualTo(1);
    assertThat(resumo.path("taxaRecuperacao").decimalValue()).isEqualByComparingTo("100");
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
  }

  @Test
  void encerramentosETransicoesProibidas() throws Exception {
    var op = oportunidade();
    String url = OPS + "/" + op.path("id").asText();
    for (String status : List.of("RECUPERADA", "ABERTA", "PERDIDA", "AGENDADA"))
      post(url + "/status", tokenOwnerA, Map.of("status", status, "revisao", 0), 409);
    post(url + "/status", tokenOwnerA, Map.of("status", "DESCARTADA", "revisao", 0), 400);
    op = contatar(op, "NAO_INTERESSADO");
    assertThat(op.path("status").asText()).isEqualTo("EM_CONTATO");
    post(
        url + "/status",
        tokenOwnerA,
        Map.of(
            "status",
            "PERDIDA",
            "observacao",
            "Cliente desistiu",
            "revisao",
            op.path("revisao").asLong()),
        200);
    post(url + "/contatos", tokenOwnerA, contato(op.path("revisao").asLong(), "INTERESSADO"), 409);
    var segundo = orcamento(tokenOwnerA);
    RELOGIO.agora = publicar(tokenOwnerA, segundo).plusSeconds(8 * 86400);
    identificar(tokenOwnerA);
    var aberta = getP(OPS, tokenOwnerA, 200, "status", "ABERTA").path("itens").get(0);
    post(
        OPS + "/" + aberta.path("id").asText() + "/status",
        tokenOwnerA,
        Map.of("status", "DESCARTADA", "observacao", "Sem necessidade", "revisao", 0),
        200);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    var resumo = get(BASE + "/resumo", tokenOwnerA, 200);
    assertThat(resumo.path("quantidadePerdida").asLong()).isEqualTo(1);
    assertThat(resumo.path("oportunidadesAbertas").asLong()).isZero();
  }

  @Test
  void validacoesDeValoresDatasEEnums() throws Exception {
    var op = oportunidade();
    String url = OPS + "/" + op.path("id").asText();
    for (Object valor : List.of(-1, "1.001", "100000000000000000.00")) {
      var erro =
          post(
              url + "/resultados",
              tokenOwnerA,
              Map.of("valorRecuperado", valor, "revisao", 0),
              400);
      assertThat(erro.path("code").asText()).isEqualTo("VALIDATION_ERROR");
      assertThat(erro.path("errors")).isNotEmpty();
    }
    post(
        url + "/contatos",
        tokenOwnerA,
        Map.of("canal", "SMS", "resultado", "SEM_RESPOSTA", "revisao", 0),
        400);
    post(
        url + "/contatos",
        tokenOwnerA,
        Map.of("canal", "EMAIL", "resultado", "AGENDADO", "revisao", 0),
        400);
    put(
        url + "/proximo-contato",
        tokenOwnerA,
        Map.of("proximoContatoEm", RELOGIO.instant().minusSeconds(1).toString(), "revisao", 0),
        400);
    post(
        url + "/contatos",
        tokenOwnerA,
        Map.of(
            "canal",
            "EMAIL",
            "resultado",
            "SEM_RESPOSTA",
            "observacao",
            "x".repeat(4001),
            "revisao",
            0),
        400);
    post(url + "/resultados", tokenOwnerA, Map.of("valorRecuperado", 10), 400);
    assertThat(get(url, tokenOwnerA, 200).path("contatos")).isEmpty();
    op = contatar(op, "AGENDADO");
    op =
        put(
            url + "/proximo-contato",
            tokenOwnerA,
            Map.of("revisao", op.path("revisao").asLong()),
            200);
    assertThat(op.path("proximoContatoEm").isNull()).isTrue();
    assertThat(op.path("status").asText()).isEqualTo("EM_CONTATO");
  }

  @Test
  void isolamentoLeituraEscritaReferenciasERelatorios() throws Exception {
    var op = oportunidade();
    String id = op.path("id").asText(), url = OPS + "/" + id;
    assertThat(lista(tokenOwnerB)).isEmpty();
    get(url, tokenOwnerB, 404);
    post(url + "/contatos", tokenOwnerB, contato(0, "INTERESSADO"), 404);
    post(url + "/resultados", tokenOwnerB, Map.of("valorRecuperado", 10, "revisao", 0), 404);
    post(
        url + "/status",
        tokenOwnerB,
        Map.of("status", "DESCARTADA", "observacao", "Motivo", "revisao", 0),
        404);
    put(url + "/responsavel", tokenOwnerB, Map.of("responsavelId", ownerB, "revisao", 0), 404);
    put(url + "/proximo-contato", tokenOwnerB, Map.of("revisao", 0), 404);
    put(url + "/responsavel", tokenOwnerA, Map.of("responsavelId", ownerB, "revisao", 0), 404);
    for (UUID invalido : List.of(mecanicoA, inativoA))
      put(url + "/responsavel", tokenOwnerA, Map.of("responsavelId", invalido, "revisao", 0), 400);
    String osB = osCompleta(tokenOwnerB);
    post(
        url + "/resultados",
        tokenOwnerA,
        Map.of("valorRecuperado", 10, "ordemServicoId", osB, "revisao", 0),
        404);
    get("/api/v1/ordens-servico/" + osB + "/proxima-revisao", tokenOwnerA, 404);
    put(
        "/api/v1/ordens-servico/" + osB + "/proxima-revisao",
        tokenOwnerA,
        Map.of("data", hoje().toString(), "revisao", 0),
        404);
    assertThat(identificar(tokenOwnerB).path("criadas").asLong()).isZero();
    var resumo = get(BASE + "/resumo", tokenOwnerB, 200);
    assertThat(resumo.path("oportunidadesAbertas").asLong()).isZero();
    assertThat(resumo.path("valorRecuperado").decimalValue()).isEqualByComparingTo("0");
    assertThat(
            getP(OPS, tokenOwnerB, 200, "clienteId", op.path("cliente").path("id").asText())
                .path("total")
                .asLong())
        .isZero();
  }

  @Test
  void todasOperacoesRespeitamPapeisESessao() throws Exception {
    var op = oportunidade();
    String url = OPS + "/" + op.path("id").asText();
    for (String token : Arrays.asList(null, tokenMecanicoA)) {
      int code = token == null ? 401 : 403;
      get(OPS, token, code);
      get(url, token, code);
      get(BASE + "/resumo", token, code);
      post(BASE + "/identificar", token, Map.of(), code);
      post(url + "/contatos", token, contato(0, "SEM_RESPOSTA"), code);
      post(url + "/resultados", token, Map.of("valorRecuperado", 10, "revisao", 0), code);
      post(
          url + "/status",
          token,
          Map.of("status", "DESCARTADA", "observacao", "Motivo", "revisao", 0),
          code);
      put(url + "/responsavel", token, Map.of("revisao", 0), code);
      put(url + "/proximo-contato", token, Map.of("revisao", 0), code);
      String osUrl =
          "/api/v1/ordens-servico/"
              + op.path("origem").path("ordemServicoId").asText()
              + "/proxima-revisao";
      get(osUrl, token, code);
      put(osUrl, token, Map.of("revisao", 0), code);
      String vUrl =
          "/api/v1/orcamento-versoes/"
              + op.path("origem").path("orcamentoVersaoId").asText()
              + "/reavaliacao";
      get(vUrl, token, code);
      put(vUrl, token, Map.of("revisao", 0), code);
    }
    get(OPS, tokenAtendenteA, 200);
    get(url, tokenAtendenteA, 200);
    get(BASE + "/resumo", tokenAtendenteA, 200);
    identificar(tokenAtendenteA);
  }

  @Test
  void filtrosPaginacaoOrdenacaoEAgregacoes() throws Exception {
    var op = oportunidade();
    var segundo = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, segundo);
    var recusa = decidir(tokenOwnerA, segundo, false);
    RELOGIO.agora = recusa.plusSeconds(40 * 86400);
    identificar(tokenOwnerA);
    assertThat(getP(OPS, tokenOwnerA, 200, "tamanho", "1", "pagina", "1").path("itens")).hasSize(1);
    assertThat(getP(OPS, tokenOwnerA, 200, "tamanho", "1", "pagina", "2").path("itens")).isEmpty();
    assertThat(getP(OPS, tokenOwnerA, 200, "tamanho", "999").path("tamanho").asInt())
        .isEqualTo(100);
    for (String campo : List.of("clienteId", "veiculoId")) {
      String id = op.path(campo.equals("clienteId") ? "cliente" : "veiculo").path("id").asText();
      assertThat(getP(OPS, tokenOwnerA, 200, campo, id).path("total").asLong()).isEqualTo(1);
    }
    assertThat(getP(OPS, tokenOwnerA, 200, "tipo", "REAVALIACAO_PENDENTE").path("total").asLong())
        .isEqualTo(1);
    assertThat(getP(OPS, tokenOwnerA, 200, "faixaIdade", "DIAS_31_60").path("total").asLong())
        .isEqualTo(1);
    assertThat(
            getP(OPS, tokenOwnerA, 200, "de", RELOGIO.instant().minusSeconds(1).toString())
                .path("total")
                .asLong())
        .isEqualTo(1);
    assertThat(
            getP(OPS, tokenOwnerA, 200, "ate", op.path("criadoEm").asText()).path("total").asLong())
        .isEqualTo(1);
    for (String ordem :
        List.of(
            "criadoEm,desc",
            "elegivelDesde,asc",
            "proximoContatoEm,desc",
            "valorPotencial,asc",
            "status",
            "tipo",
            "id")) getP(OPS, tokenOwnerA, 200, "ordenacao", ordem);
    for (String ordem : List.of("senha", "id;drop table oficina", "tipo,xxx"))
      assertThat(getP(OPS, tokenOwnerA, 400, "ordenacao", ordem).path("code").asText())
          .isEqualTo("INVALID_REQUEST");
    getP(OPS, tokenOwnerA, 400, "de", "2031-01-01T00:00:00Z", "ate", "2030-01-01T00:00:00Z");
    getP(OPS, tokenOwnerA, 400, "tipo", "INVENTADO");
    var resumo = get(BASE + "/resumo", tokenOwnerA, 200);
    assertThat(resumo.path("oportunidadesAbertas").asLong()).isEqualTo(2);
    assertThat(resumo.path("valorPotencialConhecido").decimalValue()).isEqualByComparingTo("4000");
    assertThat(resumo.path("porTipo")).hasSize(2);
    assertThat(resumo.path("porIdade")).hasSize(2);
    assertThat(resumo.path("porResponsavel").get(0).path("chave").asText())
        .isEqualTo("SEM_RESPONSAVEL");
    assertThat(resumo.path("porPeriodo")).isNotEmpty();
  }

  @Test
  void identificacaoConcorrenteNaoDuplica() throws Exception {
    var o = orcamento(tokenOwnerA);
    RELOGIO.agora = publicar(tokenOwnerA, o).plusSeconds(8 * 86400);
    var respostas = paralelo(() -> identificar(tokenOwnerA), () -> identificar(tokenOwnerA));
    assertThat(respostas.stream().mapToLong(r -> r.path("criadas").asLong()).sum()).isEqualTo(1);
    assertThat(lista(tokenOwnerA)).hasSize(1);
  }

  @Test
  void contatosConcorrentesNaoPerdemHistoricoERevisaoProtege() throws Exception {
    var o = oportunidade();
    String url = OPS + "/" + o.path("id").asText() + "/contatos";
    var respostas =
        paralelo(
            () -> postSemStatus(url, contato(0, "SEM_RESPOSTA")),
            () -> postSemStatus(url, contato(0, "INTERESSADO")));
    assertThat(respostas.stream().mapToInt(r -> r.path("http").asInt()).sorted().toArray())
        .containsExactly(201, 409);
    var detalhe = get(OPS + "/" + o.path("id").asText(), tokenOwnerA, 200);
    assertThat(detalhe.path("contatos")).hasSize(1);
    contatar(detalhe.path("oportunidade"), "INTERESSADO");
    assertThat(get(OPS + "/" + o.path("id").asText(), tokenOwnerA, 200).path("contatos"))
        .hasSize(2);
  }

  @Test
  void recuperacoesConcorrentesRegistramUmUnicoValor() throws Exception {
    var o = oportunidade();
    String url = OPS + "/" + o.path("id").asText() + "/resultados";
    var respostas =
        paralelo(
            () -> postSemStatus(url, Map.of("valorRecuperado", 1450, "revisao", 0)),
            () -> postSemStatus(url, Map.of("valorRecuperado", 900, "revisao", 0)));
    assertThat(respostas.stream().mapToInt(r -> r.path("http").asInt()).sorted().toArray())
        .containsExactly(201, 409);
    var resumo = get(BASE + "/resumo", tokenOwnerA, 200);
    assertThat(resumo.path("quantidadeRecuperada").asLong()).isEqualTo(1);
    assertThat(resumo.path("valorRecuperado").decimalValue().stripTrailingZeros())
        .isIn(
            new java.math.BigDecimal("1450").stripTrailingZeros(),
            new java.math.BigDecimal("900").stripTrailingZeros());
  }

  JsonNode postSemStatus(String url, Object body) throws Exception {
    var r =
        mvc.perform(
                MockMvcRequestBuilders.post(url)
                    .header("Authorization", "Bearer " + tokenOwnerA)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andReturn();
    return json.createObjectNode().put("http", r.getResponse().getStatus());
  }

  List<JsonNode> paralelo(
      java.util.concurrent.Callable<JsonNode> a, java.util.concurrent.Callable<JsonNode> b)
      throws Exception {
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var inicio = new java.util.concurrent.CountDownLatch(1);
      var x =
          pool.submit(
              () -> {
                inicio.await();
                return a.call();
              });
      var y =
          pool.submit(
              () -> {
                inicio.await();
                return b.call();
              });
      inicio.countDown();
      return List.of(
          x.get(30, java.util.concurrent.TimeUnit.SECONDS),
          y.get(30, java.util.concurrent.TimeUnit.SECONDS));
    }
  }

  @Test
  void historicosImutaveisEConstraintsDeTenant() throws Exception {
    var o = contatar(oportunidade(), "INTERESSADO");
    String id = o.path("id").asText();
    for (String tabela : List.of("contato_oportunidade", "evento_oportunidade"))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "delete from " + tabela + " where oportunidade_id=?", UUID.fromString(id)))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update oportunidade_recuperacao set responsavel_id=? where id=?",
                    ownerB,
                    UUID.fromString(id)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update("delete from oportunidade_recuperacao where id=?", UUID.fromString(id)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void isolamentoDasTresOrigensEProgramacoes() throws Exception {
    var o = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, o);
    decidir(tokenOwnerA, o, false);
    String vUrl = "/api/v1/orcamento-versoes/" + o.versao() + "/reavaliacao";
    get(vUrl, tokenOwnerB, 404);
    put(vUrl, tokenOwnerB, Map.of("revisao", 0, "data", hoje().toString()), 404);
    String os = completa(orcamento(tokenOwnerA));
    programar(os, hoje());
    var esquecido = orcamento(tokenOwnerB);
    publicar(tokenOwnerB, esquecido);
    RELOGIO.agora = java.time.Instant.now().plusSeconds(40 * 86400);
    assertThat(identificar(tokenOwnerB).path("criadas").asLong()).isEqualTo(1);
    assertThat(lista(tokenOwnerB)).hasSize(1);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isEqualTo(2);
    assertThat(lista(tokenOwnerA).findValuesAsText("tipo"))
        .containsExactlyInAnyOrder("REVISAO_ATRASADA", "REAVALIACAO_PENDENTE");
    for (var op : lista(tokenOwnerA)) get(OPS + "/" + op.path("id").asText(), tokenOwnerB, 404);
    String bId = lista(tokenOwnerB).get(0).path("id").asText();
    get(OPS + "/" + bId, tokenOwnerA, 404);
    assertThat(
            get(BASE + "/resumo", tokenOwnerA, 200).path("valorPotencialConhecido").decimalValue())
        .isEqualByComparingTo("2000");
  }

  @Test
  void resumoPorEncerramentoNaoPorCriacaoEFormulaExcluiDescarte() throws Exception {
    var recuperada = oportunidade();
    var segundo = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, segundo);
    var terceiro = orcamento(tokenOwnerA);
    publicar(tokenOwnerA, terceiro);
    RELOGIO.agora = java.time.Instant.now().plusSeconds(9 * 86400);
    identificar(tokenOwnerA);
    var todas = lista(tokenOwnerA);
    var outras = new ArrayList<JsonNode>();
    for (var op : todas) if (!op.path("id").equals(recuperada.path("id"))) outras.add(op);
    var inicio = RELOGIO.instant().plusSeconds(86400);
    RELOGIO.agora = inicio;
    post(
        OPS + "/" + recuperada.path("id").asText() + "/resultados",
        tokenOwnerA,
        Map.of("valorRecuperado", 1450, "revisao", 0),
        201);
    var perdida = contatar(outras.get(0), "NAO_INTERESSADO");
    post(
        OPS + "/" + perdida.path("id").asText() + "/status",
        tokenOwnerA,
        Map.of(
            "status",
            "PERDIDA",
            "observacao",
            "Desistência",
            "revisao",
            perdida.path("revisao").asLong()),
        200);
    post(
        OPS + "/" + outras.get(1).path("id").asText() + "/status",
        tokenOwnerA,
        Map.of("status", "DESCARTADA", "observacao", "Duplicidade comercial", "revisao", 0),
        200);
    var resumo =
        getP(
            BASE + "/resumo",
            tokenOwnerA,
            200,
            "de",
            inicio.minusSeconds(1).toString(),
            "ate",
            inicio.plusSeconds(1).toString());
    assertThat(resumo.path("porTipo")).isEmpty();
    assertThat(resumo.path("quantidadeRecuperada").asLong()).isEqualTo(1);
    assertThat(resumo.path("quantidadePerdida").asLong()).isEqualTo(1);
    assertThat(resumo.path("taxaRecuperacao").decimalValue()).isEqualByComparingTo("50");
    assertThat(resumo.path("valorRecuperado").decimalValue()).isEqualByComparingTo("1450");
    assertThat(
            getP(BASE + "/resumo", tokenOwnerA, 200, "de", inicio.plusSeconds(1).toString())
                .path("valorRecuperado")
                .decimalValue())
        .isEqualByComparingTo("0");
  }

  @Test
  void filtrosResponsavelProximoContatoEIdadesNasFronteiras() throws Exception {
    var op = oportunidade();
    String id = op.path("id").asText();
    var elegivel = java.time.Instant.parse(op.path("elegivelDesde").asText());
    int[] dias = {0, 7, 8, 15, 16, 30, 31, 60, 61};
    String[] faixas = {
      "DIAS_0_7",
      "DIAS_0_7",
      "DIAS_8_15",
      "DIAS_8_15",
      "DIAS_16_30",
      "DIAS_16_30",
      "DIAS_31_60",
      "DIAS_31_60",
      "MAIS_60"
    };
    for (int i = 0; i < dias.length; i++) {
      RELOGIO.agora = elegivel.plusSeconds(dias[i] * 86400L);
      assertThat(getP(OPS, tokenOwnerA, 200, "faixaIdade", faixas[i]).path("total").asLong())
          .isEqualTo(1);
      assertThat(
              get(BASE + "/resumo", tokenOwnerA, 200)
                  .path("porIdade")
                  .get(0)
                  .path("chave")
                  .asText())
          .isEqualTo(faixas[i]);
    }
    op =
        put(
            OPS + "/" + id + "/responsavel",
            tokenOwnerA,
            Map.of("responsavelId", ownerA, "revisao", 0),
            200);
    op = contatar(op, "AGENDADO");
    String data = op.path("proximoContatoEm").asText();
    assertThat(
            getP(
                    OPS,
                    tokenOwnerA,
                    200,
                    "responsavelId",
                    ownerA.toString(),
                    "proximoContatoDe",
                    data,
                    "proximoContatoAte",
                    data)
                .path("total")
                .asLong())
        .isEqualTo(1);
    assertThat(
            getP(OPS, tokenOwnerA, 200, "responsavelId", ownerB.toString()).path("total").asLong())
        .isZero();
    assertThat(
            getP(OPS, tokenOwnerA, 200, "proximoContatoAte", RELOGIO.instant().toString())
                .path("total")
                .asLong())
        .isZero();
  }

  @Test
  void statusConcorrentesNaoSobrescrevemEncerramento() throws Exception {
    var o = oportunidade();
    String url = OPS + "/" + o.path("id").asText() + "/status";
    var respostas =
        paralelo(
            () ->
                postSemStatus(
                    url, Map.of("status", "DESCARTADA", "observacao", "Motivo", "revisao", 0)),
            () -> postSemStatus(url, Map.of("status", "EM_CONTATO", "revisao", 0)));
    assertThat(respostas.stream().mapToInt(r -> r.path("http").asInt()).sorted().toArray())
        .containsExactly(200, 409);
  }

  @Test
  void origemSubstituidaDescartadaEPublicacaoNovaEsperaSeteDias() throws Exception {
    var op = oportunidade();
    String os = op.path("origem").path("ordemServicoId").asText();
    String nova = versao(tokenOwnerA, os);
    assertThat(identificar(tokenOwnerA).path("descartadas").asLong()).isEqualTo(1);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    var pub = publicar(tokenOwnerA, new Orc(os, nova));
    RELOGIO.agora = pub.plusSeconds(7 * 86400 - 1);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
    RELOGIO.agora = pub.plusSeconds(7 * 86400);
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isEqualTo(1);
    assertThat(lista(tokenOwnerA)).hasSize(2);
  }

  @Test
  void atendimentoPosteriorAntesDaIdentificacaoNaoGeraRevisao() throws Exception {
    String anterior = completa(orcamento(tokenOwnerA));
    programar(anterior, hoje());
    String v =
        get("/api/v1/ordens-servico/" + anterior, tokenOwnerA, 200).path("veiculoId").asText();
    String nova = os(tokenOwnerA, v);
    mudarStatus(tokenOwnerA, nova, "DIAGNOSTICO", 200);
    mudarStatus(tokenOwnerA, nova, "ORCAMENTO", 200);
    completa(new Orc(nova, versao(tokenOwnerA, nova)));
    assertThat(identificar(tokenOwnerA).path("criadas").asLong()).isZero();
  }

  @Test
  void constraintsImpedemRecuperacaoSemResultadoEVinculoDeOrigemErrado() throws Exception {
    var op = oportunidade();
    UUID id = UUID.fromString(op.path("id").asText());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update oportunidade_recuperacao set status='RECUPERADA',encerrada_em=now() where id=?",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    UUID cliente = UUID.fromString(cliente(tokenOwnerA, "Outro cliente"));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update oportunidade_recuperacao set cliente_id=? where id=?", cliente, id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    post(
        OPS + "/" + id + "/resultados",
        tokenOwnerA,
        Map.of("valorRecuperado", 0, "revisao", 0),
        201);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update resultado_oportunidade set valor_recuperado=10 where oportunidade_id=?",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void migrationPreservaBaseV2ERecuperaSomentePublicacaoComprovada() throws Exception {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = jdbc.getDataSource().getConnection()) {
      var ds = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
      org.flywaydb.core.Flyway.configure()
          .dataSource(ds)
          .schemas(schema)
          .defaultSchema(schema)
          .target("2")
          .load()
          .migrate();
      connection.setSchema(schema);
      var db = new JdbcTemplate(ds);
      UUID t = UUID.randomUUID(),
          u = UUID.randomUUID(),
          c = UUID.randomUUID(),
          v = UUID.randomUUID(),
          os = UUID.randomUUID(),
          b = UUID.randomUUID(),
          v1 = UUID.randomUUID(),
          v2 = UUID.randomUUID();
      db.update("insert into oficina(id,nome,slug) values(?,'Upgrade','upgrade')", t);
      db.update(
          "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,'Owner','owner@test','hash','OWNER')",
          u,
          t);
      db.update(
          "insert into cliente(id,oficina_id,nome,telefone) values(?,?,'Cliente','123')", c, t);
      db.update(
          "insert into veiculo(id,oficina_id,cliente_id,placa,marca,modelo,ano,km,cor) values(?,?,?,'ABC1234','Fiat','Uno',2020,0,'Azul')",
          v,
          t,
          c);
      db.update(
          "insert into ordem_servico(id,oficina_id,numero,veiculo_id,cliente_id,status,km_entrada,relato) values(?,?,1,?,?,'ORCAMENTO',0,'Revisão')",
          os,
          t,
          v,
          c);
      db.update("insert into orcamento(id,oficina_id,ordem_servico_id) values(?,?,?)", b, t, os);
      connection.setAutoCommit(false);
      for (int n = 1; n <= 2; n++) {
        UUID id = n == 1 ? v1 : v2;
        db.update(
            "insert into orcamento_versao(id,oficina_id,orcamento_id,numero,autor_id,total,criado_em) values(?,?,?,?,?,100,cast(? as timestamptz))",
            id,
            t,
            b,
            n,
            u,
            "2026-01-0" + n + "T00:00:00Z");
        db.update(
            "insert into item_orcamento(id,oficina_id,orcamento_versao_id,tipo,descricao,quantidade,valor_unitario) values(?,?,?,'SERVICO','Revisão',1,100)",
            UUID.randomUUID(),
            t,
            id);
      }
      connection.commit();
      connection.setAutoCommit(true);
      db.update(
          "insert into evento_ordem_servico(id,oficina_id,ordem_servico_id,autor_id,origem,tipo,descricao,criado_em) values(?,?,?,?,'USUARIO','STATUS_ALTERADO','ORCAMENTO → AGUARDANDO_APROVACAO','2026-01-01T12:00:00Z')",
          UUID.randomUUID(),
          t,
          os,
          u);
      org.flywaydb.core.Flyway.configure()
          .dataSource(ds)
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      assertThat(
              db.queryForObject(
                      "select publicado_em from acompanhamento_orcamento where orcamento_versao_id=?",
                      java.sql.Timestamp.class,
                      v1)
                  .toInstant())
          .isEqualTo(java.time.Instant.parse("2026-01-01T12:00:00Z"));
      assertThat(
              db.queryForObject(
                  "select count(*) from acompanhamento_orcamento where orcamento_versao_id=?",
                  Integer.class,
                  v2))
          .isZero();
      assertThat(db.queryForObject("select count(*) from orcamento_versao", Integer.class))
          .isEqualTo(2);
      assertThatThrownBy(() -> db.update("update orcamento_versao set total=1 where id=?", v1))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  db.update(
                      "update acompanhamento_orcamento set publicado_em=now() where orcamento_versao_id=?",
                      v1))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      connection.setSchema("public");
    }
  }

  @Test
  void openApiDocumentaTodosOsNovosContratos() throws Exception {
    var api = get("/v3/api-docs", null, 200);
    var paths = api.path("paths");
    for (String suffix :
        List.of(
            "/identificar",
            "/oportunidades",
            "/oportunidades/{id}",
            "/oportunidades/{id}/contatos",
            "/oportunidades/{id}/resultados",
            "/oportunidades/{id}/status",
            "/oportunidades/{id}/responsavel",
            "/oportunidades/{id}/proximo-contato",
            "/resumo")) assertThat(paths.has(BASE + suffix)).isTrue();
    assertThat(paths.path(OPS).path("get").path("parameters").findValuesAsText("name"))
        .contains(
            "tipo",
            "status",
            "responsavelId",
            "de",
            "ate",
            "proximoContatoDe",
            "faixaIdade",
            "ordenacao",
            "pagina",
            "tamanho");
    var operacao = paths.path(OPS + "/{id}/resultados").path("post");
    assertThat(operacao.path("responses").has("201")).isTrue();
    assertThat(operacao.path("responses").has("409")).isTrue();
    String schema =
        operacao
            .path("requestBody")
            .path("content")
            .path("application/json")
            .path("schema")
            .path("$ref")
            .asText();
    assertThat(api.at(schema.substring(1)).path("properties").has("valorRecuperado")).isTrue();
  }
}
