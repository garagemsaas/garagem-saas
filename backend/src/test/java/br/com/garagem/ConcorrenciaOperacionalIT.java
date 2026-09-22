package br.com.garagem;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Concorrência real nos caminhos de criação do núcleo operacional.
 *
 * <p>Existe porque a suíte anterior testava sequencialmente o que só falha em paralelo. Seis
 * chamadas partem ao mesmo tempo, de verdade, e todas precisam chegar ao banco: perder uma criação
 * concorrente — ou gravá-la duas vezes — é o tipo de defeito que passa por dezenas de verificações
 * verdes e só aparece na oficina cheia.
 *
 * <p>Regra desta classe: <b>o estado principal de cada cenário é produzido pela própria
 * aplicação</b>. SQL só aparece para montar o cenário de partida (empresa, usuário), nunca para
 * criar o efeito sob teste.
 *
 * <p>Nasceu como {@code Fase9ConcorrenciaIT}, quando cada uma destas criações passava antes por uma
 * checagem de cota de plano. A cobrança saiu do produto; a concorrência do caminho de criação é o
 * que sempre importou, e é o que ficou.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ConcorrenciaOperacionalIT extends br.com.garagem.suporte.IntegracaoBase {

  /** Propriedades específicas desta suíte; a origem de dados vem da base. */
  @org.springframework.test.context.DynamicPropertySource
  static void propriedadesDaSuite(org.springframework.test.context.DynamicPropertyRegistry r) {
    r.add("app.seguranca.rate-limit.habilitado", () -> false);
    // Concorrência real exige conexões de verdade: com pool pequeno o teste mediria o pool.
    r.add("spring.datasource.hikari.maximum-pool-size", () -> 16);
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
    if (hash == null) hash = encoder.encode(SENHA);
    oficina = UUID.randomUUID();
    slug = "c-" + oficina;
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", oficina, "Concorrência", slug);
    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
        UUID.randomUUID(),
        oficina,
        "Owner",
        "owner@c.test",
        hash);
    token = login("owner@c.test");
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

  // ---------------------------------------------------------------- criações concorrentes

  @Test
  void criacaoConcorrenteDeUsuariosGravaTodosOsRegistros() throws Exception {
    criarUsuario();
    assertThat(contar("usuario")).isEqualTo(2);

    var codigos = emParalelo(6, this::criarUsuario);

    assertThat(Collections.frequency(codigos, 201)).isEqualTo(6);
    assertThat(contar("usuario")).as("ativos finais").isEqualTo(8);
  }

  @Test
  void criacaoConcorrenteDeVeiculosGravaTodosOsRegistros() throws Exception {
    String cliente = clienteNovo();
    criarVeiculo(cliente);

    var codigos = emParalelo(6, () -> criarVeiculo(cliente));

    assertThat(Collections.frequency(codigos, 201)).isEqualTo(6);
    assertThat(contar("veiculo")).isEqualTo(7);
  }

  @Test
  void criacaoConcorrenteDeOrdensGravaTodosOsRegistros() throws Exception {
    String cliente = clienteNovo();
    criarVeiculo(cliente);
    String veiculo =
        jdbc.queryForObject(
            "select cast(id as text) from veiculo where oficina_id=?", String.class, oficina);
    criarOs(veiculo);

    var codigos = emParalelo(6, () -> criarOs(veiculo));

    assertThat(Collections.frequency(codigos, 201)).isEqualTo(6);
    assertThat(contar("ordem_servico")).isEqualTo(7);
  }

  @Test
  void uploadsConcorrentesGravamTodasAsFotos() throws Exception {
    String cliente = clienteNovo();
    criarVeiculo(cliente);
    String veiculo =
        jdbc.queryForObject(
            "select cast(id as text) from veiculo where oficina_id=?", String.class, oficina);
    criarOs(veiculo);
    String os =
        jdbc.queryForObject(
            "select cast(id as text) from ordem_servico where oficina_id=?", String.class, oficina);

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

  // ---------------------------------------------------------------- persistência

  /**
   * O adapter insere entidade nova sem passar por {@code merge}, e o que é definido depois do
   * {@code save} precisa chegar ao banco. Enquanto caía em merge, um campo gravado assim sumia
   * calado — sem erro, sem log, sem teste vermelho.
   */
  @Test
  void entidadeNovaEhInseridaSemMergeEMutacaoPosSaveChegaAoBanco() throws Exception {
    String cliente = clienteNovo();
    assertThat(criarVeiculo(cliente)).isEqualTo(201);
    String veiculo =
        jdbc.queryForObject(
            "select cast(id as text) from veiculo where oficina_id=?", String.class, oficina);
    assertThat(criarOs(veiculo)).isEqualTo(201);

    // criado_em é definido pela aplicação no mesmo caminho de gravação.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ordem_servico where oficina_id=? and criado_em is not null",
                Long.class,
                oficina))
        .isEqualTo(1);
  }
}
