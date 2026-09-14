package br.com.garagem.ordemservico.acessopublico.api;

import br.com.garagem.ordemservico.api.OsDtos.*;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.tenancy.TenantRequestFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/publico/{token}")
@SecurityRequirements
public class PublicoController {
  private final OsService service;

  public PublicoController(OsService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Consultar somente status, veículo e orçamento disponibilizado pelo link")
  public PublicoSaida obter(HttpServletRequest request) {
    return service.publico((UUID) request.getAttribute(TenantRequestFilter.PUBLIC_LINK));
  }

  @PostMapping("/decisao")
  @Operation(
      summary = "Aprovar ou recusar integralmente a versão atual; decisão repetida é idempotente")
  public PublicoSaida decidir(
      HttpServletRequest request, @Valid @RequestBody DecisaoEntrada input) {
    return service.decidir(
        (UUID) request.getAttribute(TenantRequestFilter.PUBLIC_LINK),
        input,
        request.getRemoteAddr());
  }
}
