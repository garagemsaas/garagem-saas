package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.garagem.cliente.domain.Cliente;
import br.com.garagem.cliente.repository.ClienteRepository;
import br.com.garagem.ordemservico.foto.application.FotoStorage;
import br.com.garagem.shared.seguranca.Tokens;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(Fase1IT.StorageConfig.class)
class Fase1IT extends br.com.garagem.suporte.IntegracaoBase {
  static org.testcontainers.containers.GenericContainer<?> minio;

  /**
   * Armazenamento real para esta suíte: é a única que exercita upload e leitura de foto. O
   * PostgreSQL vem da base compartilhada; o MinIO sobe aqui porque só aqui é necessário.
   */
  @org.springframework.test.context.DynamicPropertySource
  static void armazenamentoDaSuite(org.springframework.test.context.DynamicPropertyRegistry r) {
    if (System.getenv("TEST_DATABASE_URL") != null) return;
    minio =
        new org.testcontainers.containers.GenericContainer<>(
                "quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z.hotfix.7aa24e772")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "test-user")
            .withEnv("MINIO_ROOT_PASSWORD", "test-only-storage-password")
            .withCommand("server", "/data")
            .waitingFor(
                org.testcontainers.containers.wait.strategy.Wait.forHttp("/minio/health/ready")
                    .forPort(9000)
                    // A primeira formatação pode ultrapassar 60s no Docker Desktop.
                    .withStartupTimeout(java.time.Duration.ofMinutes(3)));
    minio.start();
    String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
    r.add("app.storage.endpoint", () -> endpoint);
    r.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
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
  @org.springframework.boot.test.web.server.LocalServerPort int port;
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
    // O bootstrap não exige variável nova para funcionar, mas continua deixando trilha: sem isso
    // uma empresa nasceria ativa e com módulos sem nenhum registro de quem a provisionou.
    assertThat(
            jdbc.queryForObject(
                """
                select e.operador from empresa_administracao_evento e
                 join oficina o on o.id=e.oficina_id where o.slug=?
                """,
                String.class,
                slug))
        .isEqualTo("bootstrap");
    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from empresa_modulo m
                 join oficina o on o.id=m.oficina_id where o.slug=? and m.modulo='OFICINA'
                """,
                Integer.class,
                slug))
        .isEqualTo(1);
    // Provisionar não cria assinatura: a venda é direta e o core não depende de billing.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from assinatura a join oficina o on o.id=a.oficina_id where o.slug=?",
                Integer.class,
                slug))
        .isZero();
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
            assertThat(clientes.findByIdAndOficinaId(UUID.fromString(other), oficinaB)).isEmpty();
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
    mvc.perform(
            delete("/api/v1/ordens-servico/" + os + "/links/" + publicLink.path("id").asText())
                .header("Authorization", "Bearer " + b))
        .andExpect(status().isNotFound());
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
    // Fase 2 passou a distinguir conteúdo não suportado (415) de dado inválido (400). O arquivo
    // continua recusado pelo conteúdo decodificado, não pela extensão: só mudou o status.
    mvc.perform(
            multipart("/api/v1/ordens-servico/" + os + "/fotos")
                .file(
                    new MockMultipartFile(
                        "arquivo", "x.png", "image/png", "<script>bad</script>".getBytes()))
                .param("finalidade", "ENTRADA")
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isUnsupportedMediaType());
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

  @Test
  void fluxoHttpRealComFotoPrivadaAteConclusao() throws Exception {
    var http = java.net.http.HttpClient.newHttpClient();
    var login =
        httpJson(
            http,
            null,
            "POST",
            "/api/v1/auth/login",
            Map.of("oficina", slugA, "email", email, "senha", "SenhaSegura123!"),
            200);
    String token = login.path("accessToken").asText();
    var c =
        httpJson(
            http,
            token,
            "POST",
            "/api/v1/clientes",
            Map.of("nome", "Cliente HTTP", "telefone", "11000000000"),
            201);
    var v =
        httpJson(
            http,
            token,
            "POST",
            "/api/v1/veiculos",
            Map.of(
                "clienteId",
                c.path("id").asText(),
                "placa",
                "HTTP123",
                "marca",
                "Fiat",
                "modelo",
                "Uno",
                "ano",
                2020,
                "km",
                100,
                "cor",
                "Prata"),
            400);
    v =
        httpJson(
            http,
            token,
            "POST",
            "/api/v1/veiculos",
            Map.of(
                "clienteId",
                c.path("id").asText(),
                "placa",
                "HTP1A23",
                "marca",
                "Fiat",
                "modelo",
                "Uno",
                "ano",
                2020,
                "km",
                100,
                "cor",
                "Prata"),
            201);
    var o =
        httpJson(
            http,
            token,
            "POST",
            "/api/v1/ordens-servico",
            Map.of(
                "veiculoId",
                v.path("id").asText(),
                "kmEntrada",
                101,
                "relato",
                "Inspecionar freios"),
            201);
    String base = "/api/v1/ordens-servico/" + o.path("id").asText();
    httpJson(
        http,
        token,
        "POST",
        base + "/checklist",
        Map.of("itens", List.of(Map.of("descricao", "Pneus", "condicao", "Bom"))),
        201);
    httpJson(
        http, token, "POST", base + "/status", Map.of("status", "DIAGNOSTICO", "revisao", 0), 200);
    var d =
        httpJson(
            http,
            token,
            "POST",
            base + "/diagnosticos",
            Map.of("descricao", "Pastilhas gastas", "classificacao", "VERMELHO"),
            201);
    var image = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", image);
    String boundary = "garagem-test-boundary";
    var payload = new ByteArrayOutputStream();
    payload.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"finalidade\"\r\n\r\nDIAGNOSTICO\r\n--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"diagnosticoItemId\"\r\n\r\n"
                + d.path("id").asText()
                + "\r\n--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"teste.png\"\r\nContent-Type: image/png\r\n\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    payload.write(image.toByteArray());
    payload.write(
        ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var upload =
        http.send(
            java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://127.0.0.1:" + port + base + "/fotos"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray()))
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertThat(upload.statusCode()).isEqualTo(201);
    var photo = json.readTree(upload.body());
    String content = base + "/fotos/" + photo.path("id").asText() + "/conteudo";
    var download =
        http.send(
            java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://127.0.0.1:" + port + content))
                .header("Authorization", "Bearer " + token)
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
    assertThat(download.statusCode()).isEqualTo(200);
    assertThat(download.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(download.body())).getWidth())
        .isEqualTo(3);
    httpJson(http, null, "GET", content, null, 401);
    httpJson(http, b, "GET", content, null, 404);
    var metadata =
        jdbc.queryForMap(
            "select objeto,oficina_id,ordem_servico_id,diagnostico_item_id,tamanho from foto_veiculo where id=?",
            UUID.fromString(photo.path("id").asText()));
    assertThat(metadata.get("oficina_id")).isEqualTo(oficinaA);
    assertThat(metadata.get("ordem_servico_id")).isEqualTo(UUID.fromString(o.path("id").asText()));
    assertThat(metadata.get("diagnostico_item_id"))
        .isEqualTo(UUID.fromString(d.path("id").asText()));
    assertThat(((Number) metadata.get("tamanho")).longValue()).isEqualTo(download.body().length);
    if (minio != null) {
      var direct =
          http.send(
              java.net.http.HttpRequest.newBuilder(
                      java.net.URI.create(
                          "http://"
                              + minio.getHost()
                              + ":"
                              + minio.getMappedPort(9000)
                              + "/garagem-fotos/"
                              + metadata.get("objeto")))
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      assertThat(direct.statusCode()).isEqualTo(403);
    }
    o = httpJson(http, token, "GET", base, null, 200);
    httpJson(
        http,
        token,
        "POST",
        base + "/status",
        Map.of("status", "ORCAMENTO", "revisao", o.path("revisao").asLong()),
        200);
    var version =
        httpJson(
            http,
            token,
            "POST",
            base + "/orcamento/versoes",
            Map.of(
                "itens",
                List.of(
                    Map.of(
                        "tipo",
                        "SERVICO",
                        "descricao",
                        "Troca de pastilhas",
                        "quantidade",
                        1,
                        "valorUnitario",
                        150))),
            201);
    o = httpJson(http, token, "GET", base, null, 200);
    httpJson(
        http,
        token,
        "POST",
        base + "/status",
        Map.of("status", "AGUARDANDO_APROVACAO", "revisao", o.path("revisao").asLong()),
        200);
    var initialVersion =
        httpJson(http, token, "GET", base + "/orcamento/versoes", null, 200).get(0);
    version =
        httpJson(
            http,
            token,
            "POST",
            base + "/orcamento/versoes",
            Map.of(
                "itens",
                List.of(
                    Map.of(
                        "tipo",
                        "SERVICO",
                        "descricao",
                        "Troca de pastilhas revisada",
                        "quantidade",
                        2,
                        "valorUnitario",
                        150))),
            201);
    assertThat(version.path("numero").asInt()).isEqualTo(2);
    assertThat(httpJson(http, token, "GET", base + "/orcamento/versoes", null, 200).get(0))
        .isEqualTo(initialVersion);
    o = httpJson(http, token, "GET", base, null, 200);
    httpJson(
        http,
        token,
        "POST",
        base + "/status",
        Map.of("status", "AGUARDANDO_APROVACAO", "revisao", o.path("revisao").asLong()),
        200);
    var l = httpJson(http, token, "POST", base + "/links", Map.of(), 201);
    assertThat(
            httpJson(http, null, "GET", "/api/v1/publico/" + l.path("token").asText(), null, 200)
                .path("orcamento")
                .path("id")
                .asText())
        .isEqualTo(version.path("id").asText());
    httpJson(
        http,
        null,
        "POST",
        "/api/v1/publico/" + l.path("token").asText() + "/decisao",
        Map.of("versaoId", version.path("id").asText(), "aprovado", true),
        200);
    for (String next : List.of("TESTE", "PRONTO")) {
      o = httpJson(http, token, "GET", base, null, 200);
      httpJson(
          http,
          token,
          "POST",
          base + "/status",
          Map.of("status", next, "revisao", o.path("revisao").asLong()),
          200);
    }
    assertThat(httpJson(http, token, "GET", base + "/timeline", null, 200).toString())
        .contains(
            "FOTO_ADICIONADA",
            "CHECKLIST_REGISTRADO",
            "DIAGNOSTICO_REGISTRADO",
            "ORCAMENTO_APROVADO",
            "PRONTO");
    assertThat(
            jdbc.queryForMap(
                "select status,concluida_em from ordem_servico where id=? and oficina_id=?",
                UUID.fromString(o.path("id").asText()),
                oficinaA))
        .containsEntry("status", "PRONTO")
        .doesNotContainValue(null);
    httpJson(
        http,
        token,
        "POST",
        base + "/diagnosticos",
        Map.of("descricao", "Tardio", "classificacao", "VERDE"),
        409);
    httpJson(http, null, "GET", "/actuator/health", null, 200);
  }

  @Test
  void fase4ErrosPublicosNaoExponhemToken() throws Exception {
    String os = prepararOrcamento(a);
    var v = versao(a, os, "10.00");
    var l = link(a, os);
    String token = l.path("token").asText();
    String path = "/api/v1/publico/" + token + "/decisao";
    for (var input :
        List.of(Map.of(), Map.of("versaoId", v.path("id").asText(), "aprovado", true))) {
      int code = input.isEmpty() ? 400 : 409;
      var error = send(null, path, input, code);
      assertThat(error.toString()).doesNotContain(token);
      assertThat(error.hasNonNull("requestId")).isTrue();
      assertThat(error.hasNonNull("timestamp")).isTrue();
    }
  }

  @Test
  void fase4RevogacaoRepetidaPreservaDataEEvento() throws Exception {
    String os = os(a);
    var l = link(a, os);
    String path = "/api/v1/ordens-servico/" + os + "/links/" + l.path("id").asText();
    mvc.perform(delete(path).header("Authorization", "Bearer " + a))
        .andExpect(status().isNoContent());
    var before =
        jdbc.queryForMap(
            "select revogado_em from link_acesso_publico where id=?",
            UUID.fromString(l.path("id").asText()));
    mvc.perform(delete(path).header("Authorization", "Bearer " + a))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForMap(
                "select revogado_em from link_acesso_publico where id=?",
                UUID.fromString(l.path("id").asText())))
        .isEqualTo(before);
    assertThat(read(a, "/api/v1/ordens-servico/" + os + "/timeline", 200).findValuesAsText("tipo"))
        .containsOnlyOnce("LINK_REVOGADO");
    read(null, "/api/v1/publico/" + l.path("token").asText(), 404);
  }

  @Test
  void fase4UploadAcimaDoLimiteResponde413() throws Exception {
    String os = os(a);
    mvc.perform(
            multipart("/api/v1/ordens-servico/" + os + "/fotos")
                .file(
                    new MockMultipartFile(
                        "arquivo", "grande.png", "image/png", new byte[10 * 1024 * 1024 + 1]))
                .param("finalidade", "ENTRADA")
                .header("Authorization", "Bearer " + a))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("code").value("PAYLOAD_TOO_LARGE"));
  }

  @Test
  void fase4VersoesConcorrentesPreservamHistorico() throws Exception {
    String os = prepararOrcamento(a);
    versao(a, os, "10.01");
    // Comparar snapshots persistidos evita confundir precisão de Instant/JDBC com mutação.
    var first = read(a, "/api/v1/ordens-servico/" + os + "/orcamento/versoes", 200).get(0);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<JsonNode> action =
          () -> {
            start.await();
            return versao(a, os, "20.01");
          };
      var one = executor.submit(action);
      var two = executor.submit(action);
      start.countDown();
      assertThat(
              List.of(
                  one.get(20, TimeUnit.SECONDS).path("numero").asInt(),
                  two.get(20, TimeUnit.SECONDS).path("numero").asInt()))
          .containsExactlyInAnyOrder(2, 3);
    }
    var versions = read(a, "/api/v1/ordens-servico/" + os + "/orcamento/versoes", 200);
    assertThat(versions.size()).isEqualTo(3);
    assertThat(versions.get(0)).isEqualTo(first);
  }

  @Test
  void fase4DecisoesContrariasConcorrentesNaoSeSobrescrevem() throws Exception {
    String os = prepararOrcamento(a);
    var v = versao(a, os, "10.00");
    statusOs(a, os, "AGUARDANDO_APROVACAO");
    String path = "/api/v1/publico/" + link(a, os).path("token").asText() + "/decisao";
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<Integer>>();
      for (boolean approve : List.of(true, false))
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post(path)
                              .contentType("application/json")
                              .content(
                                  json.writeValueAsString(
                                      Map.of(
                                          "versaoId", v.path("id").asText(), "aprovado", approve))))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      assertThat(
              List.of(
                  futures.get(0).get(20, TimeUnit.SECONDS),
                  futures.get(1).get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    var versions = read(a, "/api/v1/ordens-servico/" + os + "/orcamento/versoes", 200);
    boolean approved = versions.get(0).path("decisao").path("aprovado").asBoolean();
    assertThat(read(a, "/api/v1/ordens-servico/" + os, 200).path("status").asText())
        .isEqualTo(approved ? "EM_MANUTENCAO" : "ORCAMENTO");
    var timeline = read(a, "/api/v1/ordens-servico/" + os + "/timeline", 200);
    assertThat(
            timeline.findValuesAsText("tipo").stream()
                .filter(t -> t.equals("ORCAMENTO_APROVADO") || t.equals("ORCAMENTO_RECUSADO"))
                .count())
        .isEqualTo(1);
  }

  @Test
  void fase4RecusaRepetidaEContrariaEExpiracao() throws Exception {
    String os = prepararOrcamento(a);
    var v = versao(a, os, "10.00");
    statusOs(a, os, "AGUARDANDO_APROVACAO");
    var l = link(a, os);
    String path = "/api/v1/publico/" + l.path("token").asText();
    var refusal = Map.of("versaoId", v.path("id").asText(), "aprovado", false);
    send(null, path + "/decisao", refusal, 200);
    send(null, path + "/decisao", refusal, 200);
    assertThat(read(null, path, 200).path("orcamento").path("decisao").path("aprovado").isBoolean())
        .isTrue();
    send(null, path + "/decisao", Map.of("versaoId", v.path("id").asText(), "aprovado", true), 409);
    assertThat(read(a, "/api/v1/ordens-servico/" + os + "/timeline", 200).findValuesAsText("tipo"))
        .containsOnlyOnce("ORCAMENTO_RECUSADO");
    jdbc.update(
        "update link_acesso_publico set expira_em=clock_timestamp()-interval '1 second' where id=?",
        UUID.fromString(l.path("id").asText()));
    read(null, path, 404);
    send(null, path + "/decisao", refusal, 404);
  }

  @Test
  void fase4ExpiracaoERevogacaoDuranteEsperaDoLockImpedemDecisao() throws Exception {
    for (boolean revoke : List.of(false, true)) {
      String os = prepararOrcamento(a);
      var v = versao(a, os, "10.00");
      statusOs(a, os, "AGUARDANDO_APROVACAO");
      var l = link(a, os);
      String path = "/api/v1/publico/" + l.path("token").asText() + "/decisao";
      try (var connection = jdbc.getDataSource().getConnection();
          var executor = Executors.newSingleThreadExecutor()) {
        connection.setAutoCommit(false);
        try (var lock =
            connection.prepareStatement("select id from ordem_servico where id=? for update")) {
          lock.setObject(1, UUID.fromString(os));
          lock.executeQuery().close();
        }
        var future =
            executor.submit(
                () ->
                    mvc.perform(
                            post(path)
                                .contentType("application/json")
                                .content(
                                    json.writeValueAsString(
                                        Map.of(
                                            "versaoId", v.path("id").asText(), "aprovado", true))))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        try {
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
          boolean waiting = false;
          while (System.nanoTime() < deadline) {
            waiting =
                Boolean.TRUE.equals(
                    jdbc.queryForObject(
                        "select exists(select 1 from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%ordem_servico%' and pid<>pg_backend_pid())",
                        Boolean.class));
            if (waiting) break;
            Thread.sleep(20);
          }
          assertThat(waiting).as("decisão deve aguardar lock da OS").isTrue();
          // A transação da decisão já começou. Este instante fica depois de seu now().
          jdbc.update(
              revoke
                  ? "update link_acesso_publico set revogado_em=clock_timestamp() where id=?"
                  : "update link_acesso_publico set expira_em=clock_timestamp() where id=?",
              UUID.fromString(l.path("id").asText()));
        } finally {
          connection.rollback();
        }
        assertThat(future.get(20, TimeUnit.SECONDS)).isEqualTo(404);
      }
      assertThat(
              read(a, "/api/v1/ordens-servico/" + os + "/orcamento/versoes", 200)
                  .get(0)
                  .path("decisao")
                  .isNull())
          .isTrue();
    }
  }

  @Test
  void fase4ValidacaoDeChecklistDiagnosticoERevogacao() throws Exception {
    String os = os(a), base = "/api/v1/ordens-servico/" + os;
    var checklist = Map.of("itens", List.of(Map.of("descricao", "Pneu", "condicao", "Bom")));
    var diagnostico = Map.of("descricao", "Inspecionar", "classificacao", "AMARELO");
    for (String target : List.of(base, "/api/v1/ordens-servico/" + UUID.randomUUID())) {
      send(null, target + "/checklist", checklist, 401);
      if (!target.equals(base)) {
        send(a, target + "/checklist", checklist, 404);
        send(a, target + "/diagnosticos", diagnostico, 404);
      }
    }
    assertThat(send(a, base + "/checklist", Map.of("itens", List.of()), 400).path("code").asText())
        .isEqualTo("VALIDATION_ERROR");
    send(
        a,
        base + "/checklist",
        Map.of("itens", List.of(Map.of("descricao", "", "condicao", ""))),
        400);
    send(a, base + "/diagnosticos", Map.of("descricao", "", "classificacao", "VERDE"), 400);
    send(
        a, base + "/diagnosticos", Map.of("descricao", "Teste", "classificacao", "INVENTADO"), 400);
    var l = link(a, os);
    String revoke = base + "/links/" + l.path("id").asText();
    mvc.perform(delete(revoke)).andExpect(status().isUnauthorized());
    mvc.perform(delete(revoke).header("Authorization", "Bearer " + mecanico))
        .andExpect(status().isForbidden());
    read(null, "/api/v1/publico/invalido", 404);
    read(null, "/api/v1/publico/" + Tokens.novo(), 404);
    read(a, base + "/fotos/" + UUID.randomUUID() + "/conteudo", 404);
  }

  @Test
  void fase4TimelinePersistidaCronologicaESemCredenciais() throws Exception {
    String os = prepararOrcamento(a);
    versao(a, os, "10.00");
    var l = link(a, os);
    var timeline = read(a, "/api/v1/ordens-servico/" + os + "/timeline", 200);
    assertThat(timeline.size())
        .isEqualTo(
            jdbc.queryForObject(
                "select count(*) from evento_ordem_servico where ordem_servico_id=? and oficina_id=?",
                Integer.class,
                UUID.fromString(os),
                oficinaA));
    assertThat(timeline.findValuesAsText("criadoEm").stream().map(Instant::parse).toList())
        .isSorted();
    assertThat(timeline.toString()).doesNotContain(l.path("token").asText());
    read(b, "/api/v1/ordens-servico/" + os + "/timeline", 404);
    statusOs(a, os, "AGUARDANDO_APROVACAO");
    var summary = read(null, "/api/v1/publico/" + l.path("token").asText(), 200);
    assertThat(summary.properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder("numero", "status", "veiculo", "previsaoEntrega", "orcamento");
    assertThat(summary.toString())
        .doesNotContain("autorId", "oficinaId", "clienteId", "token", "email", "ipOrigem");
  }

  @Test
  void fase4OpenApiDescreveFotosETokenPublico() throws Exception {
    var paths = read(null, "/v3/api-docs", 200).path("paths");
    var upload = paths.path("/api/v1/ordens-servico/{osId}/fotos").path("post");
    for (String code : List.of("201", "413", "415", "503"))
      assertThat(upload.path("responses").has(code)).isTrue();
    for (String suffix : List.of("", "/decisao")) {
      var operation =
          paths.path("/api/v1/publico/{token}" + suffix).path(suffix.isEmpty() ? "get" : "post");
      assertThat(operation.path("parameters").findValuesAsText("name")).contains("token");
      assertThat(operation.path("security").toString()).doesNotContain("bearer");
    }
  }

  JsonNode httpJson(
      java.net.http.HttpClient http,
      String token,
      String method,
      String path,
      Object value,
      int expected)
      throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path))
            .timeout(java.time.Duration.ofSeconds(20));
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (value != null) request.header("Content-Type", "application/json");
    request.method(
        method,
        value == null
            ? java.net.http.HttpRequest.BodyPublishers.noBody()
            : java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value)));
    var response = http.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(expected);
    return json.readTree(response.body());
  }

  @Test
  void oficinasComDadosPropriosBloqueiamEscritaCruzadaERevisaoConcorrente() throws Exception {
    String own = os(a), other = os(b);
    assertThat(read(a, "/api/v1/ordens-servico?oficinaId=" + oficinaB, 200).toString())
        .contains(own)
        .doesNotContain(other);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into diagnostico_item(id,oficina_id,ordem_servico_id,autor_id,descricao,classificacao) values(?,?,?,?, 'Cruzado', 'VERDE')",
                    UUID.randomUUID(),
                    oficinaA,
                    UUID.fromString(other),
                    userA))
        .isInstanceOf(DataIntegrityViolationException.class);
    for (var pair : List.of(new String[] {a, own, other}, new String[] {b, other, own})) {
      String token = pair[0], target = "/api/v1/ordens-servico/" + pair[2];
      var remote = read(token.equals(a) ? b : a, target, 200);
      for (String suffix :
          List.of("", "/checklist", "/diagnosticos", "/orcamento/versoes", "/fotos", "/timeline"))
        read(token, target + suffix, 404);
      assertThat(read(token, "/api/v1/ordens-servico", 200).toString())
          .contains(pair[1])
          .doesNotContain(pair[2]);
      read(token, "/api/v1/clientes/" + remote.path("clienteId").asText(), 404);
      read(token, "/api/v1/veiculos/" + remote.path("veiculoId").asText(), 404);
      mvc.perform(
              put(target + "/responsavel")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content(json.writeValueAsString(Map.of("mecanicoId", userA, "revisao", 0))))
          .andExpect(status().isNotFound());
      var vehicle =
          read(
              token.equals(a) ? b : a,
              "/api/v1/veiculos/" + remote.path("veiculoId").asText(),
              200);
      mvc.perform(
              put("/api/v1/veiculos/" + vehicle.path("id").asText())
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content(json.writeValueAsString(vehicle)))
          .andExpect(status().isNotFound());
    }
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Integer> change =
          () -> {
            start.await();
            return mvc.perform(
                    post("/api/v1/ordens-servico/" + own + "/status")
                        .header("Authorization", "Bearer " + a)
                        .contentType("application/json")
                        .content("{\"status\":\"DIAGNOSTICO\",\"revisao\":0}"))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var one = executor.submit(change);
      var two = executor.submit(change);
      start.countDown();
      assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(read(a, "/api/v1/ordens-servico/" + own + "/timeline", 200).findValuesAsText("tipo"))
        .containsOnlyOnce("STATUS_ALTERADO");
    var page = read(a, "/api/v1/clientes?pagina=-2&tamanho=1000", 200);
    assertThat(page.path("pagina").asInt()).isZero();
    assertThat(page.path("tamanho").asInt()).isEqualTo(100);
    assertThat(read(a, "/api/v1/veiculos?tamanho=0", 200).path("tamanho").asInt()).isEqualTo(1);
  }
}
