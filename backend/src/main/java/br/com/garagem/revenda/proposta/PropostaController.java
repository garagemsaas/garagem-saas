package br.com.garagem.revenda.proposta;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.revenda.proposta.PropostaDtos.*;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revenda/propostas")
@RequerModulo(ModuloEmpresa.REVENDA)
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
public class PropostaController {
  private final PropostaService service;

  public PropostaController(PropostaService service) {
    this.service = service;
  }

  @GetMapping
  public Pagina<Item> listar(
      @ModelAttribute Filtro filtro,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.listar(filtro, pagina, tamanho);
  }

  @GetMapping("/{id}")
  public Item obter(@PathVariable UUID id) {
    return service.obter(id);
  }

  @GetMapping("/{id}/versoes")
  public Pagina<Versao> versoes(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.versoes(id, pagina, tamanho);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Item criar(@Valid @RequestBody Nova n) {
    return service.criar(n);
  }

  @PostMapping("/{id}/versoes")
  @ResponseStatus(HttpStatus.CREATED)
  public Item revisar(@PathVariable UUID id, @Valid @RequestBody Revisar n) {
    return service.revisar(id, n);
  }

  @PutMapping("/{id}/status")
  public Item status(@PathVariable UUID id, @Valid @RequestBody Transicao n) {
    return service.transicao(id, n);
  }
}
