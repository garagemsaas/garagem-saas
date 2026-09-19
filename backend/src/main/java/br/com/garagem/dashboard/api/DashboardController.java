package br.com.garagem.dashboard.api;

import br.com.garagem.dashboard.api.DashboardDtos.DashboardSaida;
import br.com.garagem.dashboard.application.DashboardService;
import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.tenancy.RequerModulo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.Clock;
import org.springframework.web.bind.annotation.*;

@RestController
@RequerModulo(ModuloEmpresa.OFICINA)
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final DashboardService service;
  private final Clock clock;

  public DashboardController(DashboardService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @GetMapping
  @Operation(
      summary = "Indicadores operacionais da oficina",
      description =
          "Disponível para OWNER, ATENDENTE e MECANICO. Os números vêm de agregações no banco,"
              + " restritas à oficina do token.")
  public DashboardSaida resumo(
      @Parameter(
              description = "Fuso IANA que delimita o dia de hoje",
              example = DashboardService.FUSO_PADRAO)
          @RequestParam(required = false)
          String fuso) {
    return service.resumo(fuso, clock);
  }
}
