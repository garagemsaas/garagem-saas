package br.com.garagem.tenancy;

import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.shared.error.ProblemJson;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica {@link RequerModulo}. Único ponto que consulta módulo contratado: não há {@code if} de
 * módulo espalhado por serviço ou controlador, e nenhum caminho de URL é interpretado aqui.
 *
 * <p>A resposta é 404, não 403: para uma empresa que não contratou o módulo, a funcionalidade não
 * existe. Distinguir "não existe" de "existe e você não pode" contaria a ela o que a plataforma
 * vende para as outras.
 */
public class ModuloInterceptor implements HandlerInterceptor {
  private final JdbcTemplate jdbc;

  public ModuloInterceptor(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
      throws IOException {
    if (!(handler instanceof HandlerMethod alvo)) return true;
    var exigido = AnnotatedElementUtils.findMergedAnnotation(alvo.getMethod(), RequerModulo.class);
    if (exigido == null)
      exigido = AnnotatedElementUtils.findMergedAnnotation(alvo.getBeanType(), RequerModulo.class);
    if (exigido == null) return true;
    // Sem tenant resolvido não há módulo a consultar. Quem barra esse caso é a autenticação, que
    // roda antes; responder 404 aqui esconderia o 401 correto de quem só está deslogado.
    if (TenantContext.UNRESOLVED.equals(TenantContext.resolvedOrEmpty())) return true;
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from empresa_modulo where oficina_id=? and modulo=?)",
            Boolean.class,
            TenantContext.current(),
            exigido.value().name()))) return true;
    ProblemJson.write(res, 404, ErrorCodes.NOT_FOUND, "Recurso não disponível.");
    return false;
  }
}
