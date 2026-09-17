package br.com.garagem;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integração da Fase 7 contra PostgreSQL real: limites por plano, assinatura, webhook assinado,
 * inadimplência, suspensão, reativação e isolamento entre oficinas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(Fase7IT.RelogioConfig.class)
class Fase7IT {
  static final String BASE = "/api/v1/assinatura";
  static final String WEBHOOK = "/api/v1/webhooks/pagamento";
  static final String SEGREDO = "segredo-de-teste-do-webhook-com-mais-de-32-bytes";
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
    r.add("app.pagamento.webhook-secret", () -> SEGREDO);
    r.add("app.pagamento.tolerancia", () -> "P7D");
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
  UUID ownerA, atendenteA, mecanicoA, ownerB;
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
    ownerB = criarUsuario(oficinaB, "owner@b.test", "OWNER", true);
    tokenOwnerA = token(slugA, "owner@a.test");
    tokenAtendenteA = token(slugA, "atendente@a.test");
    tokenMecanicoA = token(slugA, "mecanico@a.test");
    tokenOwnerB = token(slugB, "owner@b.test");
  }

  /**
   * O catálogo é global e os cenários mexem nele; restaurar aqui evita que uma falha no meio de um
   * teste contamine os seguintes.
   */
  @AfterEach
  void restaurarCatalogo() {
    jdbc.update(
        "update plano set max_usuarios=3, max_armazenamento_bytes=1073741824, max_ordens_servico_mes=60, max_veiculos=200 where codigo='BASICO'");
    jdbc.update(
        "update plano set max_usuarios=10, max_armazenamento_bytes=10737418240, max_ordens_servico_mes=400, max_veiculos=2000 where codigo='PROFISSIONAL'");
    jdbc.update(
        "update plano set max_usuarios=30, max_armazenamento_bytes=53687091200, max_ordens_servico_mes=null, max_veiculos=null where codigo='PREMIUM'");
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
    return post(
            "/api/v1/auth/login",
            null,
            Map.of("oficina", slug, "email", email, "senha", SENHA),
            200)
        .path("accessToken")
        .asText();
  }

  JsonNode corpo(MvcResult r) throws Exception {
    String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return texto.isEmpty() ? json.createObjectNode() : json.readTree(texto);
  }

  JsonNode post(String path, String token, Object body, int esperado) throws Exception {
    var req =
        MockMvcRequestBuilders.post(path)
            .contentType("application/json")
            .content(json.writeValueAsString(body));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return corpo(
        mvc.perform(req).andExpect(MockMvcResultMatchers.status().is(esperado)).andReturn());
  }

  JsonNode put(String path, String token, Object body, int esperado) throws Exception {
    var req =
        MockMvcRequestBuilders.put(path)
            .contentType("application/json")
            .content(json.writeValueAsString(body));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return corpo(
        mvc.perform(req).andExpect(MockMvcResultMatchers.status().is(esperado)).andReturn());
  }

  JsonNode get(String path, String token, int esperado) throws Exception {
    var req = MockMvcRequestBuilders.get(path);
    if (token != null) req.header("Authorization", "Bearer " + token);
    return corpo(
        mvc.perform(req).andExpect(MockMvcResultMatchers.status().is(esperado)).andReturn());
  }

  long revisao(String token) throws Exception {
    return get(BASE, token, 200).path("assinatura").path("revisao").asLong();
  }

  /** Troca o plano da oficina direto no catálogo-assinatura, para montar cenários de limite. */
  void usarPlano(UUID oficina, String codigo) {
    jdbc.update(
        "update assinatura set plano_id=(select id from plano where codigo=?) where oficina_id=?",
        codigo,
        oficina);
  }

  void ajustarLimite(String codigo, String coluna, Object valor) {
    jdbc.update("update plano set " + coluna + "=? where codigo=?", valor, codigo);
  }

  static String placaUnica() {
    int n = PLACAS.getAndIncrement();
    return ""
        + (char) ('A' + n / 676 % 26)
        + (char) ('A' + n / 26 % 26)
        + (char) ('A' + n % 26)
        + "0A00";
  }

  String cliente(String token) throws Exception {
    return post(
            "/api/v1/clientes",
            token,
            Map.of("nome", "Cliente " + UUID.randomUUID(), "telefone", "11999999999"),
            201)
        .path("id")
        .asText();
  }

  JsonNode veiculo(String token, int esperado) throws Exception {
    return post(
        "/api/v1/veiculos",
        token,
        Map.of(
            "clienteId",
            cliente(token),
            "placa",
            placaUnica(),
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
        esperado);
  }

  JsonNode novoUsuario(String token, int esperado) throws Exception {
    return post(
        "/api/v1/usuarios",
        token,
        Map.of(
            "nome",
            "Novo",
            "email",
            UUID.randomUUID() + "@a.test",
            "senha",
            SENHA,
            "papel",
            "MECANICO"),
        esperado);
  }

  JsonNode novaOs(String token, int esperado) throws Exception {
    return novaOsCom(token, veiculo(token, 201).path("id").asText(), esperado);
  }

  /**
   * Abre a OS em um veículo já existente: com a assinatura bloqueada, o cadastro do veículo também
   * seria recusado, e o teste deixaria de medir a abertura da OS.
   */
  JsonNode novaOsCom(String token, String veiculoId, int esperado) throws Exception {
    return post(
        "/api/v1/ordens-servico",
        token,
        Map.of("veiculoId", veiculoId, "kmEntrada", 1000, "relato", "Ruído no motor"),
        esperado);
  }

  /** Assina o corpo exatamente como o provedor manual exige: HMAC-SHA256 hexadecimal do cru. */
  static String assinar(String corpo) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    var hex = new StringBuilder();
    for (byte b : mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)))
      hex.append(String.format("%02x", b));
    return hex.toString();
  }

  String assinaturaExterna(UUID oficina) {
    return jdbc.queryForObject(
        "select provider_subscription_id from assinatura where oficina_id=?",
        String.class,
        oficina);
  }

  /** Garante identificador remoto: o gatilho cria a assinatura sem passar pelo provedor. */
  void comAssinaturaExterna(UUID oficina) {
    jdbc.update(
        "update assinatura set provider_subscription_id=? where oficina_id=? and provider_subscription_id is null",
        "manual:" + oficina,
        oficina);
  }

  JsonNode enviarWebhook(String id, String tipo, UUID oficina, int esperado) throws Exception {
    comAssinaturaExterna(oficina);
    var corpo =
        json.writeValueAsString(
            Map.of("id", id, "tipo", tipo, "assinaturaExterna", assinaturaExterna(oficina)));
    return corpo(
        mvc.perform(
                MockMvcRequestBuilders.post(WEBHOOK)
                    .contentType("application/json")
                    .header("X-Garagem-Signature", assinar(corpo))
                    .content(corpo))
            .andExpect(MockMvcResultMatchers.status().is(esperado))
            .andReturn());
  }

  // -------------------------------------------------------------- planos e limites

  @Test
  void oficinaNasceComAssinaturaDeAvaliacaoEPlanoEspelhado() throws Exception {
    var detalhe = get(BASE, tokenOwnerA, 200);
    assertThat(detalhe.path("assinatura").path("status").asText()).isEqualTo("TRIAL");
    assertThat(detalhe.path("assinatura").path("permiteCrescer").asBoolean()).isTrue();
    assertThat(detalhe.path("planos")).hasSize(3);
    assertThat(jdbc.queryForObject("select plano from oficina where id=?", String.class, oficinaA))
        .isEqualTo("PROFISSIONAL");
  }

  @Test
  void limiteDeUsuariosAbaixoNoLimiteEAcima() throws Exception {
    usarPlano(oficinaA, "BASICO");
    ajustarLimite("BASICO", "max_usuarios", 4);
    // Já existem 3 ativos no setup: abaixo do limite, a criação passa.
    novoUsuario(tokenOwnerA, 201);
    // Exatamente no limite (4 de 4): a próxima criação é recusada.
    var erro = novoUsuario(tokenOwnerA, 402);
    assertThat(erro.path("code").asText()).isEqualTo("PLAN_LIMIT_REACHED");
    assertThat(erro.path("detail").asText()).contains("4 usuário(s) ativo(s)");
  }

  @Test
  void desativarUsuarioLiberaVagaSemDesbloquearEdicao() throws Exception {
    usarPlano(oficinaA, "BASICO");
    ajustarLimite("BASICO", "max_usuarios", 3);
    novoUsuario(tokenOwnerA, 402);
    // Desativar não é bloqueado pelo plano e devolve a vaga: o limite conta usuários ativos.
    jdbc.update("update usuario set ativo=false where id=?", mecanicoA);
    novoUsuario(tokenOwnerA, 201);
  }

  @Test
  void limiteDeArmazenamentoBloqueiaUploadAcimaDaCota() throws Exception {
    usarPlano(oficinaA, "BASICO");
    String os = novaOs(tokenOwnerA, 201).path("id").asText();
    // Cota menor que qualquer imagem válida: o próximo upload não cabe.
    ajustarLimite("BASICO", "max_armazenamento_bytes", 10);
    var erro =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.multipart("/api/v1/ordens-servico/" + os + "/fotos")
                        .file(imagem())
                        .param("finalidade", "ENTRADA")
                        .header("Authorization", "Bearer " + tokenOwnerA))
                .andExpect(MockMvcResultMatchers.status().is(402))
                .andReturn());
    assertThat(erro.path("code").asText()).isEqualTo("STORAGE_LIMIT_REACHED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from foto_veiculo where oficina_id=?", Integer.class, oficinaA))
        .isZero();
  }

  MockMultipartFile imagem() throws Exception {
    var img = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var saida = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", saida);
    return new MockMultipartFile("arquivo", "foto.png", "image/png", saida.toByteArray());
  }

  @Test
  void limiteDeOrdensPorMesEDeVeiculosRespeitaIlimitado() throws Exception {
    usarPlano(oficinaA, "BASICO");
    ajustarLimite("BASICO", "max_ordens_servico_mes", 1);
    novaOs(tokenOwnerA, 201);
    assertThat(novaOs(tokenOwnerA, 402).path("code").asText()).isEqualTo("PLAN_LIMIT_REACHED");
    // PREMIUM tem os dois limites nulos: ilimitado não bloqueia.
    usarPlano(oficinaA, "PREMIUM");
    novaOs(tokenOwnerA, 201);
  }

  @Test
  void consumoRefleteUsoRealComLimiteIlimitadoExplicito() throws Exception {
    usarPlano(oficinaA, "PREMIUM");
    veiculo(tokenOwnerA, 201);
    var consumo = get(BASE + "/consumo", tokenOwnerA, 200);
    var limites = new HashMap<String, JsonNode>();
    consumo.path("limites").forEach(l -> limites.put(l.path("chave").asText(), l));
    assertThat(limites.get("usuarios").path("usado").asLong()).isEqualTo(3);
    assertThat(limites.get("usuarios").path("limite").asInt()).isEqualTo(30);
    assertThat(limites.get("veiculos").path("limite").isNull()).isTrue();
    assertThat(limites.get("veiculos").path("limiteLegivel").asText()).isEqualTo("Ilimitado");
    assertThat(limites.get("armazenamento").path("usadoLegivel").asText()).isEqualTo("0 B");
  }

  // -------------------------------------------------------------- ciclo de vida da assinatura

  @Test
  void mudarDePlanoRegistraEventoEEspelhaNaOficina() throws Exception {
    put(
        BASE + "/plano",
        tokenOwnerA,
        Map.of("codigo", "PREMIUM", "revisao", revisao(tokenOwnerA)),
        200);
    assertThat(get(BASE, tokenOwnerA, 200).path("assinatura").path("plano").path("codigo").asText())
        .isEqualTo("PREMIUM");
    assertThat(jdbc.queryForObject("select plano from oficina where id=?", String.class, oficinaA))
        .isEqualTo("PREMIUM");
    assertThat(tipos(tokenOwnerA)).contains("SUBSCRIPTION_CHANGED");
  }

  @Test
  void reduzirPlanoAbaixoDoUsoAtualEhRecusado() throws Exception {
    usarPlano(oficinaA, "PREMIUM");
    ajustarLimite("BASICO", "max_usuarios", 2);
    var erro =
        put(
            BASE + "/plano",
            tokenOwnerA,
            Map.of("codigo", "BASICO", "revisao", revisao(tokenOwnerA)),
            409);
    assertThat(erro.path("code").asText()).isEqualTo("PLAN_LIMIT_REACHED");
    assertThat(erro.path("detail").asText()).contains("usuários");
  }

  @Test
  void revisaoDesatualizadaImpedeDecisaoContratual() throws Exception {
    long revisao = revisao(tokenOwnerA);
    put(BASE + "/plano", tokenOwnerA, Map.of("codigo", "PREMIUM", "revisao", revisao), 200);
    assertThat(
            put(BASE + "/plano", tokenOwnerA, Map.of("codigo", "BASICO", "revisao", revisao), 409)
                .path("code")
                .asText())
        .isEqualTo("CONFLICT");
  }

  @Test
  void cancelamentoAoFimDoPeriodoPreservaAcessoEDados() throws Exception {
    novaOs(tokenOwnerA, 201);
    post(
        BASE + "/cancelamento",
        tokenOwnerA,
        Map.of("imediato", false, "motivo", "Fechando a oficina", "revisao", revisao(tokenOwnerA)),
        200);
    var a = get(BASE, tokenOwnerA, 200).path("assinatura");
    assertThat(a.path("status").asText()).isEqualTo("TRIAL");
    assertThat(a.path("canceladaEm").isNull()).isFalse();
    assertThat(a.path("permiteCrescer").asBoolean()).isTrue();
    // Enquanto o período contratado corre, a operação continua normal.
    novaOs(tokenOwnerA, 201);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ordem_servico where oficina_id=?", Integer.class, oficinaA))
        .isEqualTo(2);
  }

  @Test
  void cancelamentoImediatoBloqueiaCrescimentoSemApagarDados() throws Exception {
    String veiculoId = veiculo(tokenOwnerA, 201).path("id").asText();
    novaOsCom(tokenOwnerA, veiculoId, 201);
    post(
        BASE + "/cancelamento",
        tokenOwnerA,
        Map.of("imediato", true, "motivo", "Troca de sistema", "revisao", revisao(tokenOwnerA)),
        200);
    var erro = novaOsCom(tokenOwnerA, veiculoId, 402);
    assertThat(erro.path("code").asText()).isEqualTo("SUBSCRIPTION_INACTIVE");
    // O bloqueio alcança todo crescimento, não só a OS.
    assertThat(veiculo(tokenOwnerA, 402).path("code").asText()).isEqualTo("SUBSCRIPTION_INACTIVE");
    // Leitura e área financeira seguem liberadas; nenhum registro foi removido.
    get("/api/v1/ordens-servico", tokenOwnerA, 200);
    get(BASE, tokenOwnerA, 200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ordem_servico where oficina_id=?", Integer.class, oficinaA))
        .isEqualTo(1);
  }

  @Test
  void cancelamentoExigeMotivoENaoSeRepete() throws Exception {
    post(
        BASE + "/cancelamento",
        tokenOwnerA,
        Map.of("imediato", true, "motivo", "  ", "revisao", revisao(tokenOwnerA)),
        400);
    post(
        BASE + "/cancelamento",
        tokenOwnerA,
        Map.of("imediato", true, "motivo", "Encerrando", "revisao", revisao(tokenOwnerA)),
        200);
    post(
        BASE + "/cancelamento",
        tokenOwnerA,
        Map.of("imediato", true, "motivo", "De novo", "revisao", revisao(tokenOwnerA)),
        409);
  }

  @Test
  void reativacaoRevogaCancelamentoAgendadoEVoltaAOperar() throws Exception {
    post(
        BASE + "/cancelamento",
        tokenOwnerA,
        Map.of("imediato", false, "motivo", "Repensando", "revisao", revisao(tokenOwnerA)),
        200);
    post(BASE + "/reativacao", tokenOwnerA, Map.of("revisao", revisao(tokenOwnerA)), 200);
    var a = get(BASE, tokenOwnerA, 200).path("assinatura");
    assertThat(a.path("canceladaEm").isNull()).isTrue();
    assertThat(a.path("permiteCrescer").asBoolean()).isTrue();
    assertThat(tipos(tokenOwnerA)).contains("SUBSCRIPTION_REACTIVATED");
    novaOs(tokenOwnerA, 201);
  }

  // -------------------------------------------------------------- webhook e inadimplência

  @Test
  void webhookValidoAtivaAssinaturaEGeraAuditoria() throws Exception {
    var resposta = enviarWebhook(UUID.randomUUID().toString(), "pagamento.aprovado", oficinaA, 200);
    assertThat(resposta.path("status").asText()).isEqualTo("PROCESSADO");
    assertThat(get(BASE, tokenOwnerA, 200).path("assinatura").path("status").asText())
        .isEqualTo("ATIVA");
    assertThat(tipos(tokenOwnerA))
        .contains("WEBHOOK_RECEIVED", "WEBHOOK_PROCESSED", "PAYMENT_APPROVED");
  }

  @Test
  void webhookSemAssinaturaOuComAssinaturaErradaEhRecusado() throws Exception {
    var corpo = json.writeValueAsString(Map.of("id", "x", "tipo", "pagamento.aprovado"));
    mvc.perform(MockMvcRequestBuilders.post(WEBHOOK).contentType("application/json").content(corpo))
        .andExpect(MockMvcResultMatchers.status().is(401));
    mvc.perform(
            MockMvcRequestBuilders.post(WEBHOOK)
                .contentType("application/json")
                .header("X-Garagem-Signature", "00ff")
                .content(corpo))
        .andExpect(MockMvcResultMatchers.status().is(401));
    // Corpo alterado depois de assinado invalida o HMAC: nada é gravado.
    mvc.perform(
            MockMvcRequestBuilders.post(WEBHOOK)
                .contentType("application/json")
                .header("X-Garagem-Signature", assinar(corpo))
                .content(corpo.replace("pagamento.aprovado", "assinatura.cancelada")))
        .andExpect(MockMvcResultMatchers.status().is(401));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_pagamento where provider_event_id='x'",
                Integer.class))
        .isZero();
  }

  @Test
  void webhookDuplicadoNaoReprocessa() throws Exception {
    String id = UUID.randomUUID().toString();
    assertThat(enviarWebhook(id, "pagamento.aprovado", oficinaA, 200).path("status").asText())
        .isEqualTo("PROCESSADO");
    assertThat(enviarWebhook(id, "pagamento.aprovado", oficinaA, 200).path("status").asText())
        .isEqualTo("DUPLICADO");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_pagamento where provider_event_id=?",
                Integer.class,
                id))
        .isEqualTo(1);
    assertThat(tipos(tokenOwnerA).stream().filter("PAYMENT_APPROVED"::equals).count()).isEqualTo(1);
  }

  @Test
  void tipoDesconhecidoEhRegistradoSemAtivarNada() throws Exception {
    var resposta = enviarWebhook(UUID.randomUUID().toString(), "evento.inventado", oficinaA, 200);
    assertThat(resposta.path("status").asText()).isEqualTo("IGNORADO");
    assertThat(get(BASE, tokenOwnerA, 200).path("assinatura").path("status").asText())
        .isEqualTo("TRIAL");
    assertThat(tipos(tokenOwnerA)).contains("WEBHOOK_FAILED").doesNotContain("PAYMENT_APPROVED");
  }

  @Test
  void falhaDePagamentoEntraEmToleranciaESuspendeDepoisDoPrazo() throws Exception {
    enviarWebhook(UUID.randomUUID().toString(), "pagamento.aprovado", oficinaA, 200);
    enviarWebhook(UUID.randomUUID().toString(), "pagamento.falhou", oficinaA, 200);
    var emAtraso = get(BASE, tokenOwnerA, 200).path("assinatura");
    assertThat(emAtraso.path("status").asText()).isEqualTo("INADIMPLENTE");
    // Durante a tolerância a oficina continua operando normalmente.
    assertThat(emAtraso.path("permiteCrescer").asBoolean()).isTrue();
    novaOs(tokenOwnerA, 201);

    String veiculoId = veiculo(tokenOwnerA, 201).path("id").asText();

    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(8));
    var suspensa = get(BASE, tokenOwnerA, 200).path("assinatura");
    assertThat(suspensa.path("status").asText()).isEqualTo("SUSPENSA");
    assertThat(novaOsCom(tokenOwnerA, veiculoId, 402).path("code").asText())
        .isEqualTo("SUBSCRIPTION_INACTIVE");
    assertThat(tipos(tokenOwnerA))
        .contains("PAYMENT_FAILED", "ACCOUNT_PAST_DUE", "ACCOUNT_SUSPENDED");
  }

  @Test
  void suspensaMantemLeituraExportacaoEAreaFinanceira() throws Exception {
    enviarWebhook(UUID.randomUUID().toString(), "pagamento.falhou", oficinaA, 200);
    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(8));
    assertThat(get(BASE, tokenOwnerA, 200).path("assinatura").path("status").asText())
        .isEqualTo("SUSPENSA");
    get("/api/v1/ordens-servico", tokenOwnerA, 200);
    get("/api/v1/clientes", tokenOwnerA, 200);
    get("/api/v1/dashboard", tokenOwnerA, 200);
    get(BASE + "/consumo", tokenOwnerA, 200);
    get(BASE + "/eventos", tokenOwnerA, 200);
  }

  @Test
  void pagamentoConfirmadoReativaAutomaticamente() throws Exception {
    enviarWebhook(UUID.randomUUID().toString(), "pagamento.falhou", oficinaA, 200);
    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(8));
    get(BASE, tokenOwnerA, 200);
    // Nenhuma intervenção manual no banco: o próprio webhook restaura o acesso.
    enviarWebhook(UUID.randomUUID().toString(), "pagamento.aprovado", oficinaA, 200);
    var a = get(BASE, tokenOwnerA, 200).path("assinatura");
    assertThat(a.path("status").asText()).isEqualTo("ATIVA");
    assertThat(a.path("suspensaEm").isNull()).isTrue();
    assertThat(a.path("inadimplenteDesde").isNull()).isTrue();
    assertThat(tipos(tokenOwnerA)).contains("ACCOUNT_REACTIVATED");
    novaOs(tokenOwnerA, 201);
  }

  // -------------------------------------------------------------- segurança e isolamento

  @Test
  void mecanicoNaoAcessaAreaFinanceiraESemSessaoTambemNao() throws Exception {
    for (String path : List.of(BASE, BASE + "/consumo", BASE + "/eventos")) {
      get(path, tokenMecanicoA, 403);
      get(path, null, 401);
    }
    put(BASE + "/plano", tokenMecanicoA, Map.of("codigo", "PREMIUM", "revisao", 0), 403);
    post(
        BASE + "/cancelamento",
        tokenMecanicoA,
        Map.of("imediato", true, "motivo", "x", "revisao", 0),
        403);
  }

  @Test
  void atendenteLeMasNaoDecideContrato() throws Exception {
    get(BASE, tokenAtendenteA, 200);
    get(BASE + "/consumo", tokenAtendenteA, 200);
    put(
        BASE + "/plano",
        tokenAtendenteA,
        Map.of("codigo", "PREMIUM", "revisao", revisao(tokenAtendenteA)),
        403);
    post(
        BASE + "/cancelamento",
        tokenAtendenteA,
        Map.of("imediato", true, "motivo", "x", "revisao", 0),
        403);
    post(BASE + "/reativacao", tokenAtendenteA, Map.of("revisao", 0), 403);
  }

  @Test
  void oficinaNaoVeAssinaturaNemConsumoNemEventosDaOutra() throws Exception {
    usarPlano(oficinaB, "PREMIUM");
    put(
        BASE + "/plano",
        tokenOwnerA,
        Map.of("codigo", "BASICO", "revisao", revisao(tokenOwnerA)),
        200);
    // Cada token enxerga exclusivamente a própria assinatura; não há id de oficina na requisição.
    assertThat(get(BASE, tokenOwnerA, 200).path("assinatura").path("plano").path("codigo").asText())
        .isEqualTo("BASICO");
    assertThat(get(BASE, tokenOwnerB, 200).path("assinatura").path("plano").path("codigo").asText())
        .isEqualTo("PREMIUM");
    assertThat(get(BASE, tokenOwnerA, 200).path("assinatura").path("id").asText())
        .isNotEqualTo(get(BASE, tokenOwnerB, 200).path("assinatura").path("id").asText());

    veiculo(tokenOwnerB, 201);
    var consumoA = get(BASE + "/consumo", tokenOwnerA, 200);
    assertThat(veiculosDe(consumoA)).isZero();
    assertThat(veiculosDe(get(BASE + "/consumo", tokenOwnerB, 200))).isEqualTo(1);

    enviarWebhook(UUID.randomUUID().toString(), "pagamento.aprovado", oficinaB, 200);
    assertThat(tipos(tokenOwnerB)).contains("PAYMENT_APPROVED");
    assertThat(tipos(tokenOwnerA)).doesNotContain("PAYMENT_APPROVED");
  }

  @Test
  void webhookNaoDeixaOPayloadEscolherAOficinaAlvo() throws Exception {
    comAssinaturaExterna(oficinaA);
    // Assinatura externa inexistente: o evento é registrado, não aplicado, e ninguém é ativado.
    var corpo =
        json.writeValueAsString(
            Map.of(
                "id",
                UUID.randomUUID().toString(),
                "tipo",
                "pagamento.aprovado",
                "assinaturaExterna",
                "manual:" + UUID.randomUUID()));
    var resposta =
        corpo(
            mvc.perform(
                    MockMvcRequestBuilders.post(WEBHOOK)
                        .contentType("application/json")
                        .header("X-Garagem-Signature", assinar(corpo))
                        .content(corpo))
                .andExpect(MockMvcResultMatchers.status().is(200))
                .andReturn());
    assertThat(resposta.path("status").asText()).isEqualTo("IGNORADO");
    assertThat(get(BASE, tokenOwnerA, 200).path("assinatura").path("status").asText())
        .isEqualTo("TRIAL");
    assertThat(get(BASE, tokenOwnerB, 200).path("assinatura").path("status").asText())
        .isEqualTo("TRIAL");
  }

  @Test
  void logDeCobrancaNaoGuardaSegredo() throws Exception {
    assertThat(
            br.com.garagem.assinatura.application.CobrancaService.higienizar(
                Map.of(
                    "plano", "PREMIUM",
                    "senha", "SenhaSegura123!",
                    "apiKey", "sk_live_123",
                    "cardNumber", "4111111111111111",
                    "cvv", "123",
                    "webhook_secret", "abc")))
        .isEqualTo("plano=PREMIUM");
    enviarWebhook(UUID.randomUUID().toString(), "pagamento.aprovado", oficinaA, 200);
    var eventos = get(BASE + "/eventos", tokenOwnerA, 200).toString();
    assertThat(eventos).doesNotContain(SENHA).doesNotContain(SEGREDO).doesNotContain("senha_hash");
  }

  @Test
  void historicoDeCobrancaEhImutavelNoBanco() throws Exception {
    enviarWebhook(UUID.randomUUID().toString(), "pagamento.aprovado", oficinaA, 200);
    assertThatThrownBy(
            () ->
                jdbc.update("update evento_cobranca set mensagem='x' where oficina_id=?", oficinaA))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("delete from evento_cobranca where oficina_id=?", oficinaA))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void openApiPublicaOsContratosDaFase7() throws Exception {
    var api = get("/v3/api-docs", null, 200);
    for (String caminho :
        List.of(
            "/api/v1/assinatura",
            "/api/v1/assinatura/consumo",
            "/api/v1/assinatura/plano",
            "/api/v1/assinatura/cancelamento",
            "/api/v1/assinatura/reativacao",
            "/api/v1/assinatura/eventos",
            "/api/v1/webhooks/pagamento")) assertThat(api.path("paths").has(caminho)).isTrue();
    assertThat(api.path("components").path("schemas").has("AssinaturaDetalhe")).isTrue();
    assertThat(api.path("components").path("schemas").has("ConsumoSaida")).isTrue();
  }

  // -------------------------------------------------------------- leitura de apoio

  List<String> tipos(String token) throws Exception {
    var saida = new ArrayList<String>();
    get(BASE + "/eventos?pagina=0&tamanho=100", token, 200)
        .path("itens")
        .forEach(e -> saida.add(e.path("tipo").asText()));
    return saida;
  }

  long veiculosDe(JsonNode consumo) {
    for (var limite : consumo.path("limites"))
      if ("veiculos".equals(limite.path("chave").asText())) return limite.path("usado").asLong();
    return -1;
  }
}
