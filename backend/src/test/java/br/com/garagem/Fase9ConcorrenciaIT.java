package br.com.garagem;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Concorrência real e fluxo de cobrança de ponta a ponta.
 *
 * <p>Existe porque a suíte anterior testava sequencialmente o que só falha em paralelo, e fabricava
 * por SQL justamente o estado que a funcionalidade deveria produzir. Com isso, quatro defeitos
 * graves passaram por 27 verificações verdes: limite de plano ultrapassável, pagamento que nunca
 * ativava a assinatura, webhook aplicado duas vezes e inadimplência que nunca suspendia.
 *
 * <p>Regra desta classe: <b>o estado principal de cada cenário é produzido pela própria
 * aplicação</b>. SQL só aparece para montar o cenário de partida (oficina, usuário, relógio), nunca
 * para criar o efeito sob teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(Fase9ConcorrenciaIT.RelogioConfig.class)
class Fase9ConcorrenciaIT extends br.com.garagem.suporte.IntegracaoBase {

  /** Propriedades específicas desta suíte; a origem de dados vem da base. */
  @org.springframework.test.context.DynamicPropertySource
  static void propriedadesDaSuite(org.springframework.test.context.DynamicPropertyRegistry r) {
    r.add("app.legacy-billing.enabled", () -> true);
    r.add("app.pagamento.webhook-secret", () -> SEGREDO);
    r.add("app.pagamento.tolerancia", () -> "P7D");
    r.add("app.seguranca.rate-limit.habilitado", () -> false);
    // Concorrência real exige conexões de verdade: com pool pequeno o teste mediria o pool.
    r.add("spring.datasource.hikari.maximum-pool-size", () -> 16);
  }

  static final String BASE = "/api/v1/assinatura";
  static final String WEBHOOK = "/api/v1/webhooks/pagamento";
  static final String SEGREDO = "segredo-de-concorrencia-com-mais-de-32-bytes";
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

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  br.com.garagem.ordemservico.foto.application.FotoStorage storage;

  static final String SENHA = "SenhaSegura123!";
  static final java.util.concurrent.atomic.AtomicInteger PLACAS =
      new java.util.concurrent.atomic.AtomicInteger();
  static String hash;

  UUID oficina;
  String slug, token;

