package br.com.garagem.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origens autorizadas a chamar a API de um navegador. Lista vazia — o padrão — significa nenhuma
 * origem cruzada: em desenvolvimento o frontend passa pelo proxy do Vite e não precisa de CORS.
 * Configure {@code CORS_ALLOWED_ORIGINS} com as origens exatas de staging e produção.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    if (allowedOrigins.contains("*"))
      throw new IllegalArgumentException(
          "CORS_ALLOWED_ORIGINS não aceita '*': informe as origens explicitamente");
    for (String origin : allowedOrigins)
      if (!origin.matches("https?://[^/*\s]+"))
        throw new IllegalArgumentException(
            "Origem CORS inválida: " + origin + ". Use esquema, host e porta, sem caminho");
  }
}
