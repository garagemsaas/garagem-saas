package br.com.garagem.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Códigos estáveis do campo {@code code} de toda resposta de erro. O frontend decide por este
 * valor; {@code detail} é texto para pessoas e pode mudar.
 */
public final class ErrorCodes {
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  public static final String INVALID_REQUEST = "INVALID_REQUEST";
  public static final String UNAUTHORIZED = "UNAUTHORIZED";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
  public static final String CONFLICT = "CONFLICT";
  public static final String DUPLICATE = "DUPLICATE";
  public static final String STALE_REVISION = "STALE_REVISION";
  public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
  public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
  public static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private ErrorCodes() {}

  static String forStatus(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> INVALID_REQUEST;
      case UNAUTHORIZED -> UNAUTHORIZED;
      case FORBIDDEN -> FORBIDDEN;
      case NOT_FOUND -> NOT_FOUND;
      case METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED;
      case CONFLICT -> CONFLICT;
      case PAYLOAD_TOO_LARGE -> PAYLOAD_TOO_LARGE;
      case UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE;
      case SERVICE_UNAVAILABLE -> STORAGE_UNAVAILABLE;
      default -> INTERNAL_ERROR;
    };
  }
}
