package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.garagem.auth.application.Tokens;
import br.com.garagem.cliente.domain.Cliente;
import br.com.garagem.cliente.repository.ClienteRepository;
import br.com.garagem.ordemservico.foto.application.FotoStorage;
import br.com.garagem.tenancy.TenantContext;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(Fase1IT.StorageConfig.class)
class Fase1IT {
  static PostgreSQLContainer<?> postgres;
  static org.testcontainers.containers.GenericContainer<?> minio;

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    String local = System.getenv("TEST_DATABASE_URL");
    if (local == null) {
      postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");
      postgres.start();
      minio =
          new org.testcontainers.containers.GenericContainer<>(
                  "quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z.hotfix.7aa24e772")
              .withExposedPorts(9000)
              .withEnv("MINIO_ROOT_USER", "test-user")
              .withEnv("MINIO_ROOT_PASSWORD", "test-only-storage-password")
              .withCommand("server", "/data")
              .waitingFor(
                  org.testcontainers.containers.wait.strategy.Wait.forHttp("/minio/health/ready")
                      .forPort(9000));
      minio.start();
      String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
      r.add("app.storage.endpoint", () -> endpoint);
      try (var s3 =
          software.amazon.awssdk.services.s3.S3Client.builder()
              .endpointOverride(java.net.URI.create(endpoint))
              .region(software.amazon.awssdk.regions.Region.US_EAST_1)
              .forcePathStyle(true)
              .credentialsProvider(
                  software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                      software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                          "test-user", "test-only-storage-password")))
              .build()) {
        s3.createBucket(
            software.amazon.awssdk.services.s3.model.CreateBucketRequest.builder()
                .bucket("garagem-fotos")
                .build());
      }
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
    if (minio != null) minio.stop();
  }

  @TestConfiguration
  static class StorageConfig {
    @Bean
    @Primary
    FotoStorage fakeStorage(br.com.garagem.ordemservico.foto.application.S3FotoStorage real) {
      if (System.getenv("TEST_DATABASE_URL") == null) {
        return new FotoStorage() {
          public void put(String k, byte[] bytes, String type) {
            real.put(k, bytes, type);
          }

          public byte[] get(String k) {
            return real.get(k);
          }

          public void delete(String k) {
            real.delete(k);
          }
        };
      }
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
  @Autowired TransactionTemplate tx;
  @Autowired EntityManager em;
  @Autowired ClienteRepository clientes;
  UUID oficinaA, oficinaB, userA, userB;
  String a, b, mecanico;
  String slugA, email;
  static String passwordHash;

  @BeforeEach
  void setup() throws Exception {
    if (passwordHash == null) passwordHash = encoder.encode("SenhaSegura123!");
    oficinaA = UUID.randomUUID();
    oficinaB = UUID.randomUUID();
    userA = UUID.randomUUID();
    userB = UUID.randomUUID();
    slugA = "a-" + oficinaA;
    email = "owner@example.test";
    seed(oficinaA, slugA, userA, "OWNER");
    seed(oficinaB, "b-" + oficinaB, userB, "OWNER");
    a = login(slugA).path("accessToken").asText();
    b = login("b-" + oficinaB).path("accessToken").asText();
    UUID mech = UUID.randomUUID();
    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'MECANICO')",
        mech,
        oficinaA,
        "Mecânico",
        "mecanico@example.test",
        passwordHash);
    mecanico =
        body(mvc.perform(
                    post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "oficina",
                                    slugA,
                                    "email",
                                    "mecanico@example.test",
                                    "senha",
                                    "SenhaSegura123!"))))
                .andExpect(status().isOk())
                .andReturn())
            .path("accessToken")
            .asText();
  }

  void seed(UUID oficina, String slug, UUID user, String papel) {
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", oficina, "Oficina teste", slug);
    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,?)",
        user,
        oficina,
        "Dono",
        email,
        passwordHash,
        papel);
  }

  JsonNode login(String slug) throws Exception {
    return send(
        null,
        "/api/v1/auth/login",
        Map.of("oficina", slug, "email", email, "senha", "SenhaSegura123!"),
        200);
  }

  JsonNode body(MvcResult result) throws Exception {
    return json.readTree(
        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
  }

  JsonNode send(String token, String path, Object value, int code) throws Exception {
    var req = post(path).contentType("application/json").content(json.writeValueAsString(value));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return body(mvc.perform(req).andExpect(status().is(code)).andReturn());
  }

  JsonNode read(String token, String path, int code) throws Exception {
    var req = get(path);
    if (token != null) req.header("Authorization", "Bearer " + token);
    return body(mvc.perform(req).andExpect(status().is(code)).andReturn());
  }

  String cliente(String token, String nome) throws Exception {
    return send(token, "/api/v1/clientes", Map.of("nome", nome, "telefone", "11999999999"), 201)
        .path("id")
        .asText();
  }

  String veiculo(String token, String cliente, String placa) throws Exception {
    return send(
            token,
            "/api/v1/veiculos",
            Map.of(
                "clienteId",
                cliente,
                "placa",
                placa,
                "marca",
                "Fiat",
                "modelo",
                "Uno",
                "ano",
                2020,
                "km",
                1000,
                "cor",
                "Prata"),
            201)
        .path("id")
        .asText();
  }

  String os(String token) throws Exception {
    String cliente = cliente(token, "Cliente " + UUID.randomUUID());
    String placa = "ABC" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    String v = veiculo(token, cliente, placa);
    return send(
            token,
            "/api/v1/ordens-servico",
            Map.of("veiculoId", v, "kmEntrada", 1001, "relato", "Ruído no motor"),
            201)
        .path("id")
        .asText();
  }

  void statusOs(String token, String os, String status) throws Exception {
    var atual = read(token, "/api/v1/ordens-servico/" + os, 200);
    send(
        token,
        "/api/v1/ordens-servico/" + os + "/status",
        Map.of("status", status, "revisao", atual.path("revisao").asLong()),
        200);
  }

  String prepararOrcamento(String token) throws Exception {
    String os = os(token);
    statusOs(token, os, "DIAGNOSTICO");
    statusOs(token, os, "ORCAMENTO");
    return os;
  }

  JsonNode versao(String token, String os, String valor) throws Exception {
    return send(
        token,
        "/api/v1/ordens-servico/" + os + "/orcamento/versoes",
        Map.of(
            "observacoes",
            "Revisão",
            "itens",
            List.of(
                Map.of(
                    "tipo",
                    "SERVICO",
                    "descricao",
                    "Troca de óleo",
                    "quantidade",
                    "1.500",
                    "valorUnitario",
                    valor))),
        201);
  }

  JsonNode link(String token, String os) throws Exception {
    return send(token, "/api/v1/ordens-servico/" + os + "/links", Map.of(), 201);
  }

  @Test
  void cadastrosEIsolamentoDeLeituraEscrita() throws Exception {
    String c = cliente(a, "Maria");
    String v = veiculo(a, c, "ABC-1D23");
    assertThat(read(a, "/api/v1/veiculos/" + v, 200).path("placa").asText()).isEqualTo("ABC1D23");
    read(b, "/api/v1/clientes/" + c, 404);
    read(b, "/api/v1/veiculos/" + v, 404);
    send(
        b,
        "/api/v1/ordens-servico",
        Map.of("veiculoId", v, "kmEntrada", 1001, "relato", "Tentativa externa"),
        404);
    assertThat(read(b, "/api/v1/clientes", 200).path("total").asLong()).isZero();
    assertThat(read(b, "/api/v1/veiculos", 200).path("total").asLong()).isZero();
    send(
        b,
        "/api/v1/veiculos",
        Map.of(
            "clienteId",
            c,
            "placa",
            "DEF1234",
            "marca",
            "Fiat",
            "modelo",
            "Uno",
            "ano",
            2020,
            "km",
            0,
            "cor",
            "Azul"),
        404);
    mvc.perform(
            put("/api/v1/clientes/" + c)
                .header("Authorization", "Bearer " + b)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("nome", "Inválido", "telefone", "11", "revisao", 0))))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/clientes/" + c)
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("nome", "Maria Silva", "telefone", "11", "revisao", 0))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("revisao").value(1));
    mvc.perform(
            put("/api/v1/clientes/" + c)
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("nome", "Outra", "telefone", "11", "revisao", 0))))
        .andExpect(status().isConflict());
    String c2 = cliente(b, "Outra Maria");
    veiculo(b, c2, "ABC1D23");
    read(null, "/api/v1/clientes", 401);
  }

  @Test
  void bootstrapCriaOwnerUmaVezSemRedefinirSenha() throws Exception {
    String slug = "bootstrap-" + UUID.randomUUID();
    var env =
        new org.springframework.mock.env.MockEnvironment()
            .withProperty("app.bootstrap.slug", slug)
            .withProperty("app.bootstrap.nome", "Oficina Bootstrap")
            .withProperty("app.bootstrap.email", email)
            .withProperty("app.bootstrap.senha", "SenhaSegura123!");
    var bootstrap = new br.com.garagem.oficina.BootstrapOficina(env, jdbc, encoder);
    tx.executeWithoutResult(
        t -> bootstrap.run(new org.springframework.boot.DefaultApplicationArguments()));
    env.withProperty("app.bootstrap.senha", "NaoMudarSenha123!");
    tx.executeWithoutResult(
        t -> bootstrap.run(new org.springframework.boot.DefaultApplicationArguments()));
    assertThat(login(slug).path("papel").asText()).isEqualTo("OWNER");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from usuario u join oficina o on o.id=u.oficina_id where o.slug=?",
                Integer.class,
                slug))
        .isEqualTo(1);
  }

  @Test
  void filtroAutomaticoEChavesCompostasProtegemORepositorio() throws Exception {
    String own = cliente(a, "A");
    String other = cliente(b, "B");
    TenantContext.set(oficinaA);
    try {
      tx.executeWithoutResult(
          s -> {
            assertThat(em.createQuery("select c from Cliente c", Cliente.class).getResultList())
                .extracting(c -> c.id.toString())
                .containsExactly(own);
            assertThat(clientes.findByIdAndOficinaId(UUID.fromString(other), oficinaA)).isEmpty();
          });
    } finally {
      TenantContext.clear();
    }
    tx.executeWithoutResult(
        s ->
            assertThat(em.createQuery("select c from Cliente c", Cliente.class).getResultList())
                .isEmpty());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into veiculo(id,oficina_id,cliente_id,placa,marca,modelo,ano,km,cor) values(?,?,?,'ABC1234','Fiat','Uno',2020,0,'Azul')",
                    UUID.randomUUID(),
                    oficinaA,
                    UUID.fromString(other)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void autenticacaoRotacaoRevogacaoEPapeis() throws Exception {
    var session = login(slugA);
    var refresh =
        Map.of(
            "oficinaId",
            oficinaA.toString(),
            "refreshToken",
            session.path("refreshToken").asText());
    send(
        null,
        "/api/v1/auth/refresh",
        Map.of(
            "oficinaId",
            oficinaB.toString(),
            "refreshToken",
            session.path("refreshToken").asText()),
        401);
    var renewed = send(null, "/api/v1/auth/refresh", refresh, 200);
    send(null, "/api/v1/auth/refresh", refresh, 401);
    mvc.perform(
            post("/api/v1/auth/logout")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "oficinaId",
                            oficinaA.toString(),
                            "refreshToken",
                            renewed.path("refreshToken").asText()))))
        .andExpect(status().isNoContent());
    send(
        null,
        "/api/v1/auth/refresh",
        Map.of(
            "oficinaId",
            oficinaA.toString(),
            "refreshToken",
            renewed.path("refreshToken").asText()),
        401);
    send(
        null,
        "/api/v1/auth/login",
        Map.of("oficina", slugA, "email", email, "senha", "Errada"),
        401);
    send(mecanico, "/api/v1/clientes", Map.of("nome", "Sem permissão", "telefone", "11"), 403);
    send(
        mecanico,
        "/api/v1/usuarios",
        Map.of(
            "nome",
            "Atendente",
            "email",
            "novo@example.test",
            "senha",
            "SenhaSegura123!",
            "papel",
            "ATENDENTE"),
        403);
    var newUser =
        send(
            a,
            "/api/v1/usuarios",
            Map.of(
                "nome",
                "Atendente",
                "email",
                "novo@example.test",
                "senha",
                "SenhaSegura123!",
                "papel",
                "ATENDENTE"),
            201);
    assertThat(newUser.has("senhaHash")).isFalse();
    assertThat(read(b, "/api/v1/usuarios", 200).toString())
        .doesNotContain(newUser.path("id").asText());
    jdbc.update("update usuario set ativo=false where id=? and oficina_id=?", userA, oficinaA);
    read(a, "/api/v1/clientes", 401);
  }

  @Test
  void fluxoCompletoOrcamentoImutavelAprovacaoComProvaEBusca() throws Exception {
    String os = prepararOrcamento(a);
    var v1 = versao(a, os, "100.01");
    statusOs(a, os, "AGUARDANDO_APROVACAO");
    var publicLink = link(a, os);
    String publicPath = "/api/v1/publico/" + publicLink.path("token").asText();
    var v2 = versao(a, os, "120.00");
    statusOs(a, os, "AGUARDANDO_APROVACAO");
    send(
        null,
        publicPath + "/decisao",
        Map.of("versaoId", v1.path("id").asText(), "aprovado", true),
        409);
    var current = read(a, "/api/v1/ordens-servico/" + os, 200);
    send(
        a,
        "/api/v1/ordens-servico/" + os + "/status",
        Map.of("status", "EM_MANUTENCAO", "revisao", current.path("revisao").asLong()),
        409);
    var pub = read(null, publicPath, 200);
    assertThat(pub.path("orcamento").path("id").asText()).isEqualTo(v2.path("id").asText());
    assertThat(pub.has("clienteId")).isFalse();
    assertThat(pub.toString()).doesNotContain(email, "senha", "ipOrigem");
    send(
        null,
        publicPath + "/decisao",
        Map.of("versaoId", v2.path("id").asText(), "aprovado", true),
        200);
    send(
        null,
        publicPath + "/decisao",
        Map.of("versaoId", v2.path("id").asText(), "aprovado", true),
        200);
    send(
        null,
        publicPath + "/decisao",
        Map.of("versaoId", v2.path("id").asText(), "aprovado", false),
        409);
    var versions = read(a, "/api/v1/ordens-servico/" + os + "/orcamento/versoes", 200);
    assertThat(versions.size()).isEqualTo(2);
    assertThat(versions.get(0).path("total").decimalValue()).isEqualByComparingTo("150.02");
    assertThat(versions.get(1).path("total").decimalValue()).isEqualByComparingTo("180.00");
    var proof =
        jdbc.queryForMap(
            "select criado_em,ip_origem,canal,link_acesso_publico_id from aprovacao_orcamento where oficina_id=? and orcamento_versao_id=?",
            oficinaA,
            UUID.fromString(v2.path("id").asText()));
    assertThat(proof.get("criado_em")).isNotNull();
    assertThat(proof.get("ip_origem")).isEqualTo("127.0.0.1");
    assertThat(proof.get("canal")).isEqualTo("LINK_PUBLICO");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update orcamento_versao set total=1 where id=? and oficina_id=?",
                    UUID.fromString(v1.path("id").asText()),
                    oficinaA))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "delete from item_orcamento where orcamento_versao_id=? and oficina_id=?",
                    UUID.fromString(v1.path("id").asText()),
                    oficinaA))
        .isInstanceOf(DataIntegrityViolationException.class);
    statusOs(a, os, "AGUARDANDO_PECA");
    statusOs(a, os, "EM_MANUTENCAO");
    statusOs(a, os, "TESTE");
    statusOs(a, os, "PRONTO");
    assertThat(read(a, "/api/v1/ordens-servico/" + os, 200).path("concluidaEm").isNull()).isFalse();
    assertThat(read(a, "/api/v1/ordens-servico/" + os + "/timeline", 200).toString())
        .contains("ORCAMENTO_APROVADO", "ORCAMENTO_VERSIONADO");
    assertThat(read(a, "/api/v1/ordens-servico?busca=1", 200).path("total").asLong())
        .isGreaterThan(0);
    assertThat(read(b, "/api/v1/ordens-servico?busca=1", 200).path("total").asLong()).isZero();
  }

  @Test
  void isolamentoEmTodosOsRecursosDaOsELinksRevogados() throws Exception {
    String os = prepararOrcamento(a);
    var v = versao(a, os, "10.00");
    var publicLink = link(a, os);
    for (String suffix :
        List.of("", "/checklist", "/diagnosticos", "/fotos", "/orcamento/versoes", "/timeline"))
      read(b, "/api/v1/ordens-servico/" + os + suffix, 404);
    send(b, "/api/v1/ordens-servico/" + os + "/links", Map.of(), 404);
    send(
        b,
        "/api/v1/ordens-servico/" + os + "/status",
        Map.of("status", "AGUARDANDO_APROVACAO", "revisao", 2),
        404);
    send(
        b,
        "/api/v1/ordens-servico/" + os + "/orcamento/versoes",
        Map.of(
            "itens",
            List.of(
                Map.of("tipo", "SERVICO", "descricao", "x", "quantidade", 1, "valorUnitario", 1))),
        404);
    mvc.perform(
            delete("/api/v1/ordens-servico/" + os + "/links/" + publicLink.path("id").asText())
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isNoContent());
    read(null, "/api/v1/publico/" + publicLink.path("token").asText(), 404);
    var expired = link(a, os);
    jdbc.update(
        "update link_acesso_publico set expira_em=? where id=? and oficina_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        UUID.fromString(expired.path("id").asText()),
        oficinaA);
    read(null, "/api/v1/publico/" + expired.path("token").asText(), 404);
    read(null, "/api/v1/publico/" + Tokens.novo(), 404);
  }

  @Test
  void bancoImpedeAdicionarItemEmVersaoJaCriada() throws Exception {
    String os = prepararOrcamento(a);
    var v = versao(a, os, "10.00");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into item_orcamento(id,oficina_id,orcamento_versao_id,tipo,descricao,quantidade,valor_unitario) values(?,?,?,'SERVICO','Adulterado',1,1)",
                    UUID.randomUUID(),
                    oficinaA,
                    UUID.fromString(v.path("id").asText())))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into orcamento_versao(id,oficina_id,orcamento_id,numero,autor_id,total) select ?,oficina_id,orcamento_id,99,autor_id,10 from orcamento_versao where id=? and oficina_id=?",
                    UUID.randomUUID(),
                    UUID.fromString(v.path("id").asText()),
                    oficinaA))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void checklistDiagnosticoEFotosRespeitamOficinaEOs() throws Exception {
    String os = os(a);
    String other = os(a);
    var checklist =
        send(
            a,
            "/api/v1/ordens-servico/" + os + "/checklist",
            Map.of("itens", List.of(Map.of("descricao", "Pneu", "condicao", "Bom"))),
            201);
    send(
        b,
        "/api/v1/ordens-servico/" + os + "/checklist",
        Map.of("itens", List.of(Map.of("descricao", "Pneu", "condicao", "Bom"))),
        404);
    send(
        a,
        "/api/v1/ordens-servico/" + os + "/checklist",
        Map.of("itens", List.of(Map.of("descricao", "Pneu", "condicao", "Bom"))),
        409);
    var diag =
        send(
            mecanico,
            "/api/v1/ordens-servico/" + os + "/diagnosticos",
            Map.of("descricao", "Pastilhas gastas", "classificacao", "VERMELHO"),
            201);
    send(
        b,
        "/api/v1/ordens-servico/" + os + "/diagnosticos",
        Map.of("descricao", "x", "classificacao", "VERDE"),
        404);
    assertThat(read(a, "/api/v1/ordens-servico/" + os + "/checklist", 200).path("itens").size())
        .isEqualTo(1);
    assertThat(read(a, "/api/v1/ordens-servico/" + os + "/diagnosticos", 200).size()).isEqualTo(1);
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    var file = new MockMultipartFile("arquivo", "foto.png", "image/png", output.toByteArray());
    var foto =
        body(
            mvc.perform(
                    multipart("/api/v1/ordens-servico/" + os + "/fotos")
                        .file(file)
                        .param("finalidade", "DIAGNOSTICO")
                        .param("diagnosticoItemId", diag.path("id").asText())
                        .header("Authorization", "Bearer " + a))
                .andExpect(status().isCreated())
                .andReturn());
    mvc.perform(
            get("/api/v1/ordens-servico/" + os + "/fotos/" + foto.path("id").asText() + "/conteudo")
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
    read(
        b,
        "/api/v1/ordens-servico/" + os + "/fotos/" + foto.path("id").asText() + "/conteudo",
        404);
    mvc.perform(
            multipart("/api/v1/ordens-servico/" + other + "/fotos")
                .file(file)
                .param("finalidade", "ENTRADA")
                .param("checklistItemId", checklist.path("itens").get(0).path("id").asText())
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isNotFound());
    mvc.perform(
            multipart("/api/v1/ordens-servico/" + os + "/fotos")
                .file(file)
                .param("finalidade", "ENTRADA")
                .header("Authorization", "Bearer " + b))
        .andExpect(status().isNotFound());
    mvc.perform(
            multipart("/api/v1/ordens-servico/" + os + "/fotos")
                .file(
                    new MockMultipartFile(
                        "arquivo", "x.png", "image/png", "<script>bad</script>".getBytes()))
                .param("finalidade", "ENTRADA")
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concorrenciaDeDecisaoGravaUmUnicoEvento() throws Exception {
    String os = prepararOrcamento(a);
    var v = versao(a, os, "50.00");
    statusOs(a, os, "AGUARDANDO_APROVACAO");
    String path = "/api/v1/publico/" + link(a, os).path("token").asText() + "/decisao";
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Integer> action =
          () -> {
            start.await();
            return mvc.perform(
                    post(path)
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of("versaoId", v.path("id").asText(), "aprovado", true))))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var one = executor.submit(action);
      var two = executor.submit(action);
      start.countDown();
      assertThat(one.get(20, TimeUnit.SECONDS)).isEqualTo(200);
      assertThat(two.get(20, TimeUnit.SECONDS)).isEqualTo(200);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from aprovacao_orcamento where oficina_id=? and orcamento_versao_id=?",
                Integer.class,
                oficinaA,
                UUID.fromString(v.path("id").asText())))
        .isEqualTo(1);
  }

  @Test
  void openApiEValidacaoSemDetalhesInternos() throws Exception {
    var docs = read(null, "/v3/api-docs", 200);
    assertThat(docs.path("paths").has("/api/v1/ordens-servico/{id}/orcamento/versoes")).isTrue();
    var error = send(a, "/api/v1/clientes", Map.of("nome", "", "telefone", ""), 400);
    assertThat(error.path("detail").asText()).contains("nome");
    assertThat(error.has("trace")).isFalse();
    String os = os(a);
    send(
        a,
        "/api/v1/ordens-servico/" + os + "/status",
        Map.of("status", "PRONTO", "revisao", 0),
        409);
  }
}
