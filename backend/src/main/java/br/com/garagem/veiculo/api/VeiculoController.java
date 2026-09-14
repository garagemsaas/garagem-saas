package br.com.garagem.veiculo.api;

import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.veiculo.api.VeiculoDtos.*;
import br.com.garagem.veiculo.application.VeiculoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/veiculos")
public class VeiculoController {
  private final VeiculoService service;

  public VeiculoController(VeiculoService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Listar veículos da oficina por placa")
  public Pagina<Saida> listar(
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.listar(busca, pagina, tamanho);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar veículo")
  public Saida obter(@PathVariable UUID id) {
    return service.obter(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Cadastrar veículo")
  public Saida criar(@Valid @RequestBody Entrada input) {
    return service.criar(input);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Atualizar veículo com controle de revisão")
  public Saida atualizar(@PathVariable UUID id, @Valid @RequestBody Entrada input) {
    service.atualizar(id, input);
    return service.obter(id);
  }
}
