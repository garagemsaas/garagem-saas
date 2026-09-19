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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cobertura da Fase 3 — primeira integração. Exercita exatamente os seis contratos que o frontend
 * consome nesta etapa (login, clientes, veículos, listagem de OS, detalhe de OS e alteração de
 * status), mais os problemas encontrados durante a integração.
 *
 * <p>Não repete o que {@code Fase1IT} e {@code Fase2IT} já afirmam: o fluxo completo da OS, a
 * matriz de permissões operação a operação, o inventário do OpenAPI e os filtros nomeados continuam
 * sendo verificados lá. Aqui ficam as garantias que o consumidor da API depende para integrar sem
 * ler código Java, e as regressões dos defeitos corrigidos nesta fase.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class Fase3IT extends br.com.garagem.suporte.IntegracaoBase {
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

  // -------------------------------------------------------- Contrato 1: login

  @Test
  @DisplayName("Login devolve a sessão completa que o frontend guarda, e nada além dela")
  void contratoDeLogin() throws Exception {
    var sessao = login(slugA, "owner@a.test", SENHA, 200);

    // Os sete campos publicados em docs/api-v1.md, na forma que o cliente tipado espera.
    assertThat(sessao.path("accessToken").asText()).isNotBlank();
    assertThat(sessao.path("refreshToken").asText()).isNotBlank();
    assertThat(sessao.path("expiresIn").asLong()).isPositive();
    assertThat(sessao.path("oficinaId").asText()).isEqualTo(oficinaA.toString());
    assertThat(sessao.path("usuarioId").asText()).isEqualTo(ownerA.toString());
    assertThat(sessao.path("nome").asText()).isNotBlank();
    assertThat(sessao.path("papel").asText()).isEqualTo("OWNER");
    assertThat(campos(sessao)).hasSize(7);

    // Nenhum vestígio de credencial na resposta.
    assertThat(sessao.toString())
        .doesNotContain(SENHA)
        .doesNotContain(hash)
        .doesNotContain("senha");
  }

  @Test
  @DisplayName("Login recusa oficina, e-mail, senha e conta inativa com o mesmo 401 indistinguível")
  void errosDeLogin() throws Exception {
    List<JsonNode> recusas =
        List.of(
            login("oficina-que-nao-existe", "owner@a.test", SENHA, 401),
            login(slugA, "ninguem@a.test", SENHA, 401),
            login(slugA, "owner@a.test", "SenhaErrada123!", 401),
            login(slugA, "inativo@a.test", SENHA, 401),
            // Usuário existe, mas na outra oficina: o slug escopa a busca.
            login(slugB, "owner@a.test", SENHA, 401));
    for (var recusa : recusas) {
      assertThat(recusa.path("code").asText()).isEqualTo("UNAUTHORIZED");
      assertThat(recusa.path("status").asInt()).isEqualTo(401);
    }
    // Todos os corpos são iguais a menos do timestamp/requestId: nada denuncia o que falhou.
    assertThat(recusas.stream().map(r -> r.path("detail").asText()).distinct()).hasSize(1);

    // Validação de request é 400 com os campos recusados, não 401.
    var invalido = login(null, "nao-e-email", "", 400);
    assertThat(invalido.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(invalido.path("errors").findValuesAsText("field"))
        .contains("oficina", "email", "senha");
  }

  @Test
  @DisplayName("A sessão emitida é registrada no log sem colidir com o contexto da requisição")
  void sessaoEmitidaChegaAoLog() throws Exception {
    // Regressão: o evento trazia `oficina_id` e `usuario_id` como key-value enquanto o filtro de
    // acesso já os publicava no MDC. O escritor de log estruturado recusa nome repetido e
    // descartava a linha inteira, então o registro de login sumia do log.
    var logger = (Logger) LoggerFactory.getLogger("br.com.garagem.auth.application.AuthService");
    var capturadas = new ListAppender<ILoggingEvent>();
    capturadas.start();
    logger.addAppender(capturadas);
    try {
      login(slugA, "owner@a.test", SENHA, 200);
    } finally {
      logger.detachAppender(capturadas);
      capturadas.stop();
    }

    var evento =
        capturadas.list.stream()
            .filter(e -> e.getMessage().equals("sessao_emitida"))
            .findFirst()
            .orElseThrow();
    var nomesDeKeyValue =
        evento.getKeyValuePairs() == null
            ? List.<String>of()
            : evento.getKeyValuePairs().stream().map(p -> p.key).toList();
    assertThat(nomesDeKeyValue).doesNotContainAnyElementsOf(evento.getMDCPropertyMap().keySet());
    assertThat(evento.getMDCPropertyMap())
        .containsEntry("oficina_id", oficinaA.toString())
        .containsEntry("usuario_id", ownerA.toString());
  }

  // ---------------------------------------------- Contratos 2 e 3: clientes e veículos

  @Test
  @DisplayName("Cliente e veículo devolvem a revisão nova a cada escrita, para o próximo PUT")
  void contratoDeCadastroDevolveRevisaoUtilizavel() throws Exception {
    var criado =
        post(
            "/api/v1/clientes",
            tokenAtendenteA,
            Map.of("nome", "Ana Paula", "telefone", "11911110000", "email", "ana@exemplo.test"),
            201);
    assertThat(criado.path("revisao").asLong()).isZero();

    var atualizado =
        put(
            "/api/v1/clientes/" + criado.path("id").asText(),
            tokenAtendenteA,
            Map.of(
                "nome", "Ana Paula Souza",
                "telefone", "11911110000",
                "email", "ana@exemplo.test",
                "revisao", 0),
            200);
    // O PUT responde com o estado já gravado: a tela pode reenviar sem recarregar.
    assertThat(atualizado.path("nome").asText()).isEqualTo("Ana Paula Souza");
    assertThat(atualizado.path("revisao").asLong()).isEqualTo(1);

    String c = cliente(tokenAtendenteA, "Dono do carro");
    var veiculo =
        post(
            "/api/v1/veiculos",
            tokenAtendenteA,
            Map.of(
                "clienteId", c,
                "placa", "rst-1d23",
                "marca", "Fiat",
                "modelo", "Uno",
                "ano", 2020,
                "km", 1000,
                "cor", "Prata"),
            201);
    // Placa gravada sem separador e em maiúsculas, como o contrato publica.
    assertThat(veiculo.path("placa").asText()).isEqualTo("RST1D23");
    assertThat(veiculo.path("revisao").asLong()).isZero();
    assertThat(veiculo.path("clienteId").asText()).isEqualTo(c);
  }

  @Test
  @DisplayName("A pesquisa de veículo encontra marca e modelo com espaço e com hífen")
  void buscaDeVeiculoNaoNormalizaMarcaEModelo() throws Exception {
    // Regressão: o campo único de pesquisa era normalizado como placa antes de comparar, então
    // apagava espaço e hífen também de marca e modelo. "Land Rover" e "CR-V" não achavam nada.
    String c = cliente(tokenOwnerA, "Cliente Busca");
    veiculo(tokenOwnerA, c, "LRV1A23", "Land Rover", "Evoque");
    veiculo(tokenOwnerA, c, "HND2B34", "Honda", "CR-V");

    assertThat(
            getP("/api/v1/veiculos", tokenOwnerA, 200, "busca", "Land Rover")
                .path("total")
                .asLong())
        .isEqualTo(1);
    assertThat(getP("/api/v1/veiculos", tokenOwnerA, 200, "busca", "CR-V").path("total").asLong())
        .isEqualTo(1);
    // E a placa continua encontrável com e sem separador.
    assertThat(
            getP("/api/v1/veiculos", tokenOwnerA, 200, "busca", "lrv-1a23").path("total").asLong())
        .isEqualTo(1);
    assertThat(
            getP("/api/v1/veiculos", tokenOwnerA, 200, "busca", "HND2B34").path("total").asLong())
        .isEqualTo(1);
    // Pesquisa só de separadores não pode virar predicado vazio e devolver a base inteira:
    // "-" é texto literal, então casa com o modelo "CR-V" e com mais nada.
    assertThat(getP("/api/v1/veiculos", tokenOwnerA, 200, "busca", "-").findValuesAsText("modelo"))
        .containsExactly("CR-V");
  }

  // -------------------------------------------- Contrato 4: listagem de ordens de serviço

  @Test
  @DisplayName("A pesquisa da listagem de OS encontra a placa digitada com hífen")
  void buscaDeOsNormalizaAPlaca() throws Exception {
    // Regressão: o filtro dedicado `placa` normalizava, mas o campo único `busca` não, então a
    // mesma placa digitada com hífen achava pelo filtro e não achava pela pesquisa.
    String c = cliente(tokenOwnerA, "Marina Alves");
    String osId = os(tokenOwnerA, veiculo(tokenOwnerA, c, "XYZ9Z88", "Fiat", "Uno"));
    long numero = get("/api/v1/ordens-servico/" + osId, tokenOwnerA, 200).path("numero").asLong();

    for (String pesquisa : List.of("xyz-9z88", "XYZ9Z88", "xyz 9z88")) {
      assertThat(
              getP("/api/v1/ordens-servico", tokenOwnerA, 200, "busca", pesquisa)
                  .findValuesAsText("id"))
          .as("busca por %s", pesquisa)
          .containsExactly(osId);
    }
    // Nome do cliente e número continuam casando com o texto original.
    assertThat(
            getP("/api/v1/ordens-servico", tokenOwnerA, 200, "busca", "marina")
                .findValuesAsText("id"))
        .containsExactly(osId);
    assertThat(
            getP("/api/v1/ordens-servico", tokenOwnerA, 200, "busca", String.valueOf(numero))
                .findValuesAsText("id"))
        .containsExactly(osId);
  }

  @Test
  @DisplayName("Período de abertura aceita só o limite inferior, só o superior e os dois juntos")
  void periodoDeAberturaEmQualquerCombinacao() throws Exception {
    // Regressão do 42P18: com `de` ausente a consulta não pode mandar parâmetro temporal sem tipo.
    String osId = osCompleta(tokenOwnerA);
    String antes = "2000-01-01T00:00:00Z";
    String depois = "2100-01-01T00:00:00Z";

    assertThat(get("/api/v1/ordens-servico", tokenOwnerA, 200).findValuesAsText("id"))
        .containsExactly(osId);
    assertThat(get("/api/v1/ordens-servico?de=" + antes, tokenOwnerA, 200).findValuesAsText("id"))
        .containsExactly(osId);
    assertThat(get("/api/v1/ordens-servico?ate=" + depois, tokenOwnerA, 200).findValuesAsText("id"))
        .containsExactly(osId);
    assertThat(
            get("/api/v1/ordens-servico?de=" + antes + "&ate=" + depois, tokenOwnerA, 200)
                .findValuesAsText("id"))
        .containsExactly(osId);
    assertThat(get("/api/v1/ordens-servico?de=" + depois, tokenOwnerA, 200).path("total").asLong())
        .isZero();
  }

  @Test
  @DisplayName("A listagem de OS entrega o envelope de página publicado, inclusive além do fim")
  void envelopeDaListagemDeOs() throws Exception {
    osCompleta(tokenOwnerA);
    osCompleta(tokenOwnerA);
    osCompleta(tokenOwnerA);

    var primeira = get("/api/v1/ordens-servico?pagina=0&tamanho=2", tokenOwnerA, 200);
    assertThat(campos(primeira)).hasSize(5);
    assertThat(primeira.path("itens").size()).isEqualTo(2);
    assertThat(primeira.path("pagina").asInt()).isZero();
    assertThat(primeira.path("tamanho").asInt()).isEqualTo(2);
    assertThat(primeira.path("total").asLong()).isEqualTo(3);
    assertThat(primeira.path("totalPaginas").asInt()).isEqualTo(2);

    var alem = get("/api/v1/ordens-servico?pagina=9&tamanho=2", tokenOwnerA, 200);
    assertThat(alem.path("itens").size()).isZero();
    assertThat(alem.path("total").asLong()).isEqualTo(3);
  }

  // ------------------------------------------------ Contrato 5: detalhe da ordem de serviço

  @Test
  @DisplayName("O detalhe da OS é montado pelas chamadas que o frontend faz, todas em 200")
  void contratoDeDetalheDeOs() throws Exception {
    String osId = osCompleta(tokenOwnerA);
    String base = "/api/v1/ordens-servico/" + osId;

    var principal = get(base, tokenOwnerA, 200);
    assertThat(campos(principal))
        .containsExactlyInAnyOrder(
            "id",
            "numero",
            "veiculoId",
            "clienteId",
            "mecanicoId",
            "status",
            "kmEntrada",
            "relato",
            "criadoEm",
            "previsaoEntrega",
            "concluidaEm",
            "revisao",
            // Distingue a OS de cliente da OS interna de preparação de veículo do estoque.
            "tipo");
    assertThat(principal.path("status").asText()).isEqualTo("RECEBIDO");

    // Cliente e veículo vêm por id: a tela resolve pelos próprios contratos, não por join na OS.
    get("/api/v1/veiculos/" + principal.path("veiculoId").asText(), tokenOwnerA, 200);
    get("/api/v1/clientes/" + principal.path("clienteId").asText(), tokenOwnerA, 200);

    // Coleções da OS existem desde a abertura e respondem vazias, não 404.
    assertThat(get(base + "/diagnosticos", tokenOwnerA, 200).size()).isZero();
    assertThat(get(base + "/orcamento/versoes", tokenOwnerA, 200).size()).isZero();
    assertThat(get(base + "/fotos", tokenOwnerA, 200).size()).isZero();
    assertThat(get(base + "/timeline", tokenOwnerA, 200).size()).isEqualTo(1);

    // O checklist é a única parte que responde 404 enquanto não existe; o cliente trata o 404.
    assertThat(get(base + "/checklist", tokenOwnerA, 404).path("code").asText())
        .isEqualTo("NOT_FOUND");
    post(
        base + "/checklist",
        tokenOwnerA,
        Map.of(
            "observacoes",
            "Entrada sem avarias",
            "itens",
            List.of(Map.of("descricao", "Pneus", "condicao", "OK"))),
        201);
    assertThat(get(base + "/checklist", tokenOwnerA, 200).path("itens").size()).isEqualTo(1);
  }

  // ------------------------------------------------ Contrato 6: alteração de status

  @Test
  @DisplayName("O status só avança pelas transições da máquina de estados, nunca por escolha livre")
  void contratoDeAlteracaoDeStatus() throws Exception {
    String osId = osCompleta(tokenOwnerA);

    // Salto arbitrário a partir de RECEBIDO é recusado com conflito, não aceito.
    for (String proibido :
        List.of("ORCAMENTO", "EM_MANUTENCAO", "AGUARDANDO_PECA", "TESTE", "PRONTO", "RECEBIDO")) {
      assertThat(mudarStatus(tokenOwnerA, osId, proibido, 409).path("code").asText())
          .as("RECEBIDO → %s", proibido)
          .isEqualTo("CONFLICT");
    }
    // Status fora do enum é request inválido, não conflito.
    assertThat(
            post(
                    "/api/v1/ordens-servico/" + osId + "/status",
                    tokenOwnerA,
                    Map.of("status", "INVENTADO", "revisao", revisao(tokenOwnerA, osId)),
                    400)
                .path("code")
                .asText())
        .isEqualTo("INVALID_REQUEST");

    // A transição permitida responde com a OS já no novo status e com a revisão seguinte.
    long antes = revisao(tokenOwnerA, osId);
    var avancada = mudarStatus(tokenOwnerA, osId, "DIAGNOSTICO", 200);
    assertThat(avancada.path("status").asText()).isEqualTo("DIAGNOSTICO");
    assertThat(avancada.path("revisao").asLong()).isGreaterThan(antes);

    // Revisão desatualizada não sobrescreve em silêncio.
    assertThat(
            post(
                    "/api/v1/ordens-servico/" + osId + "/status",
                    tokenOwnerA,
                    Map.of("status", "ORCAMENTO", "revisao", antes),
                    409)
                .path("code")
                .asText())
        .isEqualTo("CONFLICT");

    // Entrar em manutenção é decisão do cliente pelo link, não da API interna.
    mudarStatus(tokenOwnerA, osId, "ORCAMENTO", 200);
    post(
        "/api/v1/ordens-servico/" + osId + "/orcamento/versoes",
        tokenOwnerA,
        Map.of(
            "itens",
            List.of(
                Map.of(
                    "tipo", "SERVICO",
                    "descricao", "Revisão",
                    "quantidade", "1.000",
                    "valorUnitario", "150.00"))),
        201);
    mudarStatus(tokenOwnerA, osId, "AGUARDANDO_APROVACAO", 200);
    assertThat(mudarStatus(tokenOwnerA, osId, "EM_MANUTENCAO", 409).path("code").asText())
        .isEqualTo("CONFLICT");
  }

  @Test
  @DisplayName("Mecânico movimenta o status, mas não abre OS nem mexe em cadastro")
  void autorizacaoNosContratosDaPrimeiraIntegracao() throws Exception {
    String c = cliente(tokenAtendenteA, "Cliente do Mecânico");
    String v = veiculo(tokenAtendenteA, c, placaUnica(), "Fiat", "Uno");
    String osId = os(tokenAtendenteA, v);

    // Leitura é de todos os papéis.
    get("/api/v1/clientes", tokenMecanicoA, 200);
    get("/api/v1/veiculos", tokenMecanicoA, 200);
    get("/api/v1/ordens-servico", tokenMecanicoA, 200);
    get("/api/v1/ordens-servico/" + osId, tokenMecanicoA, 200);

    // Escrita de cadastro e abertura de OS são do escritório.
    assertThat(
            post(
                    "/api/v1/clientes",
                    tokenMecanicoA,
                    Map.of("nome", "Nao Deve Entrar", "telefone", "11900000000"),
                    403)
                .path("code")
                .asText())
        .isEqualTo("FORBIDDEN");
    put(
        "/api/v1/clientes/" + c,
        tokenMecanicoA,
        Map.of("nome", "Outro Nome", "telefone", "11900000000", "revisao", 0),
        403);
    post(
        "/api/v1/veiculos",
        tokenMecanicoA,
        Map.of(
            "clienteId",
            c,
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
            "Preto"),
        403);
    post(
        "/api/v1/ordens-servico",
        tokenMecanicoA,
        Map.of("veiculoId", v, "kmEntrada", 1000, "relato", "Ruído"),
        403);
    put(
        "/api/v1/ordens-servico/" + osId + "/responsavel",
        tokenMecanicoA,
        Map.of("mecanicoId", mecanicoA.toString(), "revisao", revisao(tokenMecanicoA, osId)),
        403);

    // Mudar status é dos três papéis: é o mecânico quem toca a OS no dia a dia.
    mudarStatus(tokenMecanicoA, osId, "DIAGNOSTICO", 200);

    // Responsável precisa ser mecânico ativo da própria oficina.
    assertThat(
            put(
                    "/api/v1/ordens-servico/" + osId + "/responsavel",
                    tokenAtendenteA,
                    Map.of(
                        "mecanicoId", inativoA.toString(), "revisao", revisao(tokenOwnerA, osId)),
                    400)
                .path("code")
                .asText())
        .isEqualTo("INVALID_REQUEST");
    put(
        "/api/v1/ordens-servico/" + osId + "/responsavel",
        tokenAtendenteA,
        Map.of("mecanicoId", atendenteA.toString(), "revisao", revisao(tokenOwnerA, osId)),
        400);
    put(
        "/api/v1/ordens-servico/" + osId + "/responsavel",
        tokenAtendenteA,
        Map.of("mecanicoId", mecanicoB.toString(), "revisao", revisao(tokenOwnerA, osId)),
        404);
  }

  // ------------------------------------------------------- Tenancy e conflito de revisão

  @Test
  @DisplayName("Os seis contratos da integração não atravessam a fronteira da oficina")
  void isolamentoNosContratosDaPrimeiraIntegracao() throws Exception {
    String clienteA = cliente(tokenOwnerA, "Cliente da Oficina A");
    String veiculoA = veiculo(tokenOwnerA, clienteA, "AAA1A11", "Fiat", "Uno");
    String osA = os(tokenOwnerA, veiculoA);

    // Leitura por id: 404, nunca 403, para não confirmar que o registro existe.
    for (String caminho :
        List.of(
            "/api/v1/clientes/" + clienteA,
            "/api/v1/veiculos/" + veiculoA,
            "/api/v1/ordens-servico/" + osA,
            "/api/v1/ordens-servico/" + osA + "/timeline",
            "/api/v1/ordens-servico/" + osA + "/diagnosticos",
            "/api/v1/ordens-servico/" + osA + "/orcamento/versoes",
            "/api/v1/ordens-servico/" + osA + "/fotos")) {
      assertThat(get(caminho, tokenOwnerB, 404).path("code").asText())
          .as("leitura cruzada de %s", caminho)
          .isEqualTo("NOT_FOUND");
    }

    // Escrita cruzada.
    put(
        "/api/v1/clientes/" + clienteA,
        tokenOwnerB,
        Map.of("nome", "Sequestro", "telefone", "11900000000", "revisao", 0),
        404);
    put(
        "/api/v1/veiculos/" + veiculoA,
        tokenOwnerB,
        Map.of(
            "clienteId",
            clienteA,
            "placa",
            "AAA1A11",
            "marca",
            "Fiat",
            "modelo",
            "Uno",
            "ano",
            2020,
            "km",
            2000,
            "cor",
            "Prata",
            "revisao",
            0),
        404);
    post(
        "/api/v1/ordens-servico/" + osA + "/status",
        tokenOwnerB,
        Map.of("status", "DIAGNOSTICO", "revisao", 0),
        404);

    // Vínculo cruzado: a oficina B não pode pendurar um veículo no cliente da oficina A.
    post(
        "/api/v1/veiculos",
        tokenOwnerB,
        Map.of(
            "clienteId",
            clienteA,
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
        404);
    post(
        "/api/v1/ordens-servico",
        tokenOwnerB,
        Map.of("veiculoId", veiculoA, "kmEntrada", 1000, "relato", "Ruído"),
        404);

    // Filtro com id alheio devolve página vazia, nunca o registro da outra oficina.
    assertThat(
            get("/api/v1/veiculos?clienteId=" + clienteA, tokenOwnerB, 200).path("total").asLong())
        .isZero();
    assertThat(
            get("/api/v1/ordens-servico?clienteId=" + clienteA, tokenOwnerB, 200)
                .path("total")
                .asLong())
        .isZero();
    assertThat(get("/api/v1/ordens-servico", tokenOwnerB, 200).path("total").asLong()).isZero();
  }

  @Test
  @DisplayName("Revisão desatualizada em cliente, veículo e OS conflita em vez de sobrescrever")
  void conflitoDeRevisaoNosTresCadastros() throws Exception {
    String clienteId = cliente(tokenOwnerA, "Cliente Revisão");
    var corpoCliente = new HashMap<String, Object>();
    corpoCliente.put("nome", "Primeira escrita");
    corpoCliente.put("telefone", "11911112222");
    corpoCliente.put("revisao", 0);
    put("/api/v1/clientes/" + clienteId, tokenOwnerA, corpoCliente, 200);
    // A segunda escrita com a mesma revisão perdeu a corrida e precisa saber disso.
    corpoCliente.put("nome", "Escrita concorrente");
    var conflitoCliente = put("/api/v1/clientes/" + clienteId, tokenOwnerA, corpoCliente, 409);
    assertThat(conflitoCliente.path("code").asText()).isEqualTo("CONFLICT");
    assertThat(conflitoCliente.path("status").asInt()).isEqualTo(409);
    // O valor perdido não foi gravado.
    assertThat(get("/api/v1/clientes/" + clienteId, tokenOwnerA, 200).path("nome").asText())
        .isEqualTo("Primeira escrita");

    String veiculoId = veiculo(tokenOwnerA, clienteId, placaUnica(), "Ford", "Ka");
    var corpoVeiculo = new HashMap<String, Object>();
    corpoVeiculo.put("clienteId", clienteId);
    corpoVeiculo.put("placa", "KKK1K11");
    corpoVeiculo.put("marca", "Ford");
    corpoVeiculo.put("modelo", "Ka");
    corpoVeiculo.put("ano", 2021);
    corpoVeiculo.put("km", 5000);
    corpoVeiculo.put("cor", "Branco");
    corpoVeiculo.put("revisao", 0);
    put("/api/v1/veiculos/" + veiculoId, tokenOwnerA, corpoVeiculo, 200);
    assertThat(
            put("/api/v1/veiculos/" + veiculoId, tokenOwnerA, corpoVeiculo, 409)
                .path("code")
                .asText())
        .isEqualTo("CONFLICT");

    // A quilometragem nunca anda para trás, mesmo com a revisão correta.
    corpoVeiculo.put("revisao", 1);
    corpoVeiculo.put("km", 10);
    assertThat(
            put("/api/v1/veiculos/" + veiculoId, tokenOwnerA, corpoVeiculo, 400)
                .path("code")
                .asText())
        .isEqualTo("INVALID_REQUEST");

    // O veículo ficou com 5000 km na escrita bem-sucedida acima.
    String osId = os(tokenOwnerA, veiculoId, 5000);
    mudarStatus(tokenOwnerA, osId, "DIAGNOSTICO", 200);
    assertThat(
            post(
                    "/api/v1/ordens-servico/" + osId + "/status",
                    tokenOwnerA,
                    Map.of("status", "ORCAMENTO", "revisao", 0),
                    409)
                .path("code")
                .asText())
        .isEqualTo("CONFLICT");
    assertThat(get("/api/v1/ordens-servico/" + osId, tokenOwnerA, 200).path("status").asText())
        .isEqualTo("DIAGNOSTICO");
  }

  // ------------------------------------------------------- OpenAPI dos contratos publicados

  @Test
  @DisplayName("O OpenAPI nomeia um schema por DTO, com os mesmos nomes de docs/api-v1.md")
  void openApiNaoMisturaOsDtosDosContratos() throws Exception {
    // Regressão: os records se chamam Entrada/Saida em cliente, veículo, usuário e foto, e o
    // springdoc nomeia schema pelo nome simples da classe. Os quatro colidiam num só `Entrada` e
    // num só `Saida`, então o Swagger descrevia o corpo do cliente com os campos do veículo.
    var schemas = get("/v3/api-docs", null, 200).path("components").path("schemas");
    assertThat(campos(schemas))
        .contains(
            "ClienteEntrada",
            "ClienteSaida",
            "VeiculoEntrada",
            "VeiculoSaida",
            "UsuarioEntrada",
            "UsuarioSaida",
            "FotoSaida")
        .doesNotContain("Entrada", "Saida");

    assertThat(campos(schemas.path("ClienteEntrada").path("properties")))
        .containsExactlyInAnyOrder("nome", "telefone", "email", "revisao");
    // A Fase 10 somou os atributos que a revenda precisa descrever no anúncio. O conjunto segue
    // exato de propósito: o que esta asserção protege é a fronteira entre contratos, não o tamanho
    // da lista — campo novo aqui é decisão, nunca vazamento de outro DTO.
    assertThat(campos(schemas.path("VeiculoEntrada").path("properties")))
        .containsExactlyInAnyOrder(
            "clienteId",
            "placa",
            "marca",
            "modelo",
            "ano",
            "km",
            "cor",
            "revisao",
            "versao",
            "anoModelo",
            "chassi",
            "renavam",
            "combustivel",
            "cambio",
            "observacoes");

    // O envelope de página deixa de ser um schema só para todas as listagens.
    assertThat(campos(schemas)).contains("PaginaClienteSaida", "PaginaVeiculoSaida");

    // Sessão não endereça recurso: o Swagger não promete 404 nem 409 no login.
    var login =
        get("/v3/api-docs", null, 200).path("paths").path("/api/v1/auth/login").path("post");
    assertThat(campos(login.path("responses"))).containsExactlyInAnyOrder("200", "400", "401");
  }

  @Test
  @DisplayName("A mensagem de validação sai em português mesmo com navegador em outro idioma")
  void validacaoFalaPortuguesIndependenteDoAmbiente() throws Exception {
    // Regressão: a mensagem do Bean Validation era resolvida pelo locale da requisição. No
    // contêiner, sem locale definido, `detail` saía "must not be blank"; e um navegador com
    // Accept-Language: en-US recebia inglês mesmo em servidor pt-BR. A tela mostra esse texto ao
    // usuário final, então ele precisa ser estável.
    var req =
        MockMvcRequestBuilders.post("/api/v1/clientes")
            .header("Authorization", "Bearer " + tokenOwnerA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .contentType("application/json")
            .content("{\"nome\":\"\",\"telefone\":\"11987654321\",\"email\":\"nao-e-email\"}");
    var erro = corpo(mvc.perform(req).andExpect(status().is(400)).andReturn());

    assertThat(erro.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    var mensagens = erro.path("errors").findValuesAsText("message");
    assertThat(mensagens).contains("não deve estar em branco");
    assertThat(mensagens).noneMatch(m -> m.contains("must "));
    assertThat(erro.path("detail").asText()).contains("não deve estar em branco");
  }
}
