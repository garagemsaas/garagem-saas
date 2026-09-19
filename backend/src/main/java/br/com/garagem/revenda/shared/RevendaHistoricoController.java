package br.com.garagem.revenda.shared;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.RequerModulo;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequerModulo(ModuloEmpresa.REVENDA)
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
@RequestMapping("/api/v1/revenda/historico")
public class RevendaHistoricoController {
  private final RevendaEventos eventos;

  public RevendaHistoricoController(RevendaEventos eventos) {
    this.eventos = eventos;
  }

  @GetMapping("/{recurso}/{id}")
  public Pagina<RevendaEventos.Evento> listar(
      @PathVariable RevendaEventos.Recurso recurso,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return eventos.listar(recurso, id, pagina, tamanho);
  }
}
