package br.com.garagem.usuario.api;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.usuario.domain.*;
import br.com.garagem.usuario.repository.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
  private final UsuarioRepository repo;
  private final PasswordEncoder encoder;

  public UsuarioController(UsuarioRepository repo, PasswordEncoder encoder) {
    this.repo = repo;
    this.encoder = encoder;
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "UsuarioEntrada")
  public record Entrada(
      @NotBlank @Size(max = 160) String nome,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String senha,
      @NotNull Papel papel) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "UsuarioSaida")
  public record Saida(UUID id, String nome, String email, Papel papel, boolean ativo) {
    static Saida de(Usuario u) {
      return new Saida(u.id, u.nome, u.email, u.papel, u.ativo);
    }
  }

  /** Campos que a listagem aceita em {@code ordenacao}; qualquer outro é recusado com 400. */
  public static final Set<String> ORDENACAO = Set.of("nome", "email", "papel", "criadoEm");

  @GetMapping
  @Transactional(readOnly = true)
  @Operation(
      summary = "Listar equipe da oficina",
      description = "Somente dados públicos da equipe. Nenhuma senha ou hash é retornado.")
  public Pagina<Saida> listar(
      @Parameter(description = "Restringe a um papel") @RequestParam(required = false) Papel papel,
      @Parameter(description = "true lista apenas quem pode acessar")
          @RequestParam(required = false)
          Boolean ativo,
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0")
          int pagina,
      @Parameter(description = "Itens por página, máximo 100") @RequestParam(defaultValue = "20")
          int tamanho,
      @Parameter(
              description = "campo,asc|desc — aceita nome, email, papel ou criadoEm",
              example = "nome,asc")
          @RequestParam(required = false)
          String ordenacao) {
    return Pagina.de(
        repo.filtrar(
                TenantContext.current(),
                papel,
                ativo,
                Pagina.request(pagina, tamanho, ordenacao, ORDENACAO))
            .map(Saida::de));
  }

  @PostMapping
  @Transactional
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @Operation(summary = "Cadastrar usuário na oficina atual")
  public Saida criar(@Valid @RequestBody Entrada input) {
    if (input.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("Senha excede 72 bytes.");
    Usuario u = new Usuario();
    u.nome = input.nome().trim();
    u.email = input.email().trim().toLowerCase(Locale.ROOT);
    u.senhaHash = encoder.encode(input.senha());
    u.papel = input.papel();
    repo.save(u);
    org.slf4j.LoggerFactory.getLogger(UsuarioController.class)
        .atInfo()
        .addKeyValue("novo_usuario_id", u.id)
        .log("usuario_criado");
    return Saida.de(u);
  }
}
