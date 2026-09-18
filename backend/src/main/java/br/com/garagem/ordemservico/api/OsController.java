package br.com.garagem.ordemservico.api;

import br.com.garagem.ordemservico.acessopublico.application.AcessoPublicoService;
import br.com.garagem.ordemservico.api.OsDtos.*;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.ordemservico.domain.StatusOs;
import br.com.garagem.shared.persistence.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ordens-servico")
public class OsController {
  private final OsService service;
  private final AcessoPublicoService acessoPublico;

  public OsController(OsService service, AcessoPublicoService acessoPublico) {
    this.service = service;
    this.acessoPublico = acessoPublico;
  }

  @GetMapping
  @Operation(
      summary = "Listar ordens de serviço da oficina",
      description =
          "Sempre restrito à oficina do token. Os filtros combinam com E lógico; o período"
              + " compara a data de abertura da OS.")
  public Pagina<OsSaida> listar(
      @Parameter(description = "Texto único: casa com placa, nome do cliente ou número da OS")
          @RequestParam(defaultValue = "")
          String busca,
      @Parameter(description = "Número exato da OS") @RequestParam(required = false) Long numero,
      @Parameter(description = "Status atual") @RequestParam(required = false) StatusOs status,
      @Parameter(description = "Cliente da mesma oficina") @RequestParam(required = false)
          UUID clienteId,
      @Parameter(description = "Veículo da mesma oficina") @RequestParam(required = false)
          UUID veiculoId,
      @Parameter(description = "Mecânico responsável") @RequestParam(required = false)
          UUID mecanicoId,
      @Parameter(description = "Parte da placa") @RequestParam(required = false) String placa,
      @Parameter(
              description = "Abertas a partir deste instante ISO-8601",
              example = "2026-09-01T00:00:00Z")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant de,
      @Parameter(
              description = "Abertas até este instante ISO-8601",
              example = "2026-09-30T23:59:59Z")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant ate,
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0")
          int pagina,
      @Parameter(description = "Itens por página, máximo 100") @RequestParam(defaultValue = "20")
          int tamanho,
      @Parameter(
              description =
                  "campo,asc|desc — aceita numero, status, criadoEm, previsaoEntrega ou concluidaEm",
              example = "numero,desc")
          @RequestParam(required = false)
          String ordenacao) {
    return service.listar(
        new OsFiltro(busca, numero, status, clienteId, veiculoId, mecanicoId, placa, de, ate),
        pagina,
        tamanho,
        ordenacao);
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
  @Operation(
      summary = "Registrar checklist de entrada",
      description =
          "Todos os papéis. Único por OS; 1 a 100 itens. Sem atualização. Duplicado ou OS concluída: 409.")
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
  @Operation(
      summary = "Adicionar diagnóstico classificado",
      description =
          "OWNER ou MECANICO. Acrescenta item VERDE, AMARELO ou VERMELHO. Sem edição. OS concluída: 409.")
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
  @Operation(
      summary = "Criar versão imutável do orçamento e retornar à etapa de orçamento",
      description =
          "OWNER ou ATENDENTE. Exige ORCAMENTO ou AGUARDANDO_APROVACAO. Total calculado no servidor, arredondamento HALF_UP por item. Criações serializadas por OS; histórico preservado. Não recebe revisão: sempre acrescenta nova versão.")
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
  @Operation(
      summary = "Emitir link público; token exibido uma única vez",
      description =
          "OWNER ou ATENDENTE. Escopo: uma OS; validade PUBLIC_LINK_TTL (padrão sete dias). URL com token no fragmento. Somente o hash é persistido. O cliente decide informando a versão que visualizou.")
  public LinkSaida link(@PathVariable UUID id) {
    return acessoPublico.criarLink(id);
  }

  @DeleteMapping("/{id}/links/{linkId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
  @Operation(
      summary = "Revogar link público",
      description =
          "OWNER ou ATENDENTE. 204 inclusive se já revogado, preservando a primeira data e evento. Link ou OS inacessível: 404.")
  public void revogar(@PathVariable UUID id, @PathVariable UUID linkId) {
    acessoPublico.revogarLink(id, linkId);
  }
}
