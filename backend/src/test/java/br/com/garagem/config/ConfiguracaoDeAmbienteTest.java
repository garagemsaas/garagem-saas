package br.com.garagem.config;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Trava do contrato de configuração.
 *
 * <p>Existe por causa de um defeito real: {@code PAYMENT_*}, {@code RATE_LIMIT_*}, {@code
 * TRUST_PROXY} e {@code API_DOCS_ENABLED} foram documentados em {@code .env.example} e lidos em
 * {@code application.yml}, mas nunca repassados no {@code compose.yml}. O contêiner subia com os
 * padrões, silenciosamente: o webhook recusava todo evento por falta de segredo e dois controles de
 * segurança não tinham como ser ligados.
 *
 * <p>Nada disso quebra compilação nem teste de integração — só aparece em produção. Por isso a
 * conferência é um teste.
 */
class ConfiguracaoDeAmbienteTest {
  private static final Path RAIZ = Path.of("..").toAbsolutePath().normalize();
  private static final Pattern VARIAVEL_DO_ENV = Pattern.compile("^([A-Z][A-Z0-9_]*)=");
  private static final Pattern VARIAVEL_DO_COMPOSE = Pattern.compile("^\\s{6}([A-Z][A-Z0-9_]*):");
  private static final Pattern REFERENCIA_NO_YML = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)[:}]");

  private static String ler(String caminho) throws IOException {
    return Files.readString(RAIZ.resolve(caminho), StandardCharsets.UTF_8);
  }

  /** Nomes definidos em um arquivo .env, ignorando comentários. */
  private static Set<String> doEnv(String caminho) throws IOException {
    var nomes = new TreeSet<String>();
    for (String linha : ler(caminho).lines().toList()) {
      var m = VARIAVEL_DO_ENV.matcher(linha.strip());
      if (m.find()) nomes.add(m.group(1));
    }
    return nomes;
  }

  /** Nomes repassados ao serviço `api` do Compose. */
  private static Set<String> doCompose() throws IOException {
    var nomes = new TreeSet<String>();
    boolean dentro = false;
    for (String linha : ler("compose.yml").lines().toList()) {
      if (linha.startsWith("      DATABASE_URL:")) dentro = true;
      if (dentro && linha.startsWith("    ports:")) break;
      if (!dentro) continue;
      var m = VARIAVEL_DO_COMPOSE.matcher(linha);
      if (m.find()) nomes.add(m.group(1));
    }
    return nomes;
  }

  @Test
  void tudoQueOEnvExampleDocumentaChegaAoContainer() throws IOException {
    assertThat(doCompose())
        .as("variáveis documentadas em .env.example que o compose.yml não repassa à api")
        .containsAll(doEnv(".env.example"));
  }

  @Test
  void tudoQueOStagingDocumentaChegaAoContainer() throws IOException {
    assertThat(doCompose())
        .as("variáveis documentadas em .env.staging.example que o compose.yml não repassa")
        .containsAll(doEnv(".env.staging.example"));
  }

  @Test
  void todaVariavelLidaPelaAplicacaoEstaDocumentada() throws IOException {
    var lidas = new TreeSet<String>();
    var m = REFERENCIA_NO_YML.matcher(ler("backend/src/main/resources/application.yml"));
    while (m.find()) lidas.add(m.group(1));
    // Fornecidas pelo próprio Compose, não pelo operador: documentá-las no .env só confundiria.
    lidas.removeAll(Set.of("DATABASE_URL", "DATABASE_USER", "S3_ENDPOINT", "S3_REGION"));
    assertThat(doEnv(".env.example"))
        .as("variáveis que application.yml lê mas .env.example não documenta")
        .containsAll(lidas);
  }

  @Test
  void nenhumaVariavelDocumentadaFicaSemConsumidor() throws IOException {
    String aplicacao = ler("backend/src/main/resources/application.yml");
    var orfas = new TreeSet<String>();
    for (String nome : doEnv(".env.example"))
      // O Compose consome algumas diretamente (imagem, bucket, bootstrap); as demais precisam
      // aparecer em application.yml, senão são documentação de algo que ninguém lê.
      if (!aplicacao.contains("${" + nome) && !ler("compose.yml").contains("${" + nome))
        orfas.add(nome);
    assertThat(orfas).as("variáveis documentadas que nenhum consumidor lê").isEmpty();
  }

  @Test
  void exemplosNuncaTrazemSegredoReal() throws IOException {
    for (String arquivo : List.of(".env.example", ".env.staging.example")) {
      String conteudo = ler(arquivo);
      for (String linha : conteudo.lines().toList()) {
        String limpa = linha.strip();
        if (limpa.startsWith("#") || !limpa.contains("=")) continue;
        String nome = limpa.substring(0, limpa.indexOf('='));
        String valor = limpa.substring(limpa.indexOf('=') + 1).strip();
        if (!nome.matches(".*(SECRET|PASSWORD|SENHA|KEY|TOKEN).*")) continue;
        assertThat(valor)
            .as("%s em %s deve ser marcador, nunca credencial", nome, arquivo)
            .satisfiesAnyOf(
                v -> assertThat(v).isEmpty(),
                v -> assertThat(v).contains("CHANGE_ME"),
                v -> assertThat(v).isEqualTo("garagem-local"),
                v -> assertThat(v).isEqualTo("garagem-fotos"),
                v -> assertThat(v).isEqualTo("garagem-fotos-staging"));
      }
    }
  }
}
