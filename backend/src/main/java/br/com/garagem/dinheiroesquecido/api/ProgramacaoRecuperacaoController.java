package br.com.garagem.dinheiroesquecido.api;

import br.com.garagem.dinheiroesquecido.api.RecuperacaoDtos.*;
import br.com.garagem.dinheiroesquecido.application.RecuperacaoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
public class ProgramacaoRecuperacaoController {
  private final RecuperacaoService service;

  public ProgramacaoRecuperacaoController(RecuperacaoService service) {
    this.service = service;
  }

  @GetMapping("/ordens-servico/{id}/proxima-revisao")
  @Operation(summary = "Consultar data da próxima revisão e revisão da OS")
  public ProgramacaoSaida revisao(@PathVariable UUID id) {
    return service.revisao(id);
  }

  @PutMapping("/ordens-servico/{id}/proxima-revisao")
  @Operation(
      summary = "Programar revisão por data após concluir a OS",
      description =
          "OWNER/ATENDENTE. Exceção limitada para programação pós-serviço: não reabre a OS. data ISO local em São Paulo, null remove; revisão da OS obrigatória. Data não anterior à conclusão. Após gerar oportunidade, novo ciclo exige outra OS. Auditada na timeline.")
  public ProgramacaoSaida revisao(
      @PathVariable UUID id, @Valid @RequestBody ProgramacaoEntrada input) {
    return service.revisao(id, input);
  }

  @GetMapping("/orcamento-versoes/{id}/reavaliacao")
  @Operation(
      summary = "Consultar data preferencial de reavaliação e sua revisão",
      description = "Sem programação: data null, revisao 0; padrão de 30 dias após recusa.")
  public ProgramacaoSaida reavaliacao(@PathVariable UUID id) {
    return service.reavaliacao(id);
  }

  @PutMapping("/orcamento-versoes/{id}/reavaliacao")
  @Operation(
      summary = "Definir reavaliarEm para versão recusada",
      description =
          "data ISO local em São Paulo, não anterior à recusa; null restaura padrão de 30 dias. Revisão é da programação, não da OS. Após identificação, utilizar próximo contato da oportunidade. Não altera versão nem decisão imutável. Auditada na timeline.")
  public ProgramacaoSaida reavaliacao(
      @PathVariable UUID id, @Valid @RequestBody ProgramacaoEntrada input) {
    return service.reavaliacao(id, input);
  }
}
