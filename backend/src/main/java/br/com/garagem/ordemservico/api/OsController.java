package br.com.garagem.ordemservico.api;

import br.com.garagem.ordemservico.api.OsDtos.*;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.shared.persistence.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ordens-servico")
public class OsController {
  private final OsService service;

  public OsController(OsService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Buscar OS por placa, cliente ou número")
  public Pagina<OsSaida> listar(
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.listar(busca, pagina, tamanho);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar OS")
  public OsSaida obter(@PathVariable UUID id) {
    return service.obter(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Abrir OS")
  public OsSaida criar(@Valid @RequestBody NovaOs input) {
    return service.criar(input);
  }

  @PutMapping("/{id}/responsavel")
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Atribuir mecânico à OS")
  public OsSaida responsavel(@PathVariable UUID id, @Valid @RequestBody ResponsavelEntrada input) {
    service.responsavel(id, input);
    return service.obter(id);
  }

  @PostMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('OWNER','MECANICO','ATENDENTE')")
  @Operation(summary = "Avançar status respeitando fluxo e revisão da OS")
  public OsSaida status(@PathVariable UUID id, @Valid @RequestBody StatusEntrada input) {
    service.mudarStatus(id, input);
    return service.obter(id);
  }

  @PostMapping("/{id}/checklist")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar checklist de entrada")
  public ChecklistSaida checklist(
      @PathVariable UUID id, @Valid @RequestBody ChecklistEntradaDto input) {
    return service.checklist(id, input);
  }

  @GetMapping("/{id}/checklist")
  @Operation(summary = "Consultar checklist de entrada")
  public ChecklistSaida checklist(@PathVariable UUID id) {
    return service.checklist(id);
  }

  @PostMapping("/{id}/diagnosticos")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('OWNER','MECANICO')")
  @Operation(summary = "Adicionar diagnóstico classificado")
  public DiagnosticoSaida diagnostico(
      @PathVariable UUID id, @Valid @RequestBody DiagnosticoEntrada input) {
    return service.diagnostico(id, input);
  }

  @GetMapping("/{id}/diagnosticos")
  @Operation(summary = "Consultar diagnóstico")
  public List<DiagnosticoSaida> diagnosticos(@PathVariable UUID id) {
    return service.diagnosticos(id);
  }

  @PostMapping("/{id}/orcamento/versoes")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Criar versão imutável do orçamento e retornar à etapa de orçamento")
  public VersaoSaida versao(@PathVariable UUID id, @Valid @RequestBody VersaoEntrada input) {
    return service.novaVersao(id, input);
  }

  @GetMapping("/{id}/orcamento/versoes")
  @Operation(summary = "Consultar histórico completo de versões e decisões")
  public List<VersaoSaida> versoes(@PathVariable UUID id) {
    return service.versoes(id);
  }

  @GetMapping("/{id}/timeline")
  @Operation(summary = "Consultar timeline auditável da OS")
  public List<EventoSaida> timeline(@PathVariable UUID id) {
    return service.timeline(id);
  }

  @PostMapping("/{id}/links")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Emitir link público válido por sete dias; token exibido uma única vez")
  public LinkSaida link(@PathVariable UUID id) {
    return service.criarLink(id);
  }

  @DeleteMapping("/{id}/links/{linkId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(summary = "Revogar link público")
  public void revogar(@PathVariable UUID id, @PathVariable UUID linkId) {
    service.revogarLink(id, linkId);
  }
}
