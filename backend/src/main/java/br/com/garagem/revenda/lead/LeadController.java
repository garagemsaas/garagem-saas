package br.com.garagem.revenda.lead;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.revenda.lead.LeadDtos.*;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revenda/leads")
@RequerModulo(ModuloEmpresa.REVENDA)
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
public class LeadController {
  private final LeadService service;

  public LeadController(LeadService service) {
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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Item criar(@Valid @RequestBody Novo n) {
    return service.criar(n);
  }

  @PutMapping("/{id}/status")
  public Item status(@PathVariable UUID id, @Valid @RequestBody Transicao n) {
    return service.transicao(id, n);
  }
}