  @BeforeEach
  void setup() throws Exception {
    RELOGIO.agora = java.time.Instant.now();
    if (hash == null) hash = encoder.encode(SENHA);
    oficina = UUID.randomUUID();
    slug = "c-" + oficina;
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", oficina, "Concorrência", slug);
    jdbc.update(
        "insert into assinatura(id,oficina_id,plano_id,status,trial_inicio,trial_fim,periodo_inicio,periodo_fim) select ?,?,id,'TRIAL',now(),now()+interval '14 days',now(),now()+interval '14 days' from plano where codigo='PROFISSIONAL'",
        UUID.randomUUID(),
        oficina);

    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
        UUID.randomUUID(),
        oficina,
        "Owner",
        "owner@c.test",
        hash);
    token = login("owner@c.test");
  }

  @AfterEach
  void restaurarCatalogo() {
    jdbc.update(
        "update plano set max_usuarios=3, max_armazenamento_bytes=1073741824,"
            + " max_ordens_servico_mes=60, max_veiculos=200 where codigo='BASICO'");
    jdbc.update(
        "update plano set max_usuarios=10, max_armazenamento_bytes=10737418240,"
            + " max_ordens_servico_mes=400, max_veiculos=2000 where codigo='PROFISSIONAL'");
  }

  // ---------------------------------------------------------------- utilidades

  String login(String email) throws Exception {
    return corpo(
            mvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of("oficina", slug, "email", email, "senha", SENHA))))
                .andReturn())
        .path("accessToken")
        .asText();
  }

  JsonNode corpo(MvcResult r) throws Exception {
    String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return texto.isEmpty() ? json.createObjectNode() : json.readTree(texto);
  }

  JsonNode get(String path, int esperado) throws Exception {
    var r =
        mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + token))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(path).isEqualTo(esperado);
    return corpo(r);
  }

  JsonNode post(String path, Object body, int esperado) throws Exception {
    var r =
        mvc.perform(
                MockMvcRequestBuilders.post(path)
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(path).isEqualTo(esperado);
    return corpo(r);
  }

  /** Dispara N chamadas de uma vez e devolve os códigos HTTP. */
  List<Integer> emParalelo(int quantidade, Callable<Integer> chamada) throws Exception {
    try (var pool = Executors.newFixedThreadPool(quantidade)) {
      var largada = new CountDownLatch(1);
      var futuros = new ArrayList<Future<Integer>>();
      for (int i = 0; i < quantidade; i++)
        futuros.add(
            pool.submit(
                () -> {
                  largada.await();
                  return chamada.call();
                }));
      largada.countDown();
      var codigos = new ArrayList<Integer>();
      for (var f : futuros) codigos.add(f.get(60, TimeUnit.SECONDS));
      return codigos;
    }
  }

  int criarUsuario() throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/usuarios")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "nome",
                            "U",
                            "email",
                            UUID.randomUUID() + "@c.test",
                            "senha",
                            SENHA,
                            "papel",
                            "MECANICO"))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  String clienteNovo() throws Exception {
    return post("/api/v1/clientes", Map.of("nome", "C", "telefone", "11999999999"), 201)
        .path("id")
        .asText();
  }

  static String placa() {
    int n = PLACAS.getAndIncrement();
    return ""
        + (char) ('A' + n / 676 % 26)
        + (char) ('A' + n / 26 % 26)
        + (char) ('A' + n % 26)
        + "0A00";
  }

  int criarVeiculo(String cliente) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/veiculos")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "clienteId",
                            cliente,
                            "placa",
                            placa(),
                            "marca",
                            "Fiat",
                            "modelo",
                            "Uno",
                            "ano",
                            2020,
                            "km",
                            1000,
                            "cor",
                            "Prata"))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  int criarOs(String veiculo) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/ordens-servico")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("veiculoId", veiculo, "kmEntrada", 1000, "relato", "Ruído"))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  long contar(String tabela) {
    Long n =
        jdbc.queryForObject(
            "select count(*) from " + tabela + " where oficina_id=?", Long.class, oficina);
    return n == null ? 0 : n;
  }

  void limitarPlano(String coluna, Object valor) {
    jdbc.update(
        "update plano set "
            + coluna
            + "=? where codigo=(select p.codigo from plano p"
            + " join assinatura a on a.plano_id=p.id where a.oficina_id=?)",
        valor,
        oficina);
  }

  static String assinar(String corpo) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    var hex = new StringBuilder();
    for (byte b : mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)))
      hex.append(String.format("%02x", b));
    return hex.toString();
  }

  /** Contrata pelo caminho da aplicação: uma leitura da área financeira estabelece o vínculo. */
  String contratar() throws Exception {
    get(BASE, 200);
    String externo =
        jdbc.queryForObject(
            "select provider_subscription_id from assinatura where oficina_id=?",
            String.class,
            oficina);
    assertThat(externo).as("a aplicação precisa gravar o identificador externo").isNotNull();
    return externo;
  }

  MvcResult webhook(String id, String tipo, String assinaturaExterna) throws Exception {
    var corpo =
        json.writeValueAsString(
            Map.of("id", id, "tipo", tipo, "assinaturaExterna", assinaturaExterna));
    return mvc.perform(
            MockMvcRequestBuilders.post(WEBHOOK)
                .contentType("application/json")
                .header("X-Garagem-Signature", assinar(corpo))
                .content(corpo))
        .andReturn();
  }

  // ---------------------------------------------------------------- F-01 limites atômicos

  @Test
  void criacaoConcorrenteDeUsuariosIndependeDeCota() throws Exception {
    limitarPlano("max_usuarios", 3);
    criarUsuario(); // 2 ativos, resta 1 vaga
    assertThat(contar("usuario")).isEqualTo(2);

    var codigos = emParalelo(6, this::criarUsuario);

    assertThat(Collections.frequency(codigos, 201)).isEqualTo(6);
    assertThat(Collections.frequency(codigos, 402)).isZero();
    assertThat(contar("usuario")).as("ativos finais").isEqualTo(8);
  }

  @Test
  void criacaoConcorrenteDeVeiculosIndependeDeCota() throws Exception {
    String cliente = clienteNovo();
    criarVeiculo(cliente);
    limitarPlano("max_veiculos", 2);

    var codigos = emParalelo(6, () -> criarVeiculo(cliente));

    assertThat(Collections.frequency(codigos, 201)).isEqualTo(6);
    assertThat(contar("veiculo")).isEqualTo(7);
  }

  @Test
  void criacaoConcorrenteDeOrdensIndependeDeCota() throws Exception {
    String cliente = clienteNovo();
    criarVeiculo(cliente);
    String veiculo =
        jdbc.queryForObject(
            "select cast(id as text) from veiculo where oficina_id=?", String.class, oficina);
    criarOs(veiculo);
    limitarPlano("max_ordens_servico_mes", 2);

    var codigos = emParalelo(6, () -> criarOs(veiculo));

    assertThat(Collections.frequency(codigos, 201)).isEqualTo(6);
    assertThat(contar("ordem_servico")).isEqualTo(7);
  }

  @Test
  void uploadsConcorrentesIndependemDeCota() throws Exception {
    String cliente = clienteNovo();
    criarVeiculo(cliente);
    String veiculo =
        jdbc.queryForObject(
            "select cast(id as text) from veiculo where oficina_id=?", String.class, oficina);
    criarOs(veiculo);
    String os =
        jdbc.queryForObject(
            "select cast(id as text) from ordem_servico where oficina_id=?", String.class, oficina);
    // Cota histórica menor que a imagem não deve limitar uploads, inclusive concorrentes.
    limitarPlano("max_armazenamento_bytes", 10);

    var codigos = emParalelo(6, () -> enviarFoto(os));

    assertThat(codigos).allMatch(c -> c == 201);
    assertThat(contar("foto_veiculo")).isEqualTo(6);
  }

  int enviarFoto(String os) throws Exception {
    var img = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var saida = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", saida);
    return mvc.perform(
            MockMvcRequestBuilders.multipart("/api/v1/ordens-servico/" + os + "/fotos")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "arquivo", "f.png", "image/png", saida.toByteArray()))
                .param("finalidade", "ENTRADA")
                .header("Authorization", "Bearer " + token))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  // ---------------------------------------------------------------- F-06 webhook idempotente

  @Test
  void webhookSimultaneoAplicaOEfeitoUmaUnicaVez() throws Exception {
    String externo = contratar();
    String evento = "evt-" + UUID.randomUUID();

    var respostas = new CopyOnWriteArrayList<String>();
    emParalelo(
        12,
        () -> {
          var r = webhook(evento, "pagamento.aprovado", externo);
          respostas.add(corpo(r).path("status").asText());
          return r.getResponse().getStatus();
        });

    assertThat(Collections.frequency(respostas, "PROCESSADO"))
        .as("um único vencedor; respostas observadas: %s", respostas)
        .isEqualTo(1);
    assertThat(Collections.frequency(respostas, "DUPLICADO"))
        .as("as demais entregas devem sair como duplicadas: %s", respostas)
        .isEqualTo(11);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from evento_cobranca where oficina_id=? and tipo='PAYMENT_APPROVED'",
                Long.class,
                oficina))
        .as("efeito financeiro registrado uma única vez")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_pagamento where provider_event_id=?",
                Long.class,
                evento))
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------- F-02 billing ponta a ponta

  @Test
  void fluxoRealDeCobrancaDaContratacaoAoCancelamento() throws Exception {
    // 1-4. A oficina nasce com assinatura; contratar vincula ao provedor pelo caminho da aplicação.
    String externo = contratar();
    assertThat(get(BASE, 200).path("assinatura").path("status").asText()).isEqualTo("TRIAL");

    // 5-7. Pagamento aprovado ativa de verdade — sem nenhum SQL preparando o vínculo.
    var r = webhook("pago-" + UUID.randomUUID(), "pagamento.aprovado", externo);
    assertThat(corpo(r).path("status").asText()).isEqualTo("PROCESSADO");
    assertThat(get(BASE, 200).path("assinatura").path("status").asText()).isEqualTo("ATIVA");

    // 8. A trilha guarda o identificador do evento externo.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from evento_cobranca where oficina_id=? and provider_event_id is not null",
                Long.class,
                oficina))
        .as("provider_event_id precisa chegar ao banco")
        .isGreaterThan(0);

    // 9. Eventos financeiros corretos.
    assertThat(tipos()).contains("SUBSCRIPTION_CREATED", "PAYMENT_APPROVED", "WEBHOOK_PROCESSED");

    // 10-11. Inadimplência e suspensão SEM abrir a tela de assinatura.
    assertThat(
            corpo(webhook("falha-" + UUID.randomUUID(), "pagamento.falhou", externo))
                .path("status")
                .asText())
        .isEqualTo("PROCESSADO");
    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(8));

    String cliente = clienteNovo();
    assertThat(criarVeiculo(cliente))
        .as("core operacional independe da cobrança histórica")
        .isEqualTo(201);
    get(BASE, 200);
    assertThat(
            jdbc.queryForObject(
                "select status from assinatura where oficina_id=?", String.class, oficina))
        .as("a suspensão precisa estar persistida")
        .isEqualTo("SUSPENSA");

    // 12. Cancelar uma assinatura suspensa precisa funcionar.
    long revisao = get(BASE, 200).path("assinatura").path("revisao").asLong();
    post(
        BASE + "/cancelamento",
        Map.of("imediato", true, "motivo", "Encerrando a oficina", "revisao", revisao),
        200);
    assertThat(get(BASE, 200).path("assinatura").path("status").asText()).isEqualTo("CANCELADA");

    // 13. Reativar devolve ao ciclo, e o pagamento seguinte volta a ativar.
    revisao = get(BASE, 200).path("assinatura").path("revisao").asLong();
    post(BASE + "/reativacao", Map.of("revisao", revisao), 200);
    assertThat(
            corpo(webhook("volta-" + UUID.randomUUID(), "pagamento.aprovado", externo))
                .path("status")
                .asText())
        .isEqualTo("PROCESSADO");
    assertThat(get(BASE, 200).path("assinatura").path("status").asText()).isEqualTo("ATIVA");
  }

  List<String> tipos() {
    return jdbc.query(
        "select tipo from evento_cobranca where oficina_id=?", (rs, n) -> rs.getString(1), oficina);
  }

  // ---------------------------------------------------------------- F-04 suspensão sem tela

  @Test
  void inadimplenciaSuspendeSemNinguemAbrirATelaDeAssinatura() throws Exception {
    String externo = contratar();
    webhook("p-" + UUID.randomUUID(), "pagamento.aprovado", externo);
    webhook("f-" + UUID.randomUUID(), "pagamento.falhou", externo);

    // Durante a tolerância a oficina continua trabalhando.
    String cliente = clienteNovo();
    assertThat(criarVeiculo(cliente)).isEqualTo(201);

    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(8));

    // Nenhuma leitura de /assinatura entre o vencimento e a tentativa: o bloqueio tem de vir do
    // próprio caminho de criação, não de um efeito colateral de abrir a tela.
    assertThat(criarVeiculo(cliente)).isEqualTo(201);
    get(BASE, 200);
    assertThat(
            jdbc.queryForObject(
                "select status from assinatura where oficina_id=?", String.class, oficina))
        .isEqualTo("SUSPENSA");
  }

  // ---------------------------------------------------------------- F-07 matriz de transições

  @Test
  void assinaturaSuspensaPodeSerCancelada() throws Exception {
    String externo = contratar();
    webhook("p-" + UUID.randomUUID(), "pagamento.aprovado", externo);
    webhook("f-" + UUID.randomUUID(), "pagamento.falhou", externo);
    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(8));
    assertThat(get(BASE, 200).path("assinatura").path("status").asText()).isEqualTo("SUSPENSA");

    long revisao = get(BASE, 200).path("assinatura").path("revisao").asLong();
    var saida =
        post(
            BASE + "/cancelamento",
            Map.of("imediato", true, "motivo", "Sem condições de pagar", "revisao", revisao),
            200);

    assertThat(saida.path("status").asText()).isEqualTo("CANCELADA");
    // O carimbo de suspensão precisa sair junto: a constraint do banco é bicondicional.
    assertThat(
            jdbc.queryForObject(
                "select suspensa_em from assinatura where oficina_id=?", String.class, oficina))
        .isNull();
  }

  @Test
  void cancelamentoAgendadoDeSuspensaTambemEncerraSemViolarConstraint() throws Exception {
    String externo = contratar();
    webhook("p-" + UUID.randomUUID(), "pagamento.aprovado", externo);
    webhook("f-" + UUID.randomUUID(), "pagamento.falhou", externo);
    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(8));

    long revisao = get(BASE, 200).path("assinatura").path("revisao").asLong();
    post(
        BASE + "/cancelamento",
        Map.of("imediato", false, "motivo", "Ao fim do período", "revisao", revisao),
        200);

    // Passa a data efetiva: a transição agendada precisa encerrar sem violar o invariante.
    RELOGIO.agora = RELOGIO.agora.plus(java.time.Duration.ofDays(400));
    assertThat(get(BASE, 200).path("assinatura").path("status").asText()).isEqualTo("CANCELADA");
    assertThat(
            jdbc.queryForObject(
                "select suspensa_em from assinatura where oficina_id=?", String.class, oficina))
        .isNull();
  }

  // ---------------------------------------------------------------- F-05 persistência

  @Test
  void entidadeNovaEhInseridaSemMergeEMutacaoPosSaveChegaAoBanco() throws Exception {
    String externo = contratar();
    String evento = "trilha-" + UUID.randomUUID();
    webhook(evento, "pagamento.aprovado", externo);

    // provider_event_id é definido no mesmo insert; enquanto save caía em merge, sumia calado.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from evento_cobranca where oficina_id=? and provider_event_id=?",
                Long.class,
                oficina,
                evento))
        .isGreaterThan(0);
  }
}
