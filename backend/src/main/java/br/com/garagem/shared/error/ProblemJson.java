package br.com.garagem.shared.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.MDC;

/**
 * Escreve o mesmo corpo RFC 7807 de {@link ApiErrors} a partir de filtros, onde não existe
 * {@code @ControllerAdvice}. Só recebe textos fixos definidos no código, nunca entrada do usuário.
 */
public final class ProblemJson {
  private ProblemJson() {}

  public static void write(HttpServletResponse response, int status, String code, String detail)
      throws IOException {
    MDC.put("erro_code", code);
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/problem+json");
    String requestId = MDC.get("request_id");
    StringBuilder body =
        new StringBuilder()
            .append("{\"type\":\"https://garagem.com.br/erros/")
            .append(code.toLowerCase(Locale.ROOT))
            .append("\",\"title\":\"")
            .append(titulo(status))
            .append("\",\"status\":")
            .append(status)
            .append(",\"detail\":\"")
            .append(detail)
            .append("\",\"code\":\"")
            .append(code)
            .append("\",\"timestamp\":\"")
            .append(Instant.now())
            .append('"');
    if (requestId != null) body.append(",\"requestId\":\"").append(requestId).append('"');
    response.getWriter().write(body.append('}').toString());
  }

  private static String titulo(int status) {
    return switch (status) {
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      case 404 -> "Not Found";
      case 429 -> "Too Many Requests";
      default -> "Error";
    };
  }
}
