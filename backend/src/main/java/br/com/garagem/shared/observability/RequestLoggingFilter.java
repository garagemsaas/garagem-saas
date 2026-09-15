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

/**
 * Correlação e registro de acesso. Loga método, caminho, status e duração — nunca corpo, cabeçalho
 * Authorization, senha ou token. O token do link público é apagado do caminho antes de virar log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Request-Id";
  private static final String PUBLICO = "/api/v1/publico/";

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    String requestId = correlacao(req);
    MDC.put("oficina_id", "anonimo");
    MDC.put("usuario_id", "anonimo");
    MDC.put("request_id", requestId);
    res.setHeader(HEADER, requestId);
    try {
      chain.doFilter(req, res);
    } finally {
      LoggerFactory.getLogger(RequestLoggingFilter.class)
          .atInfo()
          .addKeyValue("metodo", req.getMethod())
          .addKeyValue("caminho", caminhoSeguro(req.getRequestURI()))
          .addKeyValue("status", res.getStatus())
          .addKeyValue("duracao_ms", (System.nanoTime() - start) / 1_000_000)
          .log("request_concluido");
      MDC.clear();
    }
  }

  /** Reaproveita o id enviado pelo proxy quando ele parece seguro; caso contrário gera um novo. */
  private static String correlacao(HttpServletRequest req) {
    String recebido = req.getHeader(HEADER);
    return recebido != null && recebido.matches("[A-Za-z0-9._-]{8,64}")
        ? recebido
        : UUID.randomUUID().toString();
  }

  /** O token do link público é uma credencial: ele nunca pode aparecer em log. */
  static String caminhoSeguro(String uri) {
    if (uri == null || !uri.startsWith(PUBLICO)) return uri;
    int fim = uri.indexOf('/', PUBLICO.length());
    return PUBLICO + "{token}" + (fim < 0 ? "" : uri.substring(fim));
  }
}
