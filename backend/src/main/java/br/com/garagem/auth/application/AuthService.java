package br.com.garagem.auth.application;

import br.com.garagem.auth.api.AuthDtos.*;
import br.com.garagem.shared.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final Duration accessTtl;
  private final Duration refreshTtl;
  private final String dummy;

  public AuthService(
      JdbcTemplate jdbc,
      PasswordEncoder passwords,
      JwtEncoder encoder,
      @Value("${app.jwt.access-ttl}") Duration accessTtl,
      @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.encoder = encoder;
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
    dummy = passwords.encode(Tokens.novo());
  }

  private record Account(UUID id, UUID oficinaId, String nome, String senha, String papel) {}

  @Transactional
  public Sessao login(Login input) {
    // Control-plane lookup: tenant slug + email, no cross-tenant user lookup.
    var accounts =
        jdbc.query(
            "select u.id,u.oficina_id,u.nome,u.senha_hash,u.papel from usuario u join oficina o on o.id=u.oficina_id where o.slug=? and u.email=? and u.ativo=true and o.situacao='ATIVA'",
            (rs, n) ->
                new Account(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5)),
            input.oficina().trim().toLowerCase(Locale.ROOT),
            input.email().trim().toLowerCase(Locale.ROOT));
    String hash = accounts.isEmpty() ? dummy : accounts.getFirst().senha();
    if (!passwords.matches(input.senha(), hash) || accounts.isEmpty())
      throw ApiException.unauthorized();
    return issue(accounts.getFirst());
  }

  @Transactional
  public Sessao refresh(Refresh input) {
    var accounts =
        jdbc.query(
            "select u.id,u.oficina_id,u.nome,u.senha_hash,u.papel from refresh_token r join usuario u on u.id=r.usuario_id and u.oficina_id=r.oficina_id join oficina o on o.id=u.oficina_id where r.oficina_id=? and r.token_hash=? and r.revogado_em is null and r.expira_em>now() and u.ativo=true and o.situacao='ATIVA' for update of r",
            (rs, n) ->
                new Account(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5)),
            input.oficinaId(),
            Tokens.hash(input.refreshToken()));
    if (accounts.isEmpty()) throw ApiException.unauthorized();
    revoke(input);
    return issue(accounts.getFirst());
  }

  @Transactional
  public void revoke(Refresh input) {
    jdbc.update(
        "update refresh_token set revogado_em=now() where oficina_id=? and token_hash=? and revogado_em is null",
        input.oficinaId(),
        Tokens.hash(input.refreshToken()));
  }

  private Sessao issue(Account account) {
    Instant now = Instant.now();
    String refresh = Tokens.novo();
    jdbc.update(
        "insert into refresh_token(id,oficina_id,usuario_id,token_hash,expira_em) values(?,?,?,?,?)",
        UUID.randomUUID(),
        account.oficinaId(),
        account.id(),
        Tokens.hash(refresh),
        java.sql.Timestamp.from(now.plus(refreshTtl)));
    var claims =
        JwtClaimsSet.builder()
            .issuer("garagem-api")
            .subject(account.id().toString())
            .audience(List.of("garagem-api"))
            .issuedAt(now)
            .expiresAt(now.plus(accessTtl))
            .claim("oficina_id", account.oficinaId().toString())
            .claim("papel", account.papel())
            .build();
    String access =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    LoggerFactory.getLogger(AuthService.class)
        .atInfo()
        .addKeyValue("oficina_id", account.oficinaId())
        .addKeyValue("usuario_id", account.id())
        .log("sessao_emitida");
    return new Sessao(
        access,
        refresh,
        accessTtl.toSeconds(),
        account.oficinaId(),
        account.id(),
        account.nome(),
        account.papel());
  }
}
