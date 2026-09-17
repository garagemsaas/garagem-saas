package br.com.garagem.assinatura.api;

import br.com.garagem.assinatura.api.AssinaturaDtos.*;
import br.com.garagem.assinatura.application.*;
import br.com.garagem.assinatura.domain.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Área financeira da oficina autenticada. Leitura liberada a OWNER e ATENDENTE; decisões
 * contratuais (plano, cancelamento, reativação) são exclusivas do OWNER.
 *
 * <p>Continua acessível com a assinatura suspensa, de propósito: é por aqui que a oficina entende a
 * suspensão e se regulariza.
 */
@RestController
@RequestMapping("/api/v1/assinatura")
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
@Tag(
    name = "Assinatura",
    description =
        "Plano, consumo, cobrança e histórico financeiro da oficina. MECANICO recebe 403.")
public class AssinaturaController {
  private final AssinaturaService service;
  private final UsoService uso;
  private final PlanoLimiteService limites;
  private final CobrancaService cobranca;

  public AssinaturaController(
      AssinaturaService service,
      UsoService uso,
      PlanoLimiteService limites,
      CobrancaService cobranca) {
    this.service = service;
    this.uso = uso;
    this.limites = limites;
    this.cobranca = cobranca;
  }

  @GetMapping
  @Transactional
  @Operation(
      summary = "Consultar assinatura, consumo e planos disponíveis",
      description =
          "Normaliza o estado na leitura: tolerância vencida vira SUSPENSA e cancelamento agendado"
              + " vencido vira CANCELADA, sem depender de rotina externa.")
  public Detalhe detalhe() {
    var a = service.atual();
    var plano = limites.plano(a);
    return new Detalhe(
        saida(a, plano),
        uso.consumo(a, plano),
        service.catalogo().stream().map(p -> PlanoSaida.de(p, p.id.equals(plano.id))).toList());
  }

  @GetMapping("/consumo")
  @Transactional
  @Operation(
      summary = "Consultar apenas o consumo do plano",
      description =
          "Contagem feita no banco no instante da chamada. Limite nulo significa ilimitado.")
  public ConsumoSaida consumo() {
    var a = service.atual();
    return uso.consumo(a, limites.plano(a));
  }

  @PutMapping("/plano")
  @Transactional
  @PreAuthorize("hasRole('OWNER')")
  @Operation(
      summary = "Mudar de plano",
      description =
          "Revisão obrigatória. Reduzir para um plano menor que o uso atual é recusado com 409 e"
              + " PLAN_LIMIT_REACHED, em vez de deixar a oficina permanentemente acima do limite.")
  public AssinaturaSaida mudarPlano(@Valid @RequestBody MudarPlanoEntrada input) {
    var a = service.mudarPlano(input.codigo(), input.revisao());
    return saida(a, limites.plano(a));
  }

  @PostMapping("/cancelamento")
  @Transactional
  @PreAuthorize("hasRole('OWNER')")
  @Operation(
      summary = "Cancelar a assinatura",
      description =
          "imediato=false mantém o acesso até o fim do período já pago. Motivo obrigatório."
              + " Nenhum dado da oficina é removido pelo cancelamento.")
  public AssinaturaSaida cancelar(@Valid @RequestBody CancelamentoEntrada input) {
    var a = service.cancelar(input.imediato(), input.motivo(), input.revisao());
    return saida(a, limites.plano(a));
  }

  @PostMapping("/reativacao")
  @Transactional
  @PreAuthorize("hasRole('OWNER')")
  @Operation(
      summary = "Reativar a assinatura",
      description =
          "Revoga um cancelamento agendado imediatamente. A partir de CANCELADA devolve a"
              + " assinatura à tolerância: o acesso pleno só volta na confirmação do pagamento.")
  public AssinaturaSaida reativar(@Valid @RequestBody ReativacaoEntrada input) {
    var a = service.reativar(input.revisao());
    return saida(a, limites.plano(a));
  }

  @GetMapping("/eventos")
  @Transactional(readOnly = true)
  @Operation(
      summary = "Histórico de cobrança da oficina",
      description =
          "Trilha imutável e sem dado sensível: nenhum segredo, token ou número de cartão é"
              + " gravado. Mais recentes primeiro.")
  public Pagina<EventoCobrancaSaida> eventos(
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0")
          int pagina,
      @Parameter(description = "Itens por página, máximo 100") @RequestParam(defaultValue = "20")
          int tamanho) {
    return Pagina.de(cobranca.listar(Pagina.request(pagina, tamanho)).map(EventoCobrancaSaida::de));
  }

  private AssinaturaSaida saida(Assinatura a, Plano plano) {
    if (a == null) throw ApiException.missing();
    return new AssinaturaSaida(
        a.id,
        PlanoSaida.de(plano, true),
        a.status,
        situacao(a),
        a.status.permiteCrescer(),
        a.provedor,
        a.periodoInicio,
        a.periodoFim,
        a.trialFim,
        a.inadimplenteDesde,
        a.suspensaEm,
        a.canceladaEm,
        a.cancelamentoEfetivoEm,
        a.cancelamentoMotivo,
        a.revisao);
  }

  /** Texto único para a tela, para o frontend não reimplementar a leitura do ciclo de vida. */
  private static String situacao(Assinatura a) {
    if (a.status == StatusAssinatura.CANCELADA)
      return "Assinatura cancelada. Seus dados seguem disponíveis para consulta e exportação.";
    if (a.canceladaEm != null)
      return "Cancelamento agendado. O acesso continua até o fim do período contratado.";
    return switch (a.status) {
      case TRIAL -> "Período de avaliação em andamento.";
      case ATIVA -> "Assinatura ativa.";
      case INADIMPLENTE ->
          "Pagamento em atraso. Regularize para não perder as operações de cadastro e envio.";
      case SUSPENSA ->
          "Assinatura suspensa por falta de pagamento. Consulta e exportação seguem liberadas.";
      default -> "Assinatura ativa.";
    };
  }
}
