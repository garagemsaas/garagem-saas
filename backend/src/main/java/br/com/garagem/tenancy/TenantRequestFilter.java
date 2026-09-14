package br.com.garagem.tenancy;

import br.com.garagem.auth.application.Tokens;
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
                "select count(*) from usuario u join oficina o on o.id=u.oficina_id where u.oficina_id=? and u.id=? and u.ativo=true and u.papel=? and o.situacao='ATIVA'",
                Integer.class,
                tenant,
                user,
                jwt.getToken().getClaimAsString("papel"));
        if (active == null || active != 1) {
          reject(res, 401);
          return;
        }
        TenantContext.set(tenant);
        MDC.put("usuario_id", user.toString());
      }
      if (!TenantContext.UNRESOLVED.equals(TenantContext.resolvedOrEmpty()))
        MDC.put("oficina_id", TenantContext.current().toString());
      chain.doFilter(req, res);
    } finally {
      TenantContext.clear();
    }
  }

  private void reject(HttpServletResponse response, int code) throws IOException {
    response.setStatus(code);
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write("{\"status\":" + code + ",\"detail\":\"Acesso inválido ou expirado.\"}");
  }
}
