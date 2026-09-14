package br.com.garagem.shared.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> business(ApiException e) {
    return problem(e.status, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .distinct()
            .sorted()
            .reduce((a, b) -> a + "; " + b)
            .orElse("Dados inválidos."));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "Dados inválidos. Confira os campos enviados.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integrity(Exception e) {
    return problem(HttpStatus.CONFLICT, "Dados duplicados ou relacionamento inválido.");
  }

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> concurrent(Exception e) {
    return problem(HttpStatus.CONFLICT, "O registro mudou. Atualize a página e tente novamente.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(Exception e) {
    return problem(HttpStatus.FORBIDDEN, "Seu papel não permite esta ação.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> upload(Exception e) {
    return problem(HttpStatus.PAYLOAD_TOO_LARGE, "A foto deve ter até 10 MB.");
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ProblemDetail> notFound(Exception e) {
    return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> method(Exception e) {
    return problem(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido.");
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class
  })
  ResponseEntity<ProblemDetail> missingField(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "Campo obrigatório ausente.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LoggerFactory.getLogger(ApiErrors.class)
        .error("Falha interna: {}", e.getClass().getSimpleName());
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação.");
  }

  private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
  }
}
