package br.com.garagem.veiculo.api;

import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.SemModulo;
import br.com.garagem.veiculo.api.VeiculoDtos.*;
import br.com.garagem.veiculo.application.VeiculoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@SemModulo
@RequestMapping("/api/v1/veiculos")
public class VeiculoController {
  private final VeiculoService service;

  public VeiculoController(VeiculoService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Listar veículos da oficina",
      description =
          "Sempre restrito à oficina do token. Placas são comparadas sem hífen nem espaço.")
  public Pagina<Saida> listar(
      @Parameter(description = "Texto único: casa com placa, marca ou modelo")
          @RequestParam(defaultValue = "")
          String busca,
      @Parameter(description = "Parte da placa") @RequestParam(required = false) String placa,
      @Parameter(description = "Parte da marca") @RequestParam(required = false) String marca,
      @Parameter(description = "Parte do modelo") @RequestParam(required = false) String modelo,
      @Parameter(description = "Veículos de um cliente da mesma oficina")
          @RequestParam(required = false)
          UUID clienteId,
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0")
          int pagina,
      @Parameter(description = "Itens por página, máximo 100") @RequestParam(defaultValue = "20")
          int tamanho,
      @Parameter(
              description = "campo,asc|desc — aceita placa, marca, modelo, ano, km ou criadoEm",
              example = "placa,asc")
          @RequestParam(required = false)
          String ordenacao) {
    return service.listar(busca, placa, marca, modelo, clienteId, pagina, tamanho, ordenacao);
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
