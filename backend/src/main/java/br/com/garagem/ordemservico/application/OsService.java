package br.com.garagem.ordemservico.application;

import br.com.garagem.ordemservico.acessopublico.aprovacao.repository.AprovacaoOrcamentoRepository;
import br.com.garagem.ordemservico.api.OsDtos.*;
import br.com.garagem.ordemservico.checklist.domain.ChecklistEntrada;
import br.com.garagem.ordemservico.checklist.item.domain.ChecklistItem;
import br.com.garagem.ordemservico.checklist.item.repository.ChecklistItemRepository;
import br.com.garagem.ordemservico.checklist.repository.ChecklistEntradaRepository;
import br.com.garagem.ordemservico.diagnostico.domain.DiagnosticoItem;
import br.com.garagem.ordemservico.diagnostico.repository.DiagnosticoItemRepository;
import br.com.garagem.ordemservico.domain.*;
import br.com.garagem.ordemservico.orcamento.domain.Orcamento;
import br.com.garagem.ordemservico.orcamento.item.domain.ItemOrcamento;
import br.com.garagem.ordemservico.orcamento.item.repository.ItemOrcamentoRepository;
import br.com.garagem.ordemservico.orcamento.repository.OrcamentoRepository;
import br.com.garagem.ordemservico.orcamento.versao.domain.OrcamentoVersao;
import br.com.garagem.ordemservico.orcamento.versao.repository.OrcamentoVersaoRepository;
import br.com.garagem.ordemservico.repository.OrdemServicoRepository;
import br.com.garagem.ordemservico.timeline.domain.EventoOrdemServico;
import br.com.garagem.ordemservico.timeline.repository.EventoOrdemServicoRepository;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Filtros;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.usuario.port.UsuarioPort;
import br.com.garagem.veiculo.port.VeiculoPort;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OsService {
  private final OrdemServicoRepository ordens;
  private final VeiculoPort veiculos;
  private final UsuarioPort usuarios;
  private final ChecklistEntradaRepository checklists;
  private final ChecklistItemRepository checklistItens;
  private final DiagnosticoItemRepository diagnosticos;
  private final OrcamentoRepository orcamentos;
  private final OrcamentoVersaoRepository versoes;
  private final ItemOrcamentoRepository itens;
  private final AprovacaoOrcamentoRepository aprovacoes;
  private final EventoOrdemServicoRepository eventos;
  private final JdbcTemplate jdbc;

  public OsService(
      OrdemServicoRepository ordens,
      VeiculoPort veiculos,
      UsuarioPort usuarios,
      ChecklistEntradaRepository checklists,
      ChecklistItemRepository checklistItens,
      DiagnosticoItemRepository diagnosticos,
      OrcamentoRepository orcamentos,
      OrcamentoVersaoRepository versoes,
      ItemOrcamentoRepository itens,
      AprovacaoOrcamentoRepository aprovacoes,
      EventoOrdemServicoRepository eventos,
      JdbcTemplate jdbc) {
    this.ordens = ordens;
    this.veiculos = veiculos;
    this.usuarios = usuarios;
    this.checklists = checklists;
    this.checklistItens = checklistItens;
    this.diagnosticos = diagnosticos;
    this.orcamentos = orcamentos;
    this.versoes = versoes;
    this.itens = itens;
    this.aprovacoes = aprovacoes;
    this.eventos = eventos;
    this.jdbc = jdbc;
  }

  /** Campos que a listagem aceita em {@code ordenacao}; qualquer outro é recusado com 400. */
  public static final Set<String> ORDENACAO =
      Set.of("numero", "status", "criadoEm", "previsaoEntrega", "concluidaEm");

  @Transactional(readOnly = true)
  public Pagina<OsSaida> listar(OsFiltro filtro, int pagina, int tamanho, String ordenacao) {
    return Pagina.de(
        ordens
            .filtrar(
                TenantContext.current(),
                Filtros.like(filtro.busca()),
                buscaPorPlaca(filtro.busca()),
                filtro.numero(),
                filtro.status(),
                filtro.clienteId(),
                filtro.veiculoId(),
                filtro.mecanicoId(),
                Filtros.like(placaNormalizada(filtro.placa())),
                filtro.de(),
                filtro.ate(),
                Pagina.request(pagina, tamanho, ordenacao, ORDENACAO))
            .map(OsSaida::de));
  }

  private static String placaNormalizada(String placa) {
    return placa == null ? null : placa.replace("-", "").replace(" ", "");
  }

  /**
   * Forma do campo único de pesquisa usada só contra a placa, que é gravada sem separadores. Sem
   * isso "ABC-1234" digitado na busca não encontrava a OS, embora o filtro dedicado {@code placa}
   * já normalizasse. Nome do cliente e número continuam comparados com o texto original. Quando a
   * pesquisa só tem separadores a forma normalizada fica vazia e vale o texto original, para os
   * dois parâmetros nunca chegarem nulos separadamente.
   */
  private static String buscaPorPlaca(String busca) {
    String normalizada = Filtros.like(placaNormalizada(busca));
    return normalizada == null ? Filtros.like(busca) : normalizada;
  }

  @Transactional(readOnly = true)
  public OsSaida obter(UUID id) {
    return OsSaida.de(entidade(id));
  }

  public OsSaida criar(NovaOs input) {
    // Abrir OS é a operação que mais consome a oficina; passa pelo plano antes de tocar em dado.

    var v = veiculos.buscar(input.veiculoId()).orElseThrow(ApiException::missing);
    if (input.kmEntrada() < v.km())
      throw ApiException.invalid("KM de entrada inferior à quilometragem cadastrada.");
    if (input.mecanicoId() != null) validarMecanico(input.mecanicoId());
    OrdemServico o = new OrdemServico();
    o.veiculoId = v.id();
    o.clienteId = v.clienteId();
    o.mecanicoId = input.mecanicoId();
    o.kmEntrada = input.kmEntrada();
    o.relato = input.relato().trim();
    o.previsaoEntrega = input.previsaoEntrega();
    o.numero =
        jdbc.queryForObject(
            "update oficina set proximo_numero_os=proximo_numero_os+1 where id=? returning proximo_numero_os-1",
            Long.class,
            TenantContext.current());
    veiculos.atualizarQuilometragem(v.id(), input.kmEntrada());
    ordens.save(o);
    evento(o.id, "OS_ABERTA", "OS recebida na oficina.", UsuarioAutenticado.id());
    return OsSaida.de(o);
  }

  public void responsavel(UUID id, ResponsavelEntrada input) {
    var o = bloquear(id);
    revisao(o, input.revisao());
    editavel(o);
    validarMecanico(input.mecanicoId());
    o.mecanicoId = input.mecanicoId();
    evento(id, "RESPONSAVEL_ALTERADO", "Mecânico responsável atualizado.", UsuarioAutenticado.id());
  }

  public void mudarStatus(UUID id, StatusEntrada input) {
    var o = bloquear(id);
    revisao(o, input.revisao());
    if (!o.status.permite(input.status()))
      throw ApiException.conflict("Transição de status não permitida.");
    if (input.status() == StatusOs.AGUARDANDO_APROVACAO) {
      var atual = ultimaVersao(id);
      if (aprovacoes
          .findByOrcamentoVersaoIdAndOficinaId(atual.id, TenantContext.current())
          .isPresent())
        throw ApiException.conflict("Crie uma nova versão antes de solicitar outra decisão.");
      jdbc.update(
          "insert into acompanhamento_orcamento(id,oficina_id,orcamento_versao_id,publicado_em) values(?,?,?,?) on conflict(oficina_id,orcamento_versao_id) do nothing",
          UUID.randomUUID(),
          TenantContext.current(),
          atual.id,
          java.sql.Timestamp.from(Instant.now()));
    }
    if (o.status == StatusOs.AGUARDANDO_APROVACAO && input.status() == StatusOs.EM_MANUTENCAO)
      throw ApiException.conflict("A manutenção exige aprovação do cliente pelo link.");
    transicao(o, input.status(), UsuarioAutenticado.id());
  }

  public ChecklistSaida checklist(UUID id, ChecklistEntradaDto input) {
    var o = bloquear(id);
    editavel(o);
    if (checklists.findByOrdemServicoIdAndOficinaId(id, TenantContext.current()).isPresent())
      throw ApiException.conflict("Checklist de entrada já registrado.");
    ChecklistEntrada c = new ChecklistEntrada();
    c.ordemServicoId = id;
    c.autorId = UsuarioAutenticado.id();
    c.observacoes = input.observacoes();
    checklists.save(c);
    var saida = new ArrayList<ChecklistItemSaida>();
    for (var i : input.itens()) {
      ChecklistItem item = new ChecklistItem();
      item.checklistEntradaId = c.id;
      item.descricao = i.descricao();
      item.condicao = i.condicao();
      item.observacao = i.observacao();
      checklistItens.save(item);
      saida.add(new ChecklistItemSaida(item.id, item.descricao, item.condicao, item.observacao));
    }
    evento(id, "CHECKLIST_REGISTRADO", "Checklist de entrada registrado.", UsuarioAutenticado.id());
    return new ChecklistSaida(c.id, c.observacoes, saida);
  }

  @Transactional(readOnly = true)
  public ChecklistSaida checklist(UUID id) {
    entidade(id);
    var c =
        checklists
            .findByOrdemServicoIdAndOficinaId(id, TenantContext.current())
            .orElseThrow(ApiException::missing);
    return new ChecklistSaida(
        c.id,
        c.observacoes,
        checklistItens
            .findByChecklistEntradaIdAndOficinaIdOrderByCriadoEmAscIdAsc(
                c.id, TenantContext.current())
            .stream()
            .map(i -> new ChecklistItemSaida(i.id, i.descricao, i.condicao, i.observacao))
            .toList());
  }

  public DiagnosticoSaida diagnostico(UUID id, DiagnosticoEntrada input) {
    var o = bloquear(id);
    editavel(o);
    DiagnosticoItem d = new DiagnosticoItem();
    d.ordemServicoId = id;
    d.autorId = UsuarioAutenticado.id();
    d.descricao = input.descricao();
    d.classificacao = input.classificacao();
    diagnosticos.save(d);
    evento(
        id,
        "DIAGNOSTICO_REGISTRADO",
        "Item de diagnóstico: " + d.classificacao,
        UsuarioAutenticado.id());
    return new DiagnosticoSaida(d.id, d.descricao, d.classificacao, d.criadoEm);
  }

  @Transactional(readOnly = true)
  public List<DiagnosticoSaida> diagnosticos(UUID id) {
    entidade(id);
    return diagnosticos
        .findByOrdemServicoIdAndOficinaIdOrderByCriadoEmAscIdAsc(id, TenantContext.current())
        .stream()
        .map(d -> new DiagnosticoSaida(d.id, d.descricao, d.classificacao, d.criadoEm))
        .toList();
  }

  public VersaoSaida novaVersao(UUID id, VersaoEntrada input) {
    var o = bloquear(id);
    if (o.status != StatusOs.ORCAMENTO && o.status != StatusOs.AGUARDANDO_APROVACAO)
      throw ApiException.conflict("A OS precisa estar na etapa de orçamento.");
    var orc =
        orcamentos
            .findByOrdemServicoIdAndOficinaId(id, TenantContext.current())
            .orElseGet(
                () -> {
                  var novo = new Orcamento();
                  novo.ordemServicoId = id;
                  return orcamentos.save(novo);
                });
    var anteriores =
        versoes.findByOrcamentoIdAndOficinaIdOrderByCriadoEmAscIdAsc(
            orc.id, TenantContext.current());
    OrcamentoVersao v = new OrcamentoVersao();
    v.orcamentoId = orc.id;
    v.numero = anteriores.stream().mapToInt(a -> a.numero).max().orElse(0) + 1;
    v.autorId = UsuarioAutenticado.id();
    v.observacoes = input.observacoes();
    v.total =
        input.itens().stream()
            .map(i -> subtotal(i.quantidade(), i.valorUnitario()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2);
    versoes.save(v);
    for (var i : input.itens()) {
      ItemOrcamento item = new ItemOrcamento();
      item.orcamentoVersaoId = v.id;
      item.tipo = i.tipo();
      item.descricao = i.descricao();
      item.quantidade = i.quantidade();
      item.valorUnitario = i.valorUnitario();
      itens.save(item);
    }
    if (o.status != StatusOs.ORCAMENTO) transicao(o, StatusOs.ORCAMENTO, UsuarioAutenticado.id());
    evento(
        id,
        "ORCAMENTO_VERSIONADO",
        "Orçamento v" + v.numero + " criado. Total: R$ " + v.total,
        UsuarioAutenticado.id());
    return versaoSaida(v);
  }

  @Transactional(readOnly = true)
  public List<VersaoSaida> versoes(UUID id) {
    entidade(id);
    var orc = orcamentos.findByOrdemServicoIdAndOficinaId(id, TenantContext.current());
    if (orc.isEmpty()) return List.of();
    return versoes
        .findByOrcamentoIdAndOficinaIdOrderByCriadoEmAscIdAsc(orc.get().id, TenantContext.current())
        .stream()
        .sorted(Comparator.comparingInt(v -> v.numero))
        .map(this::versaoSaida)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EventoSaida> timeline(UUID id) {
    entidade(id);
    return eventos
        .findByOrdemServicoIdAndOficinaIdOrderByCriadoEmAscIdAsc(id, TenantContext.current())
        .stream()
        .map(e -> new EventoSaida(e.id, e.tipo, e.descricao, e.origem, e.autorId, e.criadoEm))
        .toList();
  }

  /** Versão vigente do orçamento da OS. Público: o acompanhamento do cliente também a consulta. */
  public OrcamentoVersao ultimaVersao(UUID os) {
    var orc =
        orcamentos
            .findByOrdemServicoIdAndOficinaId(os, TenantContext.current())
            .orElseThrow(
                () -> ApiException.conflict("Crie o orçamento antes de solicitar aprovação."));
    return versoes
        .findByOrcamentoIdAndOficinaIdOrderByCriadoEmAscIdAsc(orc.id, TenantContext.current())
        .stream()
        .max(Comparator.comparingInt(v -> v.numero))
        .orElseThrow(() -> ApiException.conflict("O orçamento não possui versão."));
  }

  /** Monta o DTO de uma versão com itens e decisão. Compartilhado com o acompanhamento público. */
  public VersaoSaida versaoSaida(OrcamentoVersao v) {
    var items =
        itens
            .findByOrcamentoVersaoIdAndOficinaIdOrderByCriadoEmAscIdAsc(
                v.id, TenantContext.current())
            .stream()
            .map(
                i ->
                    new ItemSaida(
                        i.id,
                        i.tipo,
                        i.descricao,
                        i.quantidade,
                        i.valorUnitario,
                        subtotal(i.quantidade, i.valorUnitario)))
            .toList();
    var decision =
        aprovacoes
            .findByOrcamentoVersaoIdAndOficinaId(v.id, TenantContext.current())
            .map(a -> new DecisaoSaida(a.aprovado, a.criadoEm, a.canal))
            .orElse(null);
    return new VersaoSaida(v.id, v.numero, v.observacoes, v.total, v.criadoEm, items, decision);
  }

  /** Transição disparada pela decisão do cliente no link público. */
  public void transicaoPorDecisaoPublica(OrdemServico o, boolean aprovado) {
    transicao(o, aprovado ? StatusOs.EM_MANUTENCAO : StatusOs.ORCAMENTO, null);
  }

  public static BigDecimal subtotal(BigDecimal quantidade, BigDecimal valor) {
    return quantidade.multiply(valor).setScale(2, RoundingMode.HALF_UP);
  }

  private void transicao(OrdemServico o, StatusOs destino, UUID user) {
    var anterior = o.status;
    o.status = destino;
    if (destino == StatusOs.PRONTO) o.concluidaEm = Instant.now();
    evento(o.id, "STATUS_ALTERADO", anterior + " → " + destino, user);
  }

  public void evento(UUID os, String tipo, String descricao, UUID user) {
    EventoOrdemServico e = new EventoOrdemServico();
    e.ordemServicoId = os;
    e.autorId = user;
    e.origem = user == null ? "LINK_PUBLICO" : "USUARIO";
    e.tipo = tipo;
    e.descricao = descricao;
    eventos.save(e);
    LoggerFactory.getLogger(OsService.class)
        .atInfo()
        .addKeyValue("ordem_servico_id", os)
        .addKeyValue("evento", tipo)
        .log("acao_os");
  }

  public OrdemServico bloquear(UUID id) {
    return ordens.lock(id, TenantContext.current()).orElseThrow(ApiException::missing);
  }

  public OrdemServico entidade(UUID id) {
    return ordens
        .findByIdAndOficinaId(id, TenantContext.current())
        .orElseThrow(ApiException::missing);
  }

  public static void editavel(OrdemServico o) {
    if (o.status == StatusOs.PRONTO)
      throw ApiException.conflict("OS concluída não pode ser alterada.");
  }

  private void revisao(OrdemServico o, long revisao) {
    if (o.revisao != revisao) throw ApiException.conflict("A OS mudou. Atualize a página.");
  }

  private void validarMecanico(UUID id) {
    // Duas respostas distintas de propósito: quem não existe nesta oficina é 404, porque do lado
    // de fora não se confirma sequer a existência do registro; quem existe mas não serve é 400.
    if (!usuarios.existe(id)) throw ApiException.missing();
    if (!usuarios.ehMecanicoAtivo(id))
      throw ApiException.invalid("O responsável precisa ser um mecânico ativo.");
  }
}
