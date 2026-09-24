package br.com.garagem.tenancy;

import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.shared.error.ProblemJson;
import br.com.garagem.shared.seguranca.Tokens;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantRequestFilter extends OncePerRequestFilter {
  public static final String PUBLIC_LINK = "garagem.publicLink";
  private final JdbcTemplate jdbc;

  public TenantRequestFilter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (req.getRequestURI().startsWith("/api/v1/publico/")) {
        String token = req.getRequestURI().substring("/api/v1/publico/".length()).split("/", 2)[0];
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
          reject(res, 404);
          return;
        }
        // Capability lookup is the only public cross-tenant query. It returns scope, never business
        // data.
        var scopes =
            jdbc.query(
                "select l.id,l.oficina_id from link_acesso_publico l join oficina o on o.id=l.oficina_id where l.token_hash=? and l.revogado_em is null and l.expira_em>now() and o.situacao='ATIVA'",
                (rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)},
                Tokens.hash(token));
        if (scopes.isEmpty()) {
          reject(res, 404);
          return;
        }
        TenantContext.set(scopes.getFirst()[1]);
        req.setAttribute(PUBLIC_LINK, scopes.getFirst()[0]);
        MDC.put("usuario_id", "publico");
        res.setHeader("Cache-Control", "no-store");
      } else if (auth instanceof JwtAuthenticationToken jwt) {
        if ("PLATAFORMA".equals(jwt.getToken().getClaimAsString("escopo"))) {
          UUID platformUser;
          Long version;
          try {
            platformUser = UUID.fromString(jwt.getToken().getSubject());
            version = ((Number) jwt.getToken().getClaim("versao")).longValue();
          } catch (RuntimeException e) {
            reject(res, 401);
            return;
          }
          Integer active =
              jdbc.queryForObject(
                  "select count(*) from plataforma_usuario where id=? and ativo and papel=? and versao_sessao=?",
                  Integer.class,
                  platformUser,
                  jwt.getToken().getClaimAsString("papel"),
                  version);
          if (active == null || active != 1) {
            reject(res, 401);
            return;
          }
          if (!req.getRequestURI().startsWith("/api/v1/plataforma/")) {
            reject(res, 404);
            return;
          }
          res.setHeader("Cache-Control", "no-store");
          MDC.put("usuario_id", platformUser.toString());
          chain.doFilter(req, res);
          return;
        }
        UUID tenant, user;
        try {
          tenant = UUID.fromString(jwt.getToken().getClaimAsString("oficina_id"));
          user = UUID.fromString(jwt.getToken().getSubject());
        } catch (RuntimeException e) {
          reject(res, 401);
          return;
        }
        var active =
            jdbc.queryForObject(
                "select count(*) from usuario u join oficina o on o.id=u.oficina_id where u.oficina_id=? and u.id=? and u.ativo=true and u.papel=? and u.versao_sessao=? and o.situacao='ATIVA'",
                Integer.class,
                tenant,
                user,
                jwt.getToken().getClaimAsString("papel"),
                jwt.getToken().hasClaim("versao")
                    ? ((Number) jwt.getToken().getClaim("versao")).longValue()
                    : 0L);
        if (active == null || active != 1) {
          reject(res, 401);
          return;
        }
        TenantContext.set(tenant);
        MDC.put("usuario_id", user.toString());
      }
      if (!TenantContext.UNRESOLVED.equals(TenantContext.resolvedOrEmpty()))
        MDC.put("oficina_id", TenantContext.current().toString());
      // Qual módulo um endpoint exige é decisão dele, declarada com @RequerModulo e aplicada por
      // ModuloInterceptor. Este filtro resolve a empresa; não interpreta caminhos de URL.
      if (!TenantContext.UNRESOLVED.equals(TenantContext.resolvedOrEmpty()))
        res.setHeader("Cache-Control", "no-store");
      chain.doFilter(req, res);
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * Link público ausente, expirado ou revogado responde 404, igual a token malformado: quem tem o
   * link não consegue distinguir os casos nem descobrir se uma OS existe.
   */
  private void reject(HttpServletResponse response, int code) throws IOException {
    MDC.put(
        "auth_falha",
        code == 401 ? "SESSAO_DESATUALIZADA_OU_INATIVA" : "LINK_PUBLICO_INVALIDO_OU_EXPIRADO");
    ProblemJson.write(
        response,
        code,
        code == 401 ? ErrorCodes.UNAUTHORIZED : ErrorCodes.NOT_FOUND,
        "Acesso inválido ou expirado.");
  }
}
