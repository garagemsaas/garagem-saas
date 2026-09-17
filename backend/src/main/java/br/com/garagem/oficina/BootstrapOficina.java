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
    if (!slug.matches("[a-z0-9-]{3,80}")
        || nome.isBlank()
        || nome.length() > 160
        || !email.contains("@")
        || email.length() > 254
        || senha.length() < 12
        || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Configuração de bootstrap inválida");
    UUID id = UUID.randomUUID();
    int created =
        jdbc.update(
            "insert into oficina(id,nome,slug) values(?,?,?) on conflict(slug) do nothing",
            id,
            nome,
            slug);
    // A assinatura de avaliação é criada pelo gatilho `assinatura_da_oficina` (V4), junto com a
    // oficina: o invariante vale para qualquer origem de cadastro, não só para este bootstrap.
    if (created == 1)
      jdbc.update(
          "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
          UUID.randomUUID(),
          id,
          nome,
          email,
          encoder.encode(senha));
  }
}
