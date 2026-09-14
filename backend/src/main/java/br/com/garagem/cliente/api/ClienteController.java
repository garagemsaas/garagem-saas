package br.com.garagem.cliente.api;

import br.com.garagem.cliente.api.ClienteDtos.*;
import br.com.garagem.cliente.application.ClienteService;
import br.com.garagem.shared.persistence.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {
  private final ClienteService service;

  public ClienteController(ClienteService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Listar clientes da oficina por nome")
  public Pagina<Saida> listar(
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.listar(busca, pagina, tamanho);
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
