package br.com.garagem.plataforma;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.plataforma.bootstrap.enabled", havingValue = "true")
public class BootstrapPlataforma implements ApplicationRunner {
  private final Environment env;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;

  public BootstrapPlataforma(Environment env, JdbcTemplate jdbc, PasswordEncoder encoder) {
    this.env = env;
    this.jdbc = jdbc;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String email =
        env.getRequiredProperty("app.plataforma.bootstrap.email").trim().toLowerCase(Locale.ROOT);
    String nome = env.getRequiredProperty("app.plataforma.bootstrap.nome").trim();
    String senha = env.getRequiredProperty("app.plataforma.bootstrap.senha");
    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || email.length() > 254
        || nome.isBlank()
        || nome.length() > 160
        || senha.length() < 12
        || senha.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Configuração inicial da plataforma inválida.");
    jdbc.update(
        "insert into plataforma_usuario(id,nome,email,senha_hash,papel) values(?,?,?,?,'DESENVOLVEDOR') on conflict(email) do nothing",
        UUID.randomUUID(),
        nome,
        email,
        encoder.encode(senha));
  }
}
