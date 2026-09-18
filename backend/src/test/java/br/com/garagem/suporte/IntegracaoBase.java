package br.com.garagem.suporte;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base das suítes de integração: um único PostgreSQL para todas elas.
 *
 * <p>Antes cada classe subia o próprio contêiner e o parava no fim. Com sete suítes isso custava
 * sete inicializações por build — a maior parte dos 7min21s de {@code verify} era esperar banco
 * subir, não executar teste.
 *
 * <p>O contêiner é estático e iniciado uma vez por JVM, e deliberadamente <b>não</b> usa {@code
 * withReuse}: reuso depende de configuração na máquina do desenvolvedor ({@code
 * testcontainers.properties}) e deixaria o resultado dependente do ambiente. Aqui o ganho vem de
 * compartilhar dentro da execução, mantendo a suíte determinística e autocontida.
 *
 * <p>O isolamento entre testes continua o mesmo de antes: cada cenário cria a própria oficina com
 * UUID novo, e é o filtro por oficina que separa os dados — não o contêiner.
 */
public abstract class IntegracaoBase {
  /** Iniciado sob demanda, uma vez por JVM, e encerrado pelo Ryuk ao fim da execução. */
  private static PostgreSQLContainer<?> postgres;

  private static synchronized PostgreSQLContainer<?> postgres() {
    if (postgres == null) {
      postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");
      postgres.start();
    }
    return postgres;
  }

  @DynamicPropertySource
  static void origemDeDados(DynamicPropertyRegistry r) {
    String local = System.getenv("TEST_DATABASE_URL");
    if (local == null) {
      var container = postgres();
      r.add("spring.datasource.url", container::getJdbcUrl);
      r.add("spring.datasource.username", container::getUsername);
      r.add("spring.datasource.password", container::getPassword);
    } else {
      // Permite apontar para um PostgreSQL já de pé, útil em execução local repetida.
      r.add("spring.datasource.url", () -> local);
      r.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
      r.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }
    r.add("app.jwt.secret", () -> "test-only-secret-at-least-thirty-two-bytes-long");
    r.add("app.storage.access-key", () -> "test-user");
    r.add("app.storage.secret-key", () -> "test-only-storage-password");
  }
}
