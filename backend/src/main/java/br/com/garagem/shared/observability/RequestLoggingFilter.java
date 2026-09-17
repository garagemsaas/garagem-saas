package br.com.garagem.shared.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Correlação e registro de acesso. Loga método, caminho, status e duração — nunca corpo, cabeçalho
 * Authorization, senha ou token. O token do link público é apagado do caminho antes de virar log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Request-Id";
  private static final String PUBLICO = "/api/v1/publico/";
  private final long lentoMs;

  public RequestLoggingFilter(@Value("${app.observability.slow-request-ms:1000}") long lentoMs) {
    if (lentoMs < 1) throw new IllegalArgumentException("Limiar de lentidão deve ser positivo");
    this.lentoMs = lentoMs;
  }

  boolean lento(long duracaoMs) {
    return duracaoMs >= lentoMs;
  }

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
    } catch (IOException | ServletException | RuntimeException e) {
      if (!res.isCommitted()) res.setStatus(500);
      MDC.put("erro_code", "INTERNAL_ERROR");
      throw e;
    } finally {
      long duracao = (System.nanoTime() - start) / 1_000_000;
      var logger = LoggerFactory.getLogger(RequestLoggingFilter.class);
      var evento =
          res.getStatus() >= 500
              ? logger.atError()
              : lento(duracao) ? logger.atWarn() : logger.atInfo();
      if (MDC.get("erro_code") == null)
        MDC.put("erro_code", res.getStatus() >= 400 ? "HTTP_" + res.getStatus() : "NENHUM");
      Object rota = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      evento
          .addKeyValue("metodo", req.getMethod())
          .addKeyValue(
              "caminho", rota == null ? caminhoSeguro(req.getRequestURI()) : rota.toString())
          .addKeyValue("status", res.getStatus())
          .addKeyValue("duracao_ms", duracao)
          .addKeyValue("request_lento", lento(duracao))
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
    if (uri == null) return null;
    if (uri.startsWith(PUBLICO)) {
      int fim = uri.indexOf('/', PUBLICO.length());
      return PUBLICO
          + "{token}"
          + (fim < 0 ? "" : uri.substring(fim).equals("/decisao") ? "/decisao" : "/{rota}");
    }
    // Antes do MVC (401/403) só componentes conhecidos; segmentos livres nunca entram no log.
    var permitidos =
        java.util.Set.of(
            "api",
            "v1",
            "auth",
            "login",
            "refresh",
            "logout",
            "clientes",
            "veiculos",
            "usuarios",
            "ordens-servico",
            "checklist",
            "diagnosticos",
            "fotos",
            "conteudo",
            "orcamento",
            "versoes",
            "links",
            "timeline",
            "status",
            "responsavel",
            "dashboard",
            "dinheiro-esquecido",
            "identificar",
            "oportunidades",
            "contatos",
            "resultados",
            "resumo",
            "proximo-contato",
            "proxima-revisao",
            "orcamento-versoes",
            "reavaliacao",
            "actuator",
            "health",
            "readiness",
            "liveness");
    return java.util.Arrays.stream(uri.split("/", -1))
        .map(s -> s.isEmpty() || permitidos.contains(s) ? s : "{id}")
        .collect(java.util.stream.Collectors.joining("/"));
  }
}
