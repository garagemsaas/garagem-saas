package br.com.garagem.revenda.reserva;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.revenda.reserva.ReservaDtos.*;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revenda/reservas")
@RequerModulo(ModuloEmpresa.REVENDA)
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
public class ReservaController {
  private final ReservaService service;

  public ReservaController(ReservaService service) {
    this.service = service;
  }

  @GetMapping
  public Pagina<Item> listar(
      @RequestParam(required = false) UUID estoqueId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.listar(estoqueId, pagina, tamanho);
  }

  @GetMapping("/{id}")
  public Item obter(@PathVariable UUID id) {
    return service.obter(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Item criar(@Valid @RequestBody Nova n) {
    return service.criar(n);
  }

  @PostMapping("/{id}/cancelamento")
  public Item cancelar(@PathVariable UUID id, @Valid @RequestBody Cancelamento n) {
    return service.cancelar(id, n);
  }
}
