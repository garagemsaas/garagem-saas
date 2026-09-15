package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.garagem.ordemservico.foto.application.FotoStorage;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Cobertura da Fase 2: contrato de erros, paginação, filtros, ordenação, dashboard, sessão, matriz
 * de permissões e isolamento entre oficinas nas novas consultas.
 *
 * <p>Roda contra PostgreSQL real, porque toda a proteção de tenant depende de chaves compostas e
 * das consultas de filtro. O armazenamento de fotos é substituído por um duplo em memória: o que
 * estes testes verificam é a autorização do upload e do download, não o backend S3, já exercitado
 * em {@code Fase1IT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(Fase2IT.StorageEmMemoria.class)
class Fase2IT {
  static PostgreSQLContainer<?> postgres;

  /** TTL propositalmente diferente do padrão, para provar que o valor vem de configuração. */
  static final long ACCESS_TTL_SEGUNDOS = 120;

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
    r.add("app.jwt.access-ttl", () -> "PT" + ACCESS_TTL_SEGUNDOS + "S");
    r.add("app.storage.access-key", () -> "test-user");
    r.add("app.storage.secret-key", () -> "test-only-storage-password");
    r.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
  }

  @AfterAll
  static void close() {
    if (postgres != null) postgres.stop();
  }

  @TestConfiguration
  static class StorageEmMemoria {
    @Bean
    @Primary
    FotoStorage fakeStorage() {
      return new FotoStorage() {
        private final Map<String, byte[]> data = new ConcurrentHashMap<>();

        public void put(String k, byte[] b, String t) {
          data.put(k, b);
        }

        public byte[] get(String k) {
          return Objects.requireNonNull(data.get(k));
        }

        public void delete(String k) {
          data.remove(k);
        }
      };
    }
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
  UUID ownerA, atendenteA, mecanicoA, mecanicoInativoA, ownerB, mecanicoB;
  String tokenOwnerA, tokenAtendenteA, tokenMecanicoA, tokenOwnerB;
  String osApoio;

  @BeforeEach
  void setup() throws Exception {
    if (hash == null) hash = encoder.encode(SENHA);
    osApoio = null;
    oficinaA = UUID.randomUUID();
    oficinaB = UUID.randomUUID();
    slugA = "a-" + oficinaA;
    slugB = "b-" + oficinaB;
    criarOficina(oficinaA, slugA);
    criarOficina(oficinaB, slugB);
    ownerA = criarUsuario(oficinaA, "owner@a.test", "OWNER", true);
    atendenteA = criarUsuario(oficinaA, "atendente@a.test", "ATENDENTE", true);
    mecanicoA = criarUsuario(oficinaA, "mecanico@a.test", "MECANICO", true);
    mecanicoInativoA = criarUsuario(oficinaA, "inativo@a.test", "MECANICO", false);
    ownerB = criarUsuario(oficinaB, "owner@b.test", "OWNER", true);
    mecanicoB = criarUsuario(oficinaB, "mecanico@b.test", "MECANICO", true);
    tokenOwnerA = token(slugA, "owner@a.test");
    tokenAtendenteA = token(slugA, "atendente@a.test");
    tokenMecanicoA = token(slugA, "mecanico@a.test");
    tokenOwnerB = token(slugB, "owner@b.test");
  }

  // ------------------------------------------------------------- fixtures e utilitários

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
    return post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", slug, "email", email, "senha", SENHA),
            200)
        .path("accessToken")
        .asText();
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

  /** GET com parâmetros fora da URL, para valores com espaço ou coringa chegarem sem reencode. */
  JsonNode getP(String path, String token, int esperado, String... kv) throws Exception {
    var req = MockMvcRequestBuilders.get(path);
    for (int i = 0; i < kv.length; i += 2) req.param(kv[i], kv[i + 1]);
    if (token != null) req.header("Authorization", "Bearer " + token);
    return corpo(mvc.perform(req).andExpect(status().is(esperado)).andReturn());
  }

  String cliente(String token, String nome, String telefone, String email) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("nome", nome);
    body.put("telefone", telefone);
    if (email != null) body.put("email", email);
    return post("/api/v1/clientes", token, body, 201).path("id").asText();
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

  String os(String token, String veiculoId, String previsaoEntrega) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("veiculoId", veiculoId);
    body.put("kmEntrada", 1000);
    body.put("relato", "Ruído no motor");
    if (previsaoEntrega != null) body.put("previsaoEntrega", previsaoEntrega);
    return post("/api/v1/ordens-servico", token, body, 201).path("id").asText();
  }

  /** Cria cliente, veículo e OS de uma vez, com identificadores únicos. */
  String osCompleta(String token) throws Exception {
    String c = cliente(token, "Cliente " + UUID.randomUUID(), "11999999999", null);
    return os(token, veiculo(token, c, placaUnica(), "Fiat", "Uno"), null);
  }

  static String placaUnica() {
    return "ABC" + String.format("%04d", RANDOM.nextInt(10000));
  }

  void mudarStatus(String token, String os, String status) throws Exception {
    long revisao = get("/api/v1/ordens-servico/" + os, token, 200).path("revisao").asLong();
    post(
        "/api/v1/ordens-servico/" + os + "/status",
        token,
        Map.of("status", status, "revisao", revisao),
        200);
  }

  /** Leva uma OS até AGUARDANDO_APROVACAO com uma versão de orçamento do valor pedido. */
  String osAguardandoAprovacao(String token, String valor) throws Exception {
    String os = osCompleta(token);
    mudarStatus(token, os, "DIAGNOSTICO");
    mudarStatus(token, os, "ORCAMENTO");
    post(
        "/api/v1/ordens-servico/" + os + "/orcamento/versoes",
        token,
        Map.of(
            "itens",
            List.of(
                Map.of(
                    "tipo", "SERVICO",
                    "descricao", "Revisão",
                    "quantidade", "1.000",
                    "valorUnitario", valor))),
        201);
    mudarStatus(token, os, "AGUARDANDO_APROVACAO");
    return os;
  }

  /** PNG mínimo válido, gerado na hora para não versionar binário no repositório. */
  static byte[] png() throws Exception {
    var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  // ------------------------------------------------------------------- Parte 3: erros

  @Test
  @DisplayName("Validação recusada devolve código estável e a lista de campos, sem detalhe interno")
  void validacaoDevolveCamposECodigo() throws Exception {
    var erro =
        post(
            "/api/v1/clientes",
            tokenOwnerA,
            Map.of("nome", "", "telefone", "11999999999", "email", "nao-e-email"),
            400);
    assertThat(erro.path("status").asInt()).isEqualTo(400);
    assertThat(erro.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(erro.path("timestamp").asText()).isNotBlank();
    assertThat(erro.path("requestId").asText()).isNotBlank();
    assertThat(erro.path("errors").findValuesAsText("field")).contains("nome", "email");
    erro.path("errors").forEach(campo -> assertThat(campo.path("message").asText()).isNotBlank());
    assertThat(erro.toString())
        .doesNotContain("Exception")
        .doesNotContain("br.com.garagem")
        .doesNotContain("select ");
  }

  @Test
  @DisplayName("Cada situação de falha tem o seu próprio status e código")
  void statusECodigoPorSituacao() throws Exception {
    // 401 sem token: escrito pelo filtro de segurança, não pelo @ControllerAdvice, e ainda assim
    // no mesmo formato.
    assertThat(get("/api/v1/clientes", null, 401).path("code").asText()).isEqualTo("UNAUTHORIZED");

    assertThat(
            post(
                    "/api/v1/clientes",
                    tokenMecanicoA,
                    Map.of("nome", "Alguém", "telefone", "11999999999"),
                    403)
                .path("code")
                .asText())
        .isEqualTo("FORBIDDEN");

    assertThat(get("/api/v1/clientes/" + UUID.randomUUID(), tokenOwnerA, 404).path("code").asText())
        .isEqualTo("NOT_FOUND");

    String id = cliente(tokenOwnerA, "Original", "11999999999", null);
    assertThat(
            put(
                    "/api/v1/clientes/" + id,
                    tokenOwnerA,
                    Map.of("nome", "Outro", "telefone", "11999999999", "revisao", 99),
                    409)
                .path("code")
                .asText())
        .isEqualTo("CONFLICT");

    // Duplicidade detectada pelo banco vira 409, não 500.
    assertThat(
            post(
                    "/api/v1/usuarios",
                    tokenOwnerA,
                    Map.of(
                        "nome", "Repetido",
                        "email", "owner@a.test",
                        "senha", SENHA,
                        "papel", "ATENDENTE"),
                    409)
                .path("code")
                .asText())
        .isEqualTo("DUPLICATE");

    var metodo =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.delete("/api/v1/clientes/" + id)
                        .header("Authorization", "Bearer " + tokenOwnerA))
                .andExpect(status().is(405))
                .andReturn());
    assertThat(metodo.path("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");

    // Corpo ilegível não vaza a mensagem do parser.
    var malformado =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokenOwnerA)
                        .contentType("application/json")
                        .content("{\"nome\":"))
                .andExpect(status().is(400))
                .andReturn());
    assertThat(malformado.path("code").asText()).isEqualTo("INVALID_REQUEST");
    assertThat(malformado.toString()).doesNotContain("JsonParseException").doesNotContain("line:");
  }

  @Test
  @DisplayName("Toda resposta de erro chega como application/problem+json")
  void erroSempreUsaProblemJson() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/v1/clientes"))
        .andExpect(status().is(401))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    mvc.perform(
            MockMvcRequestBuilders.get("/api/v1/clientes/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + tokenOwnerA))
        .andExpect(status().is(404))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // --------------------------------------------------------------- Parte 4: paginação

  @Test
  @DisplayName("Paginação começa em zero, usa 20 por padrão e não passa de 100 por página")
  void paginacaoTemPadraoETeto() throws Exception {
    for (int i = 0; i < 25; i++)
      cliente(
          tokenOwnerA,
          String.format("Cliente %02d", i),
          "1199999" + String.format("%04d", i),
          null);

    var padrao = get("/api/v1/clientes", tokenOwnerA, 200);
    assertThat(padrao.path("pagina").asInt()).isZero();
    assertThat(padrao.path("tamanho").asInt()).isEqualTo(20);
    assertThat(padrao.path("total").asLong()).isEqualTo(25);
    assertThat(padrao.path("totalPaginas").asInt()).isEqualTo(2);
    assertThat(padrao.path("itens")).hasSize(20);

    assertThat(get("/api/v1/clientes?pagina=1", tokenOwnerA, 200).path("itens")).hasSize(5);

    // Não existe listagem ilimitada: o tamanho é aparado no teto e no piso.
    assertThat(get("/api/v1/clientes?tamanho=100000", tokenOwnerA, 200).path("tamanho").asInt())
        .isEqualTo(100);
    assertThat(get("/api/v1/clientes?tamanho=0", tokenOwnerA, 200).path("tamanho").asInt())
        .isEqualTo(1);
    assertThat(get("/api/v1/clientes?pagina=-5", tokenOwnerA, 200).path("pagina").asInt()).isZero();

    // Página além do fim responde vazia, não erro.
    assertThat(get("/api/v1/clientes?pagina=50", tokenOwnerA, 200).path("itens")).isEmpty();
  }

  @Test
  @DisplayName("Todas as listagens usam exatamente o mesmo envelope de página")
  void envelopeDePaginaEIgualEmTodaListagem() throws Exception {
    for (String path :
        List.of(
            "/api/v1/clientes", "/api/v1/veiculos", "/api/v1/ordens-servico", "/api/v1/usuarios")) {
      var pagina = get(path, tokenOwnerA, 200);
      assertThat(pagina.fieldNames())
          .toIterable()
          .describedAs(path)
          .containsExactlyInAnyOrder("itens", "pagina", "tamanho", "total", "totalPaginas");
    }
  }

  // --------------------------------------------------------------- Parte 6: ordenação

  @Test
  @DisplayName("Ordenação aceita apenas campos da allowlist e recusa o resto com 400")
  void ordenacaoRespeitaAllowlist() throws Exception {
    String zulmira = cliente(tokenOwnerA, "Zulmira", "11999999901", null);
    String amanda = cliente(tokenOwnerA, "Amanda", "11999999902", null);
    String marcos = cliente(tokenOwnerA, "Marcos", "11999999903", null);
    // Datas distintas e explícitas, para a ordem padrão não depender do relógio.
    datar(zulmira, "2026-01-01");
    datar(amanda, "2026-02-01");
    datar(marcos, "2026-03-01");

    assertThat(
            get("/api/v1/clientes?ordenacao=nome,asc", tokenOwnerA, 200).findValuesAsText("nome"))
        .containsExactly("Amanda", "Marcos", "Zulmira");
    assertThat(
            get("/api/v1/clientes?ordenacao=nome,desc", tokenOwnerA, 200).findValuesAsText("nome"))
        .containsExactly("Zulmira", "Marcos", "Amanda");
    // Sem direção explícita, ascendente.
    assertThat(get("/api/v1/clientes?ordenacao=nome", tokenOwnerA, 200).findValuesAsText("nome"))
        .containsExactly("Amanda", "Marcos", "Zulmira");
    // Sem ordenação, o padrão do projeto: mais recente primeiro.
    assertThat(get("/api/v1/clientes", tokenOwnerA, 200).findValuesAsText("nome"))
        .containsExactly("Marcos", "Amanda", "Zulmira");

    // Nada que o cliente envie vira campo de ordenação sem passar pela allowlist.
    for (String invalido :
        List.of("senhaHash", "oficinaId", "id;drop table cliente", "(select 1)", "nome desc"))
      assertThat(
              getP("/api/v1/clientes", tokenOwnerA, 400, "ordenacao", invalido)
                  .path("code")
                  .asText())
          .describedAs(invalido)
          .isEqualTo("INVALID_REQUEST");
    assertThat(get("/api/v1/clientes?ordenacao=nome,cima", tokenOwnerA, 400).path("code").asText())
        .isEqualTo("INVALID_REQUEST");
    // Os três clientes continuam lá: nenhuma das tentativas chegou ao banco.
    assertThat(get("/api/v1/clientes", tokenOwnerA, 200).path("total").asLong()).isEqualTo(3);
  }

  void datar(String clienteId, String dia) {
    jdbc.update(
        "update cliente set criado_em = ?::timestamptz where id=?",
        dia + "T12:00:00Z",
        UUID.fromString(clienteId));
  }

  @Test
  @DisplayName("Cada listagem tem a sua própria allowlist de ordenação")
  void allowlistDeOrdenacaoPorRecurso() throws Exception {
    get("/api/v1/veiculos?ordenacao=placa,asc", tokenOwnerA, 200);
    get("/api/v1/veiculos?ordenacao=km,desc", tokenOwnerA, 200);
    get("/api/v1/veiculos?ordenacao=numero,asc", tokenOwnerA, 400);
    get("/api/v1/ordens-servico?ordenacao=numero,desc", tokenOwnerA, 200);
    get("/api/v1/ordens-servico?ordenacao=placa,asc", tokenOwnerA, 400);
    get("/api/v1/usuarios?ordenacao=papel,asc", tokenOwnerA, 200);
    get("/api/v1/usuarios?ordenacao=senha,asc", tokenOwnerA, 400);
  }

  @Test
  @DisplayName("Ordenação por número devolve as OS na ordem pedida")
  void ordenacaoDeOsPorNumero() throws Exception {
    osCompleta(tokenOwnerA);
    osCompleta(tokenOwnerA);
    osCompleta(tokenOwnerA);
    var crescente =
        get("/api/v1/ordens-servico?ordenacao=numero,asc", tokenOwnerA, 200).findValues("numero");
    assertThat(crescente).hasSize(3);
    assertThat(crescente.stream().map(JsonNode::asLong).toList()).isSorted();
    var decrescente =
        get("/api/v1/ordens-servico?ordenacao=numero,desc", tokenOwnerA, 200).findValues("numero");
    assertThat(decrescente.stream().map(JsonNode::asLong).toList())
        .isSortedAccordingTo(Comparator.reverseOrder());
  }

  // ------------------------------------------------------------------ Parte 5: filtros

  @Test
  @DisplayName("Filtros de cliente combinam entre si e tratam coringa como texto literal")
  void filtrosDeCliente() throws Exception {
    cliente(tokenOwnerA, "Ana Paula", "11911110000", "ana@exemplo.test");
    cliente(tokenOwnerA, "Ana Clara", "11922220000", "clara@exemplo.test");
    cliente(tokenOwnerA, "Bruno Dias", "11933330000", "bruno@outro.test");

    assertThat(getP("/api/v1/clientes", tokenOwnerA, 200, "nome", "ana").path("total").asLong())
        .isEqualTo(2);
    assertThat(
            getP("/api/v1/clientes", tokenOwnerA, 200, "telefone", "9222").findValuesAsText("nome"))
        .containsExactly("Ana Clara");
    assertThat(
            getP("/api/v1/clientes", tokenOwnerA, 200, "email", "exemplo.test")
                .path("total")
                .asLong())
        .isEqualTo(2);
    // Filtros combinam com E lógico.
    assertThat(
            getP("/api/v1/clientes", tokenOwnerA, 200, "nome", "ana", "email", "clara")
                .findValuesAsText("nome"))
        .containsExactly("Ana Clara");
    // A busca única varre nome, telefone e e-mail.
    assertThat(
            getP("/api/v1/clientes", tokenOwnerA, 200, "busca", "outro.test")
                .findValuesAsText("nome"))
        .containsExactly("Bruno Dias");
    // Coringa enviado pelo cliente é dado, não sintaxe de SQL.
    assertThat(getP("/api/v1/clientes", tokenOwnerA, 200, "nome", "%").path("total").asLong())
        .isZero();
    assertThat(getP("/api/v1/clientes", tokenOwnerA, 200, "nome", "_").path("total").asLong())
        .isZero();
    assertThat(getP("/api/v1/clientes", tokenOwnerA, 200, "busca", "%").path("total").asLong())
        .isZero();
  }

  @Test
  @DisplayName("Filtros de veículo cobrem placa, cliente, marca e modelo")
  void filtrosDeVeiculo() throws Exception {
    String ana = cliente(tokenOwnerA, "Ana", "11911110000", null);
    String bruno = cliente(tokenOwnerA, "Bruno", "11922220000", null);
    veiculo(tokenOwnerA, ana, "AAA1111", "Fiat", "Uno");
    veiculo(tokenOwnerA, ana, "BBB2222", "Fiat", "Palio");
    veiculo(tokenOwnerA, bruno, "CCC3333", "Ford", "Ka");

    assertThat(get("/api/v1/veiculos?clienteId=" + ana, tokenOwnerA, 200).path("total").asLong())
        .isEqualTo(2);
    assertThat(get("/api/v1/veiculos?marca=fiat", tokenOwnerA, 200).path("total").asLong())
        .isEqualTo(2);
    assertThat(get("/api/v1/veiculos?modelo=ka", tokenOwnerA, 200).findValuesAsText("placa"))
        .containsExactly("CCC3333");
    // A placa é comparada sem separadores, como é gravada.
    assertThat(get("/api/v1/veiculos?placa=bbb-2222", tokenOwnerA, 200).findValuesAsText("placa"))
        .containsExactly("BBB2222");
    assertThat(get("/api/v1/veiculos?placa=CCC", tokenOwnerA, 200).path("total").asLong())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Filtros de OS cobrem número, status, responsável, veículo e período")
  void filtrosDeOrdemServico() throws Exception {
    String c = cliente(tokenOwnerA, "Cliente Filtro", "11944440000", null);
    String v1 = veiculo(tokenOwnerA, c, "DDD4444", "Fiat", "Uno");
    String v2 = veiculo(tokenOwnerA, c, "EEE5555", "Ford", "Ka");
    String os1 = os(tokenOwnerA, v1, null);
    String os2 = os(tokenOwnerA, v2, null);
    long numero1 = get("/api/v1/ordens-servico/" + os1, tokenOwnerA, 200).path("numero").asLong();
    mudarStatus(tokenOwnerA, os2, "DIAGNOSTICO");
    long revisao = get("/api/v1/ordens-servico/" + os1, tokenOwnerA, 200).path("revisao").asLong();
    put(
        "/api/v1/ordens-servico/" + os1 + "/responsavel",
        tokenOwnerA,
        Map.of("mecanicoId", mecanicoA.toString(), "revisao", revisao),
        200);

    assertThat(
            get("/api/v1/ordens-servico?numero=" + numero1, tokenOwnerA, 200)
                .findValuesAsText("id"))
        .containsExactly(os1);
    assertThat(
            get("/api/v1/ordens-servico?status=DIAGNOSTICO", tokenOwnerA, 200)
                .findValuesAsText("id"))
        .containsExactly(os2);
    assertThat(
            get("/api/v1/ordens-servico?mecanicoId=" + mecanicoA, tokenOwnerA, 200)
                .findValuesAsText("id"))
        .containsExactly(os1);
    assertThat(
            get("/api/v1/ordens-servico?veiculoId=" + v2, tokenOwnerA, 200).findValuesAsText("id"))
        .containsExactly(os2);
    assertThat(get("/api/v1/ordens-servico?placa=EEE", tokenOwnerA, 200).findValuesAsText("id"))
        .containsExactly(os2);
    assertThat(
            get("/api/v1/ordens-servico?clienteId=" + c, tokenOwnerA, 200).path("total").asLong())
        .isEqualTo(2);
    // Combinação de filtros.
    assertThat(
            get("/api/v1/ordens-servico?clienteId=" + c + "&status=RECEBIDO", tokenOwnerA, 200)
                .findValuesAsText("id"))
        .containsExactly(os1);

    // Período sobre a data de abertura.
    assertThat(
            get("/api/v1/ordens-servico?de=2000-01-01T00:00:00Z", tokenOwnerA, 200)
                .path("total")
                .asLong())
        .isEqualTo(2);
    assertThat(
            get("/api/v1/ordens-servico?ate=2000-01-01T00:00:00Z", tokenOwnerA, 200)
                .path("total")
                .asLong())
        .isZero();

    // Valores inválidos são recusados, não ignorados em silêncio.
    assertThat(
            get("/api/v1/ordens-servico?status=INVENTADO", tokenOwnerA, 400).path("code").asText())
        .isEqualTo("INVALID_REQUEST");
    get("/api/v1/ordens-servico?numero=abc", tokenOwnerA, 400);
    get("/api/v1/ordens-servico?de=ontem", tokenOwnerA, 400);
    get("/api/v1/ordens-servico?clienteId=nao-e-uuid", tokenOwnerA, 400);
  }

  @Test
  @DisplayName("Equipe pode ser filtrada por papel e por situação de acesso")
  void filtrosDeUsuario() throws Exception {
    assertThat(get("/api/v1/usuarios?papel=MECANICO", tokenOwnerA, 200).path("total").asLong())
        .isEqualTo(2);
    assertThat(
            get("/api/v1/usuarios?papel=MECANICO&ativo=true", tokenOwnerA, 200)
                .findValuesAsText("id"))
        .containsExactly(mecanicoA.toString());
    assertThat(get("/api/v1/usuarios?ativo=false", tokenOwnerA, 200).findValuesAsText("id"))
        .containsExactly(mecanicoInativoA.toString());
    // Nem a listagem completa expõe qualquer resquício de credencial.
    assertThat(get("/api/v1/usuarios", tokenOwnerA, 200).toString())
        .doesNotContain("senha")
        .doesNotContain("$2a$");
  }

  // ---------------------------------------------------------------- Parte 7: dashboard

  @Test
  @DisplayName("Dashboard resume a operação sem obrigar o frontend a baixar todas as OS")
  void dashboardResumeAOperacao() throws Exception {
    osCompleta(tokenOwnerA);
    String comDiagnostico = osCompleta(tokenOwnerA);
    mudarStatus(tokenOwnerA, comDiagnostico, "DIAGNOSTICO");
    osAguardandoAprovacao(tokenOwnerA, "250.00");

    // Uma OS atrasada e sem responsável.
    os(
        tokenOwnerA,
        veiculo(
            tokenOwnerA,
            cliente(tokenOwnerA, "Atrasado", "11955550000", null),
            placaUnica(),
            "Fiat",
            "Uno"),
        "2020-01-01T00:00:00Z");

    // Uma OS já entregue, dentro da janela de sete dias.
    String pronta = osCompleta(tokenOwnerA);
    jdbc.update(
        "update ordem_servico set status='PRONTO', concluida_em=now() where id=? and oficina_id=?",
        UUID.fromString(pronta),
        oficinaA);

    var painel = get("/api/v1/dashboard", tokenOwnerA, 200);
    assertThat(painel.path("geradoEm").asText()).isNotBlank();
    // Todos os status aparecem, inclusive zerados, para a tela não precisar adivinhar chaves.
    assertThat(painel.path("porStatus").size()).isEqualTo(8);
    assertThat(painel.path("porStatus").path("RECEBIDO").asLong()).isEqualTo(2);
    assertThat(painel.path("porStatus").path("DIAGNOSTICO").asLong()).isEqualTo(1);
    assertThat(painel.path("porStatus").path("AGUARDANDO_APROVACAO").asLong()).isEqualTo(1);
    assertThat(painel.path("porStatus").path("PRONTO").asLong()).isEqualTo(1);
    assertThat(painel.path("porStatus").path("TESTE").asLong()).isZero();
    assertThat(painel.path("emAndamento").asLong()).isEqualTo(4);
    assertThat(painel.path("prontas").asLong()).isEqualTo(1);
    assertThat(painel.path("concluidasSeteDias").asLong()).isEqualTo(1);
    assertThat(painel.path("entradasHoje").asLong()).isEqualTo(5);
    assertThat(painel.path("atrasadas").asLong()).isEqualTo(1);
    assertThat(painel.path("semResponsavel").asLong()).isEqualTo(4);
    assertThat(painel.path("orcamentosAguardandoDecisao").path("quantidade").asLong()).isEqualTo(1);
    assertThat(painel.path("orcamentosAguardandoDecisao").path("total").asDouble())
        .isEqualTo(250.0);
  }

  @Test
  @DisplayName("Orçamento já decidido sai da fila de decisões pendentes")
  void dashboardIgnoraOrcamentoJaDecidido() throws Exception {
    String os = osAguardandoAprovacao(tokenOwnerA, "400.00");
    assertThat(
            get("/api/v1/dashboard", tokenOwnerA, 200)
                .path("orcamentosAguardandoDecisao")
                .path("quantidade")
                .asLong())
        .isEqualTo(1);

    String token =
        post("/api/v1/ordens-servico/" + os + "/links", tokenOwnerA, Map.of(), 201)
            .path("token")
            .asText();
    String versao =
        get("/api/v1/ordens-servico/" + os + "/orcamento/versoes", tokenOwnerA, 200)
            .get(0)
            .path("id")
            .asText();
    post(
        "/api/v1/publico/" + token + "/decisao",
        null,
        Map.of("versaoId", versao, "aprovado", true),
        200);

    var painel = get("/api/v1/dashboard", tokenOwnerA, 200);
    assertThat(painel.path("orcamentosAguardandoDecisao").path("quantidade").asLong()).isZero();
    assertThat(painel.path("orcamentosAguardandoDecisao").path("total").asDouble()).isZero();
    assertThat(painel.path("porStatus").path("EM_MANUTENCAO").asLong()).isEqualTo(1);
  }

  @Test
  @DisplayName("Dashboard só enxerga a própria oficina e recusa fuso inventado")
  void dashboardEIsoladoPorOficina() throws Exception {
    osCompleta(tokenOwnerA);
    osCompleta(tokenOwnerA);
    osCompleta(tokenOwnerB);

    assertThat(get("/api/v1/dashboard", tokenOwnerA, 200).path("emAndamento").asLong())
        .isEqualTo(2);
    assertThat(get("/api/v1/dashboard", tokenOwnerB, 200).path("emAndamento").asLong())
        .isEqualTo(1);
    // Mecânico também acompanha o painel.
    assertThat(get("/api/v1/dashboard", tokenMecanicoA, 200).path("emAndamento").asLong())
        .isEqualTo(2);
    get("/api/v1/dashboard", null, 401);
    assertThat(get("/api/v1/dashboard?fuso=Marte/Olympus", tokenOwnerA, 400).path("code").asText())
        .isEqualTo("INVALID_REQUEST");
    get("/api/v1/dashboard?fuso=UTC", tokenOwnerA, 200);
  }

  // ----------------------------------------------------------- Parte 22: multi-tenancy

  @Test
  @DisplayName("Oficina A não alcança nenhum recurso da oficina B, nem por id nem por filtro")
  void isolamentoEntreOficinas() throws Exception {
    String clienteB = cliente(tokenOwnerB, "Cliente da B", "11966660000", "b@exemplo.test");
    String veiculoB = veiculo(tokenOwnerB, clienteB, "ZZZ9999", "Honda", "Civic");
    String osB = os(tokenOwnerB, veiculoB, null);
    post(
        "/api/v1/ordens-servico/" + osB + "/checklist",
        tokenOwnerB,
        Map.of("itens", List.of(Map.of("descricao", "Pneus", "condicao", "Bom"))),
        201);
    post(
        "/api/v1/ordens-servico/" + osB + "/diagnosticos",
        tokenOwnerB,
        Map.of("descricao", "Correia gasta", "classificacao", "VERMELHO"),
        201);
    String fotoB =
        corpo(
                mvc.perform(
                        multipart("/api/v1/ordens-servico/" + osB + "/fotos")
                            .file(new MockMultipartFile("arquivo", "f.png", "image/png", png()))
                            .param("finalidade", "ENTRADA")
                            .header("Authorization", "Bearer " + tokenOwnerB))
                    .andExpect(status().is(201))
                    .andReturn())
            .path("id")
            .asText();

    // Leitura direta por id responde 404, o mesmo que um id inexistente: A não descobre nem que o
    // registro existe.
    get("/api/v1/clientes/" + clienteB, tokenOwnerA, 404);
    get("/api/v1/veiculos/" + veiculoB, tokenOwnerA, 404);
    get("/api/v1/ordens-servico/" + osB, tokenOwnerA, 404);
    get("/api/v1/ordens-servico/" + osB + "/checklist", tokenOwnerA, 404);
    get("/api/v1/ordens-servico/" + osB + "/diagnosticos", tokenOwnerA, 404);
    get("/api/v1/ordens-servico/" + osB + "/orcamento/versoes", tokenOwnerA, 404);
    get("/api/v1/ordens-servico/" + osB + "/timeline", tokenOwnerA, 404);
    get("/api/v1/ordens-servico/" + osB + "/fotos", tokenOwnerA, 404);
    get("/api/v1/ordens-servico/" + osB + "/fotos/" + fotoB + "/conteudo", tokenOwnerA, 404);

    // Escrita em recurso alheio.
    put(
        "/api/v1/clientes/" + clienteB,
        tokenOwnerA,
        Map.of("nome", "Invadido", "telefone", "11900000000", "revisao", 0),
        404);
    post(
        "/api/v1/ordens-servico/" + osB + "/diagnosticos",
        tokenOwnerA,
        Map.of("descricao", "Invadido", "classificacao", "VERDE"),
        404);
    post("/api/v1/ordens-servico/" + osB + "/links", tokenOwnerA, Map.of(), 404);

    // Associação de recurso alheio: veículo de A não aceita cliente de B.
    post(
        "/api/v1/veiculos",
        tokenOwnerA,
        Map.of(
            "clienteId",
            clienteB,
            "placa",
            placaUnica(),
            "marca",
            "Fiat",
            "modelo",
            "Uno",
            "ano",
            2020,
            "km",
            0,
            "cor",
            "Preto"),
        404);

    // Mecânico de B não pode ser responsável por OS de A.
    String osA = osCompleta(tokenOwnerA);
    long revisao = get("/api/v1/ordens-servico/" + osA, tokenOwnerA, 200).path("revisao").asLong();
    put(
        "/api/v1/ordens-servico/" + osA + "/responsavel",
        tokenOwnerA,
        Map.of("mecanicoId", mecanicoB.toString(), "revisao", revisao),
        404);

    // Filtros com id da outra oficina devolvem vazio, nunca o registro alheio.
    assertThat(
            get("/api/v1/veiculos?clienteId=" + clienteB, tokenOwnerA, 200).path("total").asLong())
        .isZero();
    assertThat(
            get("/api/v1/ordens-servico?clienteId=" + clienteB, tokenOwnerA, 200)
                .path("total")
                .asLong())
        .isZero();
    assertThat(
            get("/api/v1/ordens-servico?mecanicoId=" + mecanicoB, tokenOwnerA, 200)
                .path("total")
                .asLong())
        .isZero();
    assertThat(get("/api/v1/veiculos?placa=ZZZ", tokenOwnerA, 200).path("total").asLong()).isZero();
    assertThat(
            getP("/api/v1/clientes", tokenOwnerA, 200, "busca", "Cliente da B")
                .path("total")
                .asLong())
        .isZero();
    assertThat(get("/api/v1/usuarios", tokenOwnerA, 200).toString())
        .doesNotContain(ownerB.toString())
        .doesNotContain(mecanicoB.toString());
    // O painel de A também não soma nada de B.
    assertThat(
            get("/api/v1/dashboard", tokenOwnerA, 200).path("porStatus").path("RECEBIDO").asLong())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("O mesmo isolamento vale no sentido inverso, de B para A")
  void isolamentoNoSentidoInverso() throws Exception {
    String clienteA = cliente(tokenOwnerA, "Cliente da A", "11977770000", null);
    String veiculoA = veiculo(tokenOwnerA, clienteA, "YYY8888", "Fiat", "Mobi");
    String osA = os(tokenOwnerA, veiculoA, null);

    get("/api/v1/clientes/" + clienteA, tokenOwnerB, 404);
    get("/api/v1/veiculos/" + veiculoA, tokenOwnerB, 404);
    get("/api/v1/ordens-servico/" + osA, tokenOwnerB, 404);
    assertThat(get("/api/v1/ordens-servico", tokenOwnerB, 200).path("total").asLong()).isZero();
    assertThat(
            getP("/api/v1/clientes", tokenOwnerB, 200, "nome", "Cliente da A")
                .path("total")
                .asLong())
        .isZero();
    post(
        "/api/v1/ordens-servico",
        tokenOwnerB,
        Map.of("veiculoId", veiculoA, "kmEntrada", 2000, "relato", "Tentativa"),
        404);
  }

  // -------------------------------------------------------------- Parte 23: permissões

  /** Uma linha da matriz de permissões: a operação e os papéis que podem executá-la. */
  record Regra(String operacao, Set<String> permitidos) {}

  @Test
  @DisplayName("Matriz de permissões por papel corresponde ao que a API realmente faz")
  void matrizDePermissoes() throws Exception {
    String clienteId = cliente(tokenOwnerA, "Base", "11988880000", null);
    String veiculoId = veiculo(tokenOwnerA, clienteId, placaUnica(), "Fiat", "Uno");
    Map<String, String> tokens =
        Map.of("OWNER", tokenOwnerA, "ATENDENTE", tokenAtendenteA, "MECANICO", tokenMecanicoA);

    Set<String> escritorio = Set.of("OWNER", "ATENDENTE");
    Set<String> todos = Set.of("OWNER", "ATENDENTE", "MECANICO");
    Set<String> oficina = Set.of("OWNER", "MECANICO");

    List<Regra> regras =
        List.of(
            new Regra("criarCliente", escritorio),
            new Regra("atualizarCliente", escritorio),
            new Regra("criarVeiculo", escritorio),
            new Regra("abrirOs", escritorio),
            new Regra("atribuirResponsavel", escritorio),
            new Regra("criarVersaoOrcamento", escritorio),
            new Regra("emitirLink", escritorio),
            new Regra("criarUsuario", Set.of("OWNER")),
            new Regra("criarDiagnostico", oficina),
            new Regra("mudarStatus", todos),
            new Regra("registrarChecklist", todos),
            new Regra("enviarFoto", todos),
            new Regra("listarClientes", todos),
            new Regra("listarOs", todos),
            new Regra("listarEquipe", todos),
            new Regra("verDashboard", todos));

    for (Regra regra : regras)
      for (var papel : tokens.entrySet()) {
        boolean permitido = regra.permitidos().contains(papel.getKey());
        assertThat(executar(regra.operacao(), papel.getValue(), clienteId, veiculoId))
            .describedAs("%s como %s", regra.operacao(), papel.getKey())
            .isEqualTo(permitido ? 0 : 403);
      }

    // Dono da outra oficina passa pela autorização de papel, mas não alcança o dado de A.
    assertThat(executar("listarClientes", tokenOwnerB, clienteId, veiculoId)).isZero();
    assertThat(executar("atualizarCliente", tokenOwnerB, clienteId, veiculoId)).isEqualTo(404);
    // Sem sessão nenhuma operação passa.
    assertThat(executar("listarClientes", null, clienteId, veiculoId)).isEqualTo(401);
  }

  /** OS descartável para as operações da matriz que precisam de uma OS já existente. */
  String osDeApoio() throws Exception {
    if (osApoio == null) osApoio = osCompleta(tokenOwnerA);
    return osApoio;
  }

  /**
   * Executa a operação e devolve 0 quando a autorização passou. O resultado de negócio — 200, 201,
   * 409 por revisão ou etapa — não interessa aqui; interessa apenas quem foi barrado, e por quê.
   */
  int executar(String operacao, String token, String clienteId, String veiculoId) throws Exception {
    MockHttpServletRequestBuilder req;
    Object body = null;
    switch (operacao) {
      case "criarCliente" -> {
        req = MockMvcRequestBuilders.post("/api/v1/clientes");
        body = Map.of("nome", "Novo " + UUID.randomUUID(), "telefone", "11900000000");
      }
      case "atualizarCliente" -> {
        req = MockMvcRequestBuilders.put("/api/v1/clientes/" + clienteId);
        body = Map.of("nome", "Base", "telefone", "11988880000", "revisao", 0);
      }
      case "criarVeiculo" -> {
        req = MockMvcRequestBuilders.post("/api/v1/veiculos");
        body =
            Map.of(
                "clienteId",
                clienteId,
                "placa",
                placaUnica(),
                "marca",
                "Fiat",
                "modelo",
                "Uno",
                "ano",
                2020,
                "km",
                5000,
                "cor",
                "Azul");
      }
      case "abrirOs" -> {
        req = MockMvcRequestBuilders.post("/api/v1/ordens-servico");
        body = Map.of("veiculoId", veiculoId, "kmEntrada", 9000, "relato", "Teste de permissão");
      }
      case "atribuirResponsavel" -> {
        req = MockMvcRequestBuilders.put("/api/v1/ordens-servico/" + osDeApoio() + "/responsavel");
        body = Map.of("mecanicoId", mecanicoA.toString(), "revisao", 0);
      }
      case "criarVersaoOrcamento" -> {
        req =
            MockMvcRequestBuilders.post(
                "/api/v1/ordens-servico/" + osDeApoio() + "/orcamento/versoes");
        body =
            Map.of(
                "itens",
                List.of(
                    Map.of(
                        "tipo", "SERVICO",
                        "descricao", "Item",
                        "quantidade", "1.000",
                        "valorUnitario", "10.00")));
      }
      case "emitirLink" -> {
        req = MockMvcRequestBuilders.post("/api/v1/ordens-servico/" + osDeApoio() + "/links");
        body = Map.of();
      }
      case "criarUsuario" -> {
        req = MockMvcRequestBuilders.post("/api/v1/usuarios");
        body =
            Map.of(
                "nome",
                "Novo",
                "email",
                UUID.randomUUID() + "@a.test",
                "senha",
                SENHA,
                "papel",
                "ATENDENTE");
      }
      case "criarDiagnostico" -> {
        req =
            MockMvcRequestBuilders.post("/api/v1/ordens-servico/" + osDeApoio() + "/diagnosticos");
        body = Map.of("descricao", "Item", "classificacao", "VERDE");
      }
      case "mudarStatus" -> {
        req = MockMvcRequestBuilders.post("/api/v1/ordens-servico/" + osDeApoio() + "/status");
        body = Map.of("status", "DIAGNOSTICO", "revisao", 0);
      }
      case "registrarChecklist" -> {
        req = MockMvcRequestBuilders.post("/api/v1/ordens-servico/" + osDeApoio() + "/checklist");
        body = Map.of("itens", List.of(Map.of("descricao", "Pneus", "condicao", "Bom")));
      }
      case "enviarFoto" -> {
        var upload =
            multipart("/api/v1/ordens-servico/" + osDeApoio() + "/fotos")
                .file(new MockMultipartFile("arquivo", "f.png", "image/png", png()))
                .param("finalidade", "ENTRADA");
        if (token != null) upload.header("Authorization", "Bearer " + token);
        return classificar(mvc.perform(upload).andReturn().getResponse().getStatus());
      }
      case "listarClientes" -> req = MockMvcRequestBuilders.get("/api/v1/clientes");
      case "listarOs" -> req = MockMvcRequestBuilders.get("/api/v1/ordens-servico");
      case "listarEquipe" -> req = MockMvcRequestBuilders.get("/api/v1/usuarios");
      case "verDashboard" -> req = MockMvcRequestBuilders.get("/api/v1/dashboard");
      default -> throw new IllegalArgumentException(operacao);
    }
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
    return classificar(mvc.perform(req).andReturn().getResponse().getStatus());
  }

  static int classificar(int status) {
    return status == 401 || status == 403 || status == 404 ? status : 0;
  }

  // ------------------------------------------------- Partes 9 a 11: sessão, tokens e logout

  @Test
  @DisplayName("Login confere oficina, usuário e senha, e nunca devolve hash")
  void loginValidaOficinaUsuarioESenha() throws Exception {
    var sessao =
        post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", slugA, "email", "owner@a.test", "senha", SENHA),
            200);
    assertThat(sessao.path("oficinaId").asText()).isEqualTo(oficinaA.toString());
    assertThat(sessao.path("usuarioId").asText()).isEqualTo(ownerA.toString());
    assertThat(sessao.path("papel").asText()).isEqualTo("OWNER");
    assertThat(sessao.path("accessToken").asText()).isNotBlank();
    assertThat(sessao.path("refreshToken").asText()).isNotBlank();
    assertThat(sessao.toString()).doesNotContain("senha").doesNotContain("$2a$");
    // O TTL do access token vem da configuração, não de um número fixo no código.
    assertThat(sessao.path("expiresIn").asLong()).isEqualTo(ACCESS_TTL_SEGUNDOS);

    // Oficina inexistente e senha errada respondem igual, sem revelar qual das duas falhou.
    var oficinaInexistente =
        post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", "nao-existe", "email", "owner@a.test", "senha", SENHA),
            401);
    var senhaErrada =
        post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", slugA, "email", "owner@a.test", "senha", "Errada123!"),
            401);
    assertThat(oficinaInexistente.path("detail").asText())
        .isEqualTo(senhaErrada.path("detail").asText());
    assertThat(oficinaInexistente.path("code").asText()).isEqualTo("UNAUTHORIZED");

    // Usuário de uma oficina não entra pelo slug da outra.
    post(
        "/api/v1/auth/login",
        null,
        Map.of("oficina", slugB, "email", "owner@a.test", "senha", SENHA),
        401);
    // Usuário inativo não recebe sessão.
    post(
        "/api/v1/auth/login",
        null,
        Map.of("oficina", slugA, "email", "inativo@a.test", "senha", SENHA),
        401);
    // Credenciais incompletas são erro de validação, não de autenticação.
    assertThat(
            post("/api/v1/auth/login", null, Map.of("oficina", slugA, "email", ""), 400)
                .path("code")
                .asText())
        .isEqualTo("VALIDATION_ERROR");
  }

  @Test
  @DisplayName("Refresh expirado, revogado, de outra oficina ou reutilizado não renova a sessão")
  void refreshRespeitaValidadeERotacao() throws Exception {
    var sessao =
        post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", slugA, "email", "owner@a.test", "senha", SENHA),
            200);
    String refresh = sessao.path("refreshToken").asText();
    var corpo = Map.of("oficinaId", oficinaA.toString(), "refreshToken", refresh);

    // Rotação de uso único: o token velho morre assim que o novo nasce.
    var renovada = post("/api/v1/auth/refresh", null, corpo, 200);
    assertThat(renovada.path("refreshToken").asText()).isNotEqualTo(refresh);
    post("/api/v1/auth/refresh", null, corpo, 401);

    // Token válido apresentado com a oficina errada não vale.
    post(
        "/api/v1/auth/refresh",
        null,
        Map.of(
            "oficinaId",
            oficinaB.toString(),
            "refreshToken",
            renovada.path("refreshToken").asText()),
        401);

    // Token inventado.
    post(
        "/api/v1/auth/refresh",
        null,
        Map.of("oficinaId", oficinaA.toString(), "refreshToken", "nao-existe"),
        401);

    // Token expirado: a data de expiração é conferida no banco a cada uso.
    var outra =
        post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", slugA, "email", "owner@a.test", "senha", SENHA),
            200);
    jdbc.update(
        "update refresh_token set expira_em = now() - interval '1 second' where oficina_id=?",
        oficinaA);
    post(
        "/api/v1/auth/refresh",
        null,
        Map.of(
            "oficinaId", oficinaA.toString(), "refreshToken", outra.path("refreshToken").asText()),
        401);
  }

  @Test
  @DisplayName("Logout revoga o refresh e repetir o logout continua respondendo 204")
  void logoutRevogaEEhIdempotente() throws Exception {
    var sessao =
        post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", slugA, "email", "owner@a.test", "senha", SENHA),
            200);
    var corpo =
        Map.of(
            "oficinaId", oficinaA.toString(), "refreshToken", sessao.path("refreshToken").asText());

    post("/api/v1/auth/logout", null, corpo, 204);
    post("/api/v1/auth/refresh", null, corpo, 401);
    // Repetir o logout não vira erro: o cliente pode reenviar sem medo.
    post("/api/v1/auth/logout", null, corpo, 204);
    // Logout de token inventado também é silencioso, para não confirmar a existência do token.
    post(
        "/api/v1/auth/logout",
        null,
        Map.of("oficinaId", oficinaA.toString(), "refreshToken", "nunca-existiu"),
        204);
  }

  @Test
  @DisplayName("Usuário desativado perde o acesso mesmo com access token ainda no prazo")
  void usuarioDesativadoPerdeAcessoImediatamente() throws Exception {
    get("/api/v1/clientes", tokenAtendenteA, 200);
    jdbc.update("update usuario set ativo=false where id=?", atendenteA);
    get("/api/v1/clientes", tokenAtendenteA, 401);
  }

  // ------------------------------------------------------------------- Parte 18: fotos

  @Test
  @DisplayName("Upload aceita só imagem de verdade e recusa o resto com 415")
  void uploadRecusaConteudoQueNaoEImagem() throws Exception {
    String os = osCompleta(tokenOwnerA);

    // Nome e Content-Type mentem; quem decide é o conteúdo decodificado.
    var disfarcado =
        corpo(
            mvc.perform(
                    multipart("/api/v1/ordens-servico/" + os + "/fotos")
                        .file(
                            new MockMultipartFile(
                                "arquivo",
                                "malicioso.png",
                                "image/png",
                                "<?php echo 1; ?>".getBytes()))
                        .param("finalidade", "ENTRADA")
                        .header("Authorization", "Bearer " + tokenOwnerA))
                .andExpect(status().is(415))
                .andReturn());
    assertThat(disfarcado.path("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");

    // JSON em endpoint multipart também é 415, não 500.
    var tipoErrado =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/ordens-servico/" + os + "/fotos")
                        .contentType("application/json")
                        .content("{}")
                        .header("Authorization", "Bearer " + tokenOwnerA))
                .andExpect(status().is(415))
                .andReturn());
    assertThat(tipoErrado.path("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");

    // Finalidade fora do conjunto conhecido é recusada.
    mvc.perform(
            multipart("/api/v1/ordens-servico/" + os + "/fotos")
                .file(new MockMultipartFile("arquivo", "f.png", "image/png", png()))
                .param("finalidade", "QUALQUER")
                .header("Authorization", "Bearer " + tokenOwnerA))
        .andExpect(status().is(400));
  }

  @Test
  @DisplayName("Foto privada exige sessão e sai com cabeçalhos que impedem cache e sniffing")
  void downloadDeFotoEhPrivadoEComCabecalhosSeguros() throws Exception {
    String os = osCompleta(tokenOwnerA);
    String fotoId =
        corpo(
                mvc.perform(
                        multipart("/api/v1/ordens-servico/" + os + "/fotos")
                            .file(new MockMultipartFile("arquivo", "f.png", "image/png", png()))
                            .param("finalidade", "ENTRADA")
                            .header("Authorization", "Bearer " + tokenOwnerA))
                    .andExpect(status().is(201))
                    .andReturn())
            .path("id")
            .asText();

    String url = "/api/v1/ordens-servico/" + os + "/fotos/" + fotoId + "/conteudo";
    mvc.perform(MockMvcRequestBuilders.get(url)).andExpect(status().is(401));
    mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + tokenOwnerA))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Type", "image/png"))
        .andExpect(
            header().string("Content-Disposition", "inline; filename=\"foto-" + fotoId + ".png\""));

    // Nenhuma resposta entrega endereço de bucket ou chave de objeto.
    assertThat(get("/api/v1/ordens-servico/" + os + "/fotos", tokenOwnerA, 200).toString())
        .doesNotContain("objeto")
        .doesNotContain("garagem-fotos");

    get(
        "/api/v1/ordens-servico/" + os + "/fotos/" + UUID.randomUUID() + "/conteudo",
        tokenOwnerA,
        404);
  }

  // ---------------------------------------------- Partes 8, 27 e 28: Swagger, logs e health

  @Test
  @DisplayName("OpenAPI carrega e descreve a API que existe de verdade")
  void openApiRepresentaAApiReal() throws Exception {
    var doc = get("/v3/api-docs", null, 200);
    var paths = doc.path("paths");

    assertThat(paths.has("/api/v1/dashboard")).isTrue();
    assertThat(paths.has("/api/v1/clientes")).isTrue();
    assertThat(paths.has("/api/v1/publico/{token}")).isTrue();

    // Bearer JWT declarado e aplicado por padrão.
    assertThat(
            doc.path("components").path("securitySchemes").path("bearer").path("scheme").asText())
        .isEqualTo("bearer");
    assertThat(doc.path("security").toString()).contains("bearer");

    // Endpoints de sessão e públicos não pedem token no Swagger.
    assertThat(paths.path("/api/v1/auth/login").path("post").path("security")).isEmpty();
    assertThat(paths.path("/api/v1/publico/{token}").path("get").path("security")).isEmpty();

    // Paginação, filtros e ordenação aparecem como parâmetros documentados.
    assertThat(
            paths.path("/api/v1/clientes").path("get").path("parameters").findValuesAsText("name"))
        .contains("busca", "nome", "telefone", "email", "pagina", "tamanho", "ordenacao");
    assertThat(
            paths
                .path("/api/v1/ordens-servico")
                .path("get")
                .path("parameters")
                .findValuesAsText("name"))
        .contains(
            "numero",
            "status",
            "clienteId",
            "veiculoId",
            "mecanicoId",
            "placa",
            "de",
            "ate",
            "pagina",
            "tamanho",
            "ordenacao");
    assertThat(
            paths.path("/api/v1/usuarios").path("get").path("parameters").findValuesAsText("name"))
        .contains("papel", "ativo", "ordenacao");

    // Upload multipart declarado como tal.
    assertThat(
            paths
                .path("/api/v1/ordens-servico/{osId}/fotos")
                .path("post")
                .path("requestBody")
                .path("content")
                .has("multipart/form-data"))
        .isTrue();

    // Respostas de erro compartilhadas presentes nas operações protegidas.
    var respostas = paths.path("/api/v1/clientes").path("get").path("responses");
    assertThat(respostas.has("400")).isTrue();
    assertThat(respostas.has("401")).isTrue();
    assertThat(respostas.has("403")).isTrue();
    assertThat(respostas.has("404")).isTrue();
    assertThat(respostas.has("409")).isTrue();
    assertThat(doc.path("components").path("schemas").has("Problema")).isTrue();
    // O envelope de página aparece no esquema com o campo novo.
    assertThat(doc.path("components").path("schemas").toString()).contains("totalPaginas");

    // A UI do Swagger sobe.
    mvc.perform(MockMvcRequestBuilders.get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Health responde sem sessão e cobre banco e storage, sem detalhar internals")
  void healthCobreBancoEStorage() throws Exception {
    var health =
        corpo(
            mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(health.path("status").asText()).isEqualTo("UP");
    // show-details: never — nenhum nome de componente, versão ou caminho vaza.
    assertThat(health.has("components")).isFalse();
    assertThat(health.toString()).doesNotContain("jdbc").doesNotContain("bucket");

    // As sondas de liveness e readiness estão disponíveis para o Docker.
    mvc.perform(MockMvcRequestBuilders.get("/actuator/health/readiness"))
        .andExpect(status().isOk());
    mvc.perform(MockMvcRequestBuilders.get("/actuator/health/liveness")).andExpect(status().isOk());

    // Nenhum outro endpoint de actuator está exposto.
    mvc.perform(MockMvcRequestBuilders.get("/actuator/env")).andExpect(status().is4xxClientError());
    mvc.perform(MockMvcRequestBuilders.get("/actuator/beans"))
        .andExpect(status().is4xxClientError());
    mvc.perform(MockMvcRequestBuilders.get("/actuator/loggers"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("Toda resposta carrega X-Request-Id para correlacionar com o log")
  void respostaTemIdDeCorrelacao() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.get("/api/v1/clientes")
                .header("Authorization", "Bearer " + tokenOwnerA))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"));
    // Um id enviado pelo proxy é reaproveitado, para a requisição ter um id só ponta a ponta.
    mvc.perform(
            MockMvcRequestBuilders.get("/api/v1/clientes")
                .header("Authorization", "Bearer " + tokenOwnerA)
                .header("X-Request-Id", "req-do-proxy-123"))
        .andExpect(header().string("X-Request-Id", "req-do-proxy-123"));
    // Um valor suspeito enviado de fora é descartado em vez de entrar no log.
    mvc.perform(
            MockMvcRequestBuilders.get("/api/v1/clientes")
                .header("Authorization", "Bearer " + tokenOwnerA)
                .header("X-Request-Id", "quebra\nde linha"))
        .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("quebra\nde linha")));
  }
}
