package br.com.garagem.dinheiroesquecido.api;

import br.com.garagem.dinheiroesquecido.api.RecuperacaoDtos.*;
import br.com.garagem.dinheiroesquecido.application.*;
import br.com.garagem.shared.persistence.Pagina;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dinheiro-esquecido")
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
@Tag(
    name = "Dinheiro Esquecido",
    description =
        "Operação comercial da oficina autenticada. OWNER e ATENDENTE; MECANICO recebe 403.")
public class RecuperacaoController {
  private final RecuperacaoService service;
  private final IdentificacaoService identificacao;

  public RecuperacaoController(RecuperacaoService service, IdentificacaoService identificacao) {
    this.service = service;
    this.identificacao = identificacao;
  }

  @PostMapping("/identificar")
  @Operation(
      summary = "Identificar e reconciliar oportunidades da oficina",
      description =
          "Idempotente por origem, sem corpo. 7 dias após publicação; 30 dias após recusa ou reavaliarEm; revisão por data em São Paulo. Origens que perderam elegibilidade são descartadas com auditoria. Transações por OS: em falha parcial, repetir é seguro.")
  public IdentificacaoSaida identificar() {
    return identificacao.identificar();
  }

  @GetMapping("/oportunidades")
  @Operation(
      summary = "Listar oportunidades paginadas",
      description =
          "Filtros combinados por E. de/ate inclusivos sobre criação. Idade: dias completos desde elegibilidade, congelados no encerramento. Consulta não executa identificação.")
  public Pagina<OportunidadeSaida> listar(
      @ParameterObject @ModelAttribute Filtro filtro,
      @Parameter(description = "Página base 0") @RequestParam(defaultValue = "0") int pagina,
      @Parameter(description = "Tamanho aparado a 1–100") @RequestParam(defaultValue = "20")
          int tamanho,
      @Parameter(
              description =
                  "campo,asc|desc: criadoEm, elegivelDesde, proximoContatoEm, valorPotencial, status, tipo, id. Padrão criadoEm,desc; desempate id; nulos por último.",
              example = "elegivelDesde,asc")
          @RequestParam(required = false)
          String ordenacao) {
    return service.listar(filtro, pagina, tamanho, ordenacao);
  }

  @GetMapping("/oportunidades/{id}")
  @Operation(summary = "Consultar oportunidade, contatos, recuperação e auditoria")
  public Detalhe detalhe(@PathVariable UUID id) {
    return service.detalhe(id);
  }

  @PostMapping("/oportunidades/{id}/contatos")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Acrescentar tentativa de contato manual",
      description =
          "WHATSAPP apenas registra o canal, sem envio. AGENDADO exige próximo contato futuro e muda para AGENDADA; demais resultados mudam para EM_CONTATO. Omitir próximo contato limpa a data. Revisão obrigatória; terminal ou revisão vencida: 409.")
  public OportunidadeSaida contato(
      @PathVariable UUID id, @Valid @RequestBody ContatoEntrada input) {
    return service.contato(id, input);
  }

  @PostMapping("/oportunidades/{id}/resultados")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrar recuperação efetiva e encerrar",
      description =
          "Valor explícito não negativo, até 17 inteiros e 2 decimais; pode diferir do potencial. OS opcional da mesma oficina. Um único resultado imutável. Repetição/terminal/revisão antiga: 409.")
  public OportunidadeSaida resultado(
      @PathVariable UUID id, @Valid @RequestBody ResultadoEntrada input) {
    return service.resultado(id, input);
  }

  @PostMapping("/oportunidades/{id}/status")
  @Operation(
      summary = "Alterar status comercial",
      description =
          "EM_CONTATO; AGENDADA exige próximo contato; PERDIDA exige tentativa e motivo; DESCARTADA exige motivo. RECUPERADA somente via resultado. Sem reabertura ou exclusão.")
  public OportunidadeSaida status(@PathVariable UUID id, @Valid @RequestBody StatusEntrada input) {
    return service.status(id, input);
  }

  @PutMapping("/oportunidades/{id}/responsavel")
  @Operation(summary = "Atribuir responsável comercial ativo; null remove")
  public OportunidadeSaida responsavel(
      @PathVariable UUID id, @Valid @RequestBody ResponsavelEntrada input) {
    return service.responsavel(id, input);
  }

  @PutMapping("/oportunidades/{id}/proximo-contato")
  @Operation(
      summary = "Definir próximo contato futuro; null limpa",
      description =
          "Ao limpar uma oportunidade AGENDADA, passa a EM_CONTATO; alteração auditada. Revisão obrigatória.")
  public OportunidadeSaida proximo(
      @PathVariable UUID id, @Valid @RequestBody ProximoContatoEntrada input) {
    return service.proximoContato(id, input);
  }

  @GetMapping("/resumo")
  @Operation(
      summary = "Indicadores agregados da oficina",
      description =
          "de/ate inclusivos: criação para carteira/grupos, encerramento para recuperação/perdas. Abertas inclui ABERTA, EM_CONTATO e AGENDADA. Taxa percentual = recuperadas/(recuperadas+perdidas), descartadas excluídas; zero sem denominador. porPeriodo agrupa mês de criação em São Paulo; idade somente ativas. Grupos vazios omitidos.")
  public Resumo resumo(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant de,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant ate) {
    return service.resumo(de, ate);
  }
}
