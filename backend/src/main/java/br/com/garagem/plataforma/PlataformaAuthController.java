package br.com.garagem.plataforma;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.SemModulo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@SemModulo
@RequestMapping("/api/v1/plataforma/auth")
public class PlataformaAuthController {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final String dummy;

  public PlataformaAuthController(
      JdbcTemplate jdbc, PasswordEncoder passwords, JwtEncoder encoder) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.encoder = encoder;
    dummy = passwords.encode(UUID.randomUUID().toString());
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "PlataformaLogin")
  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String senha) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "PlataformaSessao")
  public record Sessao(String accessToken, String nome, String papel) {}

  @PostMapping("/login")
  public Sessao login(@Valid @RequestBody Login input) {
    var users =
        jdbc.queryForList(
            "select id,nome,senha_hash,papel,versao_sessao from plataforma_usuario where email=? and ativo",
            input.email().trim().toLowerCase(Locale.ROOT));
    String hash = users.isEmpty() ? dummy : (String) users.getFirst().get("senha_hash");
    if (!passwords.matches(input.senha(), hash) || users.isEmpty())
      throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
    var user = users.getFirst();
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("garagem-api")
            .audience(List.of("garagem-api"))
            .subject(user.get("id").toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(1800))
            .claim("escopo", "PLATAFORMA")
            .claim("papel", user.get("papel"))
            .claim("versao", user.get("versao_sessao"))
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new Sessao(token, (String) user.get("nome"), (String) user.get("papel"));
  }

  @PostMapping("/logout")
  @PreAuthorize("hasAnyRole('DESENVOLVEDOR','ADMIN_PLATAFORMA')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    jdbc.update(
        "update plataforma_usuario set versao_sessao=versao_sessao+1 where id=?",
        UsuarioAutenticado.id());
  }
}
