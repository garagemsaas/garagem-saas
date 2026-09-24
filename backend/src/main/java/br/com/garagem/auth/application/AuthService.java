package br.com.garagem.auth.application;

import br.com.garagem.auth.api.AuthDtos.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.seguranca.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
  private final org.springframework.transaction.support.TransactionTemplate novaTransacao;

  public AuthService(
      JdbcTemplate jdbc,
      PasswordEncoder passwords,
      JwtEncoder encoder,
      org.springframework.transaction.PlatformTransactionManager transacoes,
      @Value("${app.jwt.access-ttl}") Duration accessTtl,
      @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
    var template = new org.springframework.transaction.support.TransactionTemplate(transacoes);
    template.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.novaTransacao = template;
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.encoder = encoder;
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
    dummy = passwords.encode(Tokens.novo());
  }

  private record Account(
      UUID id, UUID oficinaId, String nome, String senha, String papel, long versao) {}

  @Transactional
  public Sessao login(Login input) {
    // Control-plane lookup: tenant slug + email, no cross-tenant user lookup.
    var accounts =
        jdbc.query(
            "select u.id,u.oficina_id,u.nome,u.senha_hash,u.papel,u.versao_sessao from usuario u join oficina o on o.id=u.oficina_id where o.slug=? and u.email=? and u.ativo=true and o.situacao='ATIVA'",
            (rs, n) ->
                new Account(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getLong(6)),
            input.oficina().trim().toLowerCase(Locale.ROOT),
            input.email().trim().toLowerCase(Locale.ROOT));
    String hash = accounts.isEmpty() ? dummy : accounts.getFirst().senha();
    if (!passwords.matches(input.senha(), hash) || accounts.isEmpty()) {
      MDC.put("auth_falha", "LOGIN_RECUSADO");
      throw ApiException.unauthorized();
    }
    return issue(accounts.getFirst());
  }

  @Transactional
  public Sessao refresh(Refresh input) {
    var accounts =
        jdbc.query(
            "select u.id,u.oficina_id,u.nome,u.senha_hash,u.papel,u.versao_sessao from refresh_token r join usuario u on u.id=r.usuario_id and u.oficina_id=r.oficina_id join oficina o on o.id=u.oficina_id where r.oficina_id=? and r.token_hash=? and r.revogado_em is null and r.expira_em>now() and u.ativo=true and o.situacao='ATIVA' for update of r",
            (rs, n) ->
                new Account(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getLong(6)),
            input.oficinaId(),
            Tokens.hash(input.refreshToken()));
    if (accounts.isEmpty()) {
      MDC.put("auth_falha", "REFRESH_RECUSADO");
      detectarReuso(input);
      throw ApiException.unauthorized();
    }
    revogar(input, "ROTACAO");
    return issue(accounts.getFirst());
  }

  /**
   * Um refresh já revogado sendo apresentado de novo significa que duas partes tiveram o mesmo
   * token: ou é o cliente legítimo repetindo, ou é alguém usando um token roubado. Não há como
   * distinguir, então derruba-se a família inteira e o usuário entra de novo.
   *
   * <p>Sem isto, o ladrão que rotaciona primeiro fica com uma sessão válida indefinidamente, porque
   * cada renovação lhe dá um token novo e o dono legítimo só vê um 401 isolado.
   */
  private void detectarReuso(Refresh input) {
    // Transação própria: quem chama termina lançando 401, e o rollback desse erro desfaria a
    // revogação — a defesa sumiria justamente no caminho em que ela precisa valer.
    novaTransacao.executeWithoutResult(
        status -> {
          // Só ROTACAO é evidência de reuso. Um token revogado por LOGOUT reapresentado é uma
          // aba atrasada; derrubar a sessão da pessoa por isso seria defeito, não defesa.
          var usuarios =
              jdbc.query(
                  "select usuario_id from refresh_token where oficina_id=? and token_hash=? and motivo_revogacao='ROTACAO'",
                  (rs, n) -> rs.getObject(1, UUID.class),
                  input.oficinaId(),
                  Tokens.hash(input.refreshToken()));
          if (usuarios.isEmpty()) return;
          int derrubados =
              jdbc.update(
                  "update refresh_token set revogado_em=now(), motivo_revogacao='REUSO' where oficina_id=? and usuario_id=? and revogado_em is null",
                  input.oficinaId(),
                  usuarios.getFirst());
          MDC.put("auth_falha", "REFRESH_REUTILIZADO");
          LoggerFactory.getLogger(AuthService.class)
              .atWarn()
              .addKeyValue("sessoes_revogadas", derrubados)
              .log("refresh_reutilizado_familia_revogada");
        });
  }

  @Transactional
  public void revoke(Refresh input) {
    revogar(input, "LOGOUT");
  }

  private void revogar(Refresh input, String motivo) {
    jdbc.update(
        "update refresh_token set revogado_em=now(), motivo_revogacao=? where oficina_id=? and token_hash=? and revogado_em is null",
        motivo,
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
            .claim("versao", account.versao())
            .build();
    String access =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    // Os dois identificadores vão para o MDC, não como key-value do evento: o filtro de acesso já
    // publica `oficina_id` e `usuario_id` no contexto de toda requisição, e repetir esses nomes no
    // evento faz o escritor de log estruturado recusar a linha inteira ("has already been
    // written"). Pelo MDC o `sessao_emitida` é gravado, e o `request_concluido` da sessão que
    // acabou de nascer deixa de sair como "anonimo".
    MDC.put("oficina_id", account.oficinaId().toString());
    MDC.put("usuario_id", account.id().toString());
    LoggerFactory.getLogger(AuthService.class).atInfo().log("sessao_emitida");
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
