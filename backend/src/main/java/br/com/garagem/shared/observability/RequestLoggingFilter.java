package br.com.garagem.shared.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    MDC.put("oficina_id", "anonimo");
    MDC.put("usuario_id", "anonimo");
    MDC.put("request_id", UUID.randomUUID().toString());
    try {
      chain.doFilter(req, res);
    } finally {
      LoggerFactory.getLogger(RequestLoggingFilter.class)
          .atInfo()
          .addKeyValue("metodo", req.getMethod())
          .addKeyValue("status", res.getStatus())
          .addKeyValue("duracao_ms", (System.nanoTime() - start) / 1_000_000)
          .log("request_concluido");
      MDC.clear();
    }
  }
}
