package br.com.garagem.shared.error;

import org.springframework.http.HttpStatus;

/** Falha de negócio já traduzida para status HTTP e código estável de contrato. */
public class ApiException extends RuntimeException {
  public final HttpStatus status;
  public final String code;

  public ApiException(HttpStatus status, String message) {
    this(status, ErrorCodes.forStatus(status), message);
  }

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Registro não encontrado.");
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, ErrorCodes.CONFLICT, message);
  }

  public static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_REQUEST, message);
  }

  public static ApiException unauthorized() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Credenciais inválidas ou expiradas.");
  }
}
