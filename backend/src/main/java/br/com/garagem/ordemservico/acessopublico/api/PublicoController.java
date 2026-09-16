package br.com.garagem.ordemservico.acessopublico.api;

import br.com.garagem.ordemservico.api.OsDtos.*;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.tenancy.TenantRequestFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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
  @Parameter(
      name = "token",
      in = ParameterIn.PATH,
      required = true,
      description = "Credencial opaca do link. Inválida, expirada ou revogada: 404.",
      schema = @Schema(type = "string", pattern = "[A-Za-z0-9_-]{43}"))
  @Operation(summary = "Consultar somente status, veículo e orçamento disponibilizado pelo link")
  public PublicoSaida obter(HttpServletRequest request) {
    return service.publico((UUID) request.getAttribute(TenantRequestFilter.PUBLIC_LINK));
  }

  @PostMapping("/decisao")
  @Parameter(
      name = "token",
      in = ParameterIn.PATH,
      required = true,
      description = "Credencial opaca do link. Revalidada após obter o lock da OS.",
      schema = @Schema(type = "string", pattern = "[A-Za-z0-9_-]{43}"))
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
