package br.com.garagem.auth.api;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  public record Login(
      @NotBlank @Size(max = 80) String oficina,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 72) String senha) {}

  public record Refresh(@NotNull UUID oficinaId, @NotBlank @Size(max = 100) String refreshToken) {}

  public record Sessao(
      String accessToken,
      String refreshToken,
      long expiresIn,
      UUID oficinaId,
      UUID usuarioId,
      String nome,
      String papel) {}
}
