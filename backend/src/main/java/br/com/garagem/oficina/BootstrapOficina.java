package br.com.garagem.oficina;

import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class BootstrapOficina implements ApplicationRunner {
  private final Environment env;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;

  public BootstrapOficina(Environment env, JdbcTemplate jdbc, PasswordEncoder encoder) {
    this.env = env;
    this.jdbc = jdbc;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String slug = env.getRequiredProperty("app.bootstrap.slug").trim().toLowerCase(Locale.ROOT);
    String nome = env.getRequiredProperty("app.bootstrap.nome").trim();
    String email = env.getRequiredProperty("app.bootstrap.email").trim().toLowerCase(Locale.ROOT);
    String senha = env.getRequiredProperty("app.bootstrap.senha");
    // Quem provisionou, para a trilha administrativa. O próprio bootstrap é um operador legítimo:
    // exigir a variável faria toda instalação que já definia as cinco anteriores quebrar no start
    // por causa de uma sexta que ninguém sabia existir.
    String operador = env.getProperty("app.bootstrap.operador", "").trim();
    if (operador.isEmpty()) operador = "bootstrap";
    var modulosConfigurados = env.getProperty("app.bootstrap.modulos", "OFICINA");
    if (!slug.matches("[a-z0-9-]{3,80}")
        || nome.isBlank()
        || nome.length() > 160
        || !email.contains("@")
        || email.length() > 254
        || senha.length() < 12
        || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || operador.length() > 160)
      throw new IllegalArgumentException("Configuração de bootstrap inválida");
    String[] modulos;
    try {
      modulos =
          Arrays.stream(modulosConfigurados.split(","))
              .map(String::trim)
              .filter(m -> !m.isEmpty())
              .map(ModuloEmpresa::valueOf)
              .distinct()
              .map(Enum::name)
              .toArray(String[]::new);
    } catch (IllegalArgumentException e) {
      // Um módulo desconhecido é erro de configuração, e a mensagem precisa dizer isso em vez de
      // vazar o nome do enum do Java para quem está subindo o ambiente.
      throw new IllegalArgumentException("Configuração de bootstrap inválida");
    }
    if (modulos.length == 0)
      throw new IllegalArgumentException("Configuração de bootstrap inválida");
    String operadorAuditado = operador;
    // A empresa nasce com os módulos contratados, não com OFICINA para ser corrigida depois: uma
    // instalação de revenda nunca chega a existir como oficina, nem por um instante.
    UUID id =
        jdbc.execute(
            (java.sql.Connection connection) -> {
              try (var statement =
                  connection.prepareStatement("select provisionar_empresa(?, ?, ?, ?, ?)")) {
                statement.setString(1, slug);
                statement.setString(2, nome);
                statement.setArray(3, connection.createArrayOf("text", modulos));
                statement.setString(4, operadorAuditado);
                statement.setString(5, "Provisionamento inicial por venda direta");
                try (var resultado = statement.executeQuery()) {
                  return resultado.next() ? resultado.getObject(1, UUID.class) : null;
                }
              }
            });
    // Slug já existente devolve null: o bootstrap é idempotente e não redefine senha de quem
    // existe.
    if (id != null)
      jdbc.update(
          "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
          UUID.randomUUID(),
          id,
          nome,
          email,
          encoder.encode(senha));
  }
}
