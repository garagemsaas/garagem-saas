package br.com.garagem.cliente.api;

import br.com.garagem.cliente.domain.Cliente;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class ClienteDtos {
  private ClienteDtos() {}

  @Schema(name = "ClienteEntrada")
  public record Entrada(
      @NotBlank @Size(max = 160) String nome,
      @NotBlank @Size(max = 30) String telefone,
      @Email @Size(max = 254) String email,
      @PositiveOrZero long revisao) {}

  @Schema(name = "ClienteSaida")
  public record Saida(UUID id, String nome, String telefone, String email, long revisao) {
    public static Saida de(Cliente c) {
      return new Saida(c.id, c.nome, c.telefone, c.email, c.revisao);
    }
  }
}
