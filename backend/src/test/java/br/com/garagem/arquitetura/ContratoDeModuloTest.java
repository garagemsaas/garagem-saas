package br.com.garagem.arquitetura;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Trava do contrato de módulo: nenhum endpoint entra na aplicação sem dizer a que módulo pertence.
 *
 * <p>Antes a resposta estava numa expressão regular de caminhos dentro do filtro de tenant. O risco
 * não era ela estar errada — estava certa —, era ela ficar para trás: uma rota nova de oficina
 * criada sem tocar na regex passaria a atender empresa que só contratou REVENDA, e nada acusaria.
 * Regex não quebra quando alguém esquece dela.
 *
 * <p>Agora a exigência é declarada no controlador, e este teste torna a declaração obrigatória. Um
 * {@code @RestController} novo sem {@code @RequerModulo} nem {@code @SemModulo} reprova o build. O
 * desenvolvedor não precisa saber que existe uma lista central; precisa apenas responder à pergunta
 * que o compilador não faz.
 *
 * <p>A verificação é por anotação de classe — as duas aparecem sem indentação, ao contrário das de
 * método. {@code @RequerModulo} em um método continua permitido para apertar um endpoint
 * específico, mas não substitui a declaração da classe.
 */
class ContratoDeModuloTest {
  private static final Path ORIGEM =
      Path.of("src/main/java/br/com/garagem").toAbsolutePath().normalize();

  private record Controlador(Path caminho, List<String> linhas) {}

  private static List<Controlador> controladores() throws IOException {
    try (Stream<Path> caminhos = Files.walk(ORIGEM)) {
      var lista = new ArrayList<Controlador>();
      for (Path p : caminhos.filter(f -> f.toString().endsWith(".java")).toList()) {
        var linhas = Files.readAllLines(p, StandardCharsets.UTF_8);
        if (linhas.contains("@RestController")) lista.add(new Controlador(p, linhas));
      }
      return lista;
    }
  }

  @Test
  void todoControladorDeclaraSeExigeModulo() throws IOException {
    var controladores = controladores();
    // Se este número cair a zero, o teste passaria sem verificar nada.
    assertThat(controladores).as("controladores encontrados").hasSizeGreaterThan(10);
    var omissos = new ArrayList<String>();
    for (var c : controladores) {
      boolean declara =
          c.linhas().stream()
              .anyMatch(l -> l.equals("@SemModulo") || l.startsWith("@RequerModulo("));
      if (!declara) omissos.add(ORIGEM.relativize(c.caminho()).toString().replace('\\', '/'));
    }
    assertThat(omissos).as("controlador sem @RequerModulo nem @SemModulo na classe").isEmpty();
  }

  @Test
  void exigenciaDeModuloNaoVoltaAMorarEmCaminhoDeUrl() throws IOException {
    // O filtro de tenant resolve a empresa. Se ele voltar a consultar empresa_modulo, é porque a
    // decisão voltou a depender do caminho pedido.
    String filtro =
        Files.readString(
            ORIGEM.resolve("tenancy/TenantRequestFilter.java"), StandardCharsets.UTF_8);
    assertThat(filtro)
        .as("gating de módulo pertence a ModuloInterceptor, pela anotação do endpoint")
        .doesNotContain("empresa_modulo");
  }
}
