package br.com.garagem.shared.error;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tradutor único de exceções para RFC 7807. Nenhuma resposta carrega stack trace, SQL, nome de
 * classe interna ou dado sensível: apenas status, código estável, texto para pessoas e, na
 * validação, a lista de campos recusados.
 */
@RestControllerAdvice
public class ApiErrors {

  /** Um campo recusado pela validação. */
  public record CampoInvalido(String field, String message) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> business(ApiException e) {
    return problem(e.status, e.code, e.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
    var campos =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> new CampoInvalido(f.getField(), mensagem(f)))
            .distinct()
            .sorted(
                Comparator.comparing(CampoInvalido::field).thenComparing(CampoInvalido::message))
            .toList();
    // `detail` mantém o resumo legível que o frontend já exibe desde a Fase 1; `errors` é
    // acréscimo, para a tela marcar campo a campo sem precisar interpretar texto.
    String resumo =
        campos.stream()
            .map(c -> c.field() + ": " + c.message())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Dados inválidos.");
    return problem(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, resumo, campos);
  }

  private static String mensagem(FieldError erro) {
    return erro.getDefaultMessage() == null ? "Valor inválido." : erro.getDefaultMessage();
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.INVALID_REQUEST,
        "Dados inválidos. Confira os campos enviados.",
        null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integrity(Exception e) {
    return problem(
        HttpStatus.CONFLICT,
        ErrorCodes.DUPLICATE,
        "Dados duplicados ou relacionamento inválido.",
        null);
  }

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> concurrent(Exception e) {
    return problem(
        HttpStatus.CONFLICT,
        ErrorCodes.STALE_REVISION,
        "O registro mudou. Atualize a página e tente novamente.",
        null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(Exception e) {
    MDC.put("auth_falha", "PAPEL_NAO_AUTORIZADO");
    return problem(
        HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "Seu papel não permite esta ação.", null);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> upload(Exception e) {
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE,
        ErrorCodes.PAYLOAD_TOO_LARGE,
        "A foto deve ter até 10 MB.",
        null);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> mediaType(Exception e) {
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
        "Tipo de conteúdo não suportado por este endpoint.",
        null);
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ProblemDetail> notFound(Exception e) {
    return problem(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Recurso não encontrado.", null);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> method(Exception e) {
    return problem(
        HttpStatus.METHOD_NOT_ALLOWED,
        ErrorCodes.METHOD_NOT_ALLOWED,
        "Método não permitido.",
        null);
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class
  })
  ResponseEntity<ProblemDetail> missingField(Exception e) {
    return problem(
        HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, "Campo obrigatório ausente.", null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LoggerFactory.getLogger(ApiErrors.class)
        .error("Falha interna: {}", e.getClass().getSimpleName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCodes.INTERNAL_ERROR,
        "Não foi possível concluir a operação.",
        null);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail, List<CampoInvalido> errors) {
    var body = corpo(status, code, detail, errors);
    if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
        instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
      String path = attributes.getRequest().getRequestURI();
      if (path.startsWith("/api/v1/publico/")) {
        // Impede o Spring MVC de preencher instance com a credencial presente na URL.
        body.setInstance(URI.create("/api/v1/publico/oculto"));
      }
    }
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }

  /** Monta o corpo canônico usado também pelos filtros de segurança e tenancy. */
  public static ProblemDetail corpo(
      HttpStatus status, String code, String detail, List<CampoInvalido> errors) {
    if (MDC.get("request_id") != null) MDC.put("erro_code", code);
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("https://garagem.com.br/erros/" + code.toLowerCase(Locale.ROOT)));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("timestamp", Instant.now().toString());
    if (errors != null && !errors.isEmpty()) problem.setProperty("errors", errors);
    String requestId = MDC.get("request_id");
    if (requestId != null) problem.setProperty("requestId", requestId);
    return problem;
  }
}
