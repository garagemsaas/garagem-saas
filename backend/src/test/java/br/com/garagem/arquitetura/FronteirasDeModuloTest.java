package br.com.garagem.arquitetura;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Trava das fronteiras do monólito modular.
 *
 * <p>A auditoria encontrou um ciclo real entre {@code assinatura} e {@code ordemservico}, criado
 * por uma utilidade de segurança guardada dentro do serviço de OS, e o módulo {@code
 * dinheiroesquecido} alcançando quatro repositórios internos de {@code ordemservico} — um deles em
 * {@code acessopublico.aprovacao.repository}. Nada disso quebra compilação: some sozinho num
 * refactor e volta sozinho no commit seguinte. Por isso vira teste.
 */
class FronteirasDeModuloTest {
  private static final Path ORIGEM =
      Path.of("src/main/java/br/com/garagem").toAbsolutePath().normalize();

  /** Módulos de negócio. {@code shared}, {@code config} e {@code tenancy} são infraestrutura. */
  private static final Set<String> MODULOS =
      Set.of(
          "ordemservico",
          "dinheiroesquecido",
          "assinatura",
          "cliente",
          "veiculo",
          "usuario",
          "auth",
          "dashboard",
          "oficina");

  private static final Pattern IMPORT =
      Pattern.compile("^import (?:static )?br\\.com\\.garagem\\.([a-z]+)\\.([A-Za-z0-9_.]+);");

  private record Arquivo(String modulo, Path caminho, List<String> linhas) {}

  private static List<Arquivo> fontes() throws IOException {
    try (Stream<Path> caminhos = Files.walk(ORIGEM)) {
      var lista = new ArrayList<Arquivo>();
      for (Path p : caminhos.filter(f -> f.toString().endsWith(".java")).toList()) {
        String relativo = ORIGEM.relativize(p).toString().replace('\\', '/');
        String modulo = relativo.contains("/") ? relativo.substring(0, relativo.indexOf('/')) : "";
        lista.add(new Arquivo(modulo, p, Files.readAllLines(p, StandardCharsets.UTF_8)));
      }
      return lista;
    }
  }

  /** Dependências módulo → módulo, considerando só módulos de negócio. */
  private static Map<String, Set<String>> grafo() throws IOException {
    var grafo = new TreeMap<String, Set<String>>();
    for (var a : fontes()) {
      if (!MODULOS.contains(a.modulo())) continue;
      for (String linha : a.linhas()) {
        var m = IMPORT.matcher(linha.strip());
        if (!m.find()) continue;
        String alvo = m.group(1);
        if (!MODULOS.contains(alvo) || alvo.equals(a.modulo())) continue;
        grafo.computeIfAbsent(a.modulo(), k -> new TreeSet<>()).add(alvo);
      }
    }
    return grafo;
  }

  @Test
  void naoExisteCicloEntreModulos() throws IOException {
    var grafo = grafo();
    var ciclos = new TreeSet<String>();
    for (var origem : grafo.entrySet())
      for (String destino : origem.getValue())
        if (grafo.getOrDefault(destino, Set.of()).contains(origem.getKey()))
          ciclos.add(
              origem.getKey().compareTo(destino) < 0
                  ? origem.getKey() + " <-> " + destino
                  : destino + " <-> " + origem.getKey());
    assertThat(ciclos).as("ciclos entre módulos de negócio").isEmpty();
  }

  @Test
  void moduloNaoAlcancaOEncanamentoInternoDeOutro() throws IOException {
    var violacoes = new ArrayList<String>();
    for (var a : fontes()) {
      if (!MODULOS.contains(a.modulo())) continue;
      for (String linha : a.linhas()) {
        var m = IMPORT.matcher(linha.strip());
        if (!m.find()) continue;
        String alvo = m.group(1);
        String resto = m.group(2);
        if (!MODULOS.contains(alvo) || alvo.equals(a.modulo())) continue;
        // De fora, um módulo só pode ser acessado pela sua porta. Repositório, entidade e serviço
        // interno são detalhe de implementação de quem é dono do dado.
        //
        // Enum de domínio é a exceção deliberada: é vocabulário de valor, imutável e sem
        // comportamento — o dashboard agrupar por status de OS não cria acoplamento a encanamento.
        // Entidade não entra nessa exceção justamente por ser estado mutável de outro módulo.
        boolean permitido = resto.startsWith("port.") || ehEnum(alvo, resto);
        if (!permitido)
          violacoes.add(
              ORIGEM.relativize(a.caminho()).toString().replace('\\', '/')
                  + " importa "
                  + alvo
                  + "."
                  + resto);
      }
    }
    assertThat(violacoes)
        .as("acesso ao interior de outro módulo; use a porta do módulo ou mova para shared")
        .isEmpty();
  }

  /** Verifica na fonte do tipo importado se ele é um enum. */
  private static boolean ehEnum(String modulo, String resto) throws IOException {
    Path fonte = ORIGEM.resolve(modulo).resolve(resto.replace('.', '/') + ".java");
    return Files.exists(fonte)
        && Files.readString(fonte, StandardCharsets.UTF_8).contains("public enum ");
  }

  @Test
  void utilidadeDeSegurancaNaoMoraDentroDeUmModuloDeNegocio() throws IOException {
    // O ciclo original nasceu de `autor()` guardado em OsService e chamado por quatro módulos.
    for (var a : fontes())
      if (MODULOS.contains(a.modulo()))
        assertThat(String.join("\n", a.linhas()))
            .as("%s não deve expor identidade do autor: isso vive em shared.seguranca", a.caminho())
            .doesNotContain("SecurityContextHolder.getContext().getAuthentication()");
  }
}
