package br.com.garagem.cliente.api;

import br.com.garagem.cliente.api.ClienteDtos.*;
import br.com.garagem.cliente.application.ClienteService;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.SemModulo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@SemModulo
@RequestMapping("/api/v1/clientes")
public class ClienteController {
  private final ClienteService service;

  public ClienteController(ClienteService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Listar clientes da oficina",
      description =
          "Sempre restrito à oficina do token. Os filtros combinam entre si com E lógico e"
              + " ignoram maiúsculas/minúsculas.")
  public Pagina<Saida> listar(
      @Parameter(description = "Texto único: casa com nome, telefone ou e-mail")
          @RequestParam(defaultValue = "")
          String busca,
      @Parameter(description = "Parte do nome") @RequestParam(required = false) String nome,
      @Parameter(description = "Parte do telefone") @RequestParam(required = false) String telefone,
      @Parameter(description = "Parte do e-mail") @RequestParam(required = false) String email,
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0")
          int pagina,
      @Parameter(description = "Itens por página, máximo 100") @RequestParam(defaultValue = "20")
          int tamanho,
      @Parameter(
              description = "campo,asc|desc — aceita nome, telefone, email ou criadoEm",
              example = "nome,asc")
          @RequestParam(required = false)
          String ordenacao) {
    return service.listar(busca, nome, telefone, email, pagina, tamanho, ordenacao);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar cliente")
  public Saida obter(@PathVariable UUID id) {
    return service.obter(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Cadastrar cliente")
  public Saida criar(@Valid @RequestBody Entrada input) {
    return service.criar(input);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Atualizar cliente com controle de revisão")
  public Saida atualizar(@PathVariable UUID id, @Valid @RequestBody Entrada input) {
    service.atualizar(id, input);
    return service.obter(id);
  }
}
