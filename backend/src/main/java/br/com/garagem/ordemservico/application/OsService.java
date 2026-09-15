package br.com.garagem.ordemservico.application;

import br.com.garagem.auth.application.Tokens;
import br.com.garagem.ordemservico.acessopublico.aprovacao.domain.AprovacaoOrcamento;
import br.com.garagem.ordemservico.acessopublico.aprovacao.repository.AprovacaoOrcamentoRepository;
import br.com.garagem.ordemservico.acessopublico.domain.LinkAcessoPublico;
import br.com.garagem.ordemservico.acessopublico.repository.LinkAcessoPublicoRepository;
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
import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.usuario.domain.Papel;
import br.com.garagem.usuario.repository.UsuarioRepository;
import br.com.garagem.veiculo.repository.VeiculoRepository;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OsService {
  private final OrdemServicoRepository ordens;
  private final VeiculoRepository veiculos;
  private final UsuarioRepository usuarios;
  private final ChecklistEntradaRepository checklists;
  private final ChecklistItemRepository checklistItens;
  private final DiagnosticoItemRepository diagnosticos;
  private final OrcamentoRepository orcamentos;
  private final OrcamentoVersaoRepository versoes;
  private final ItemOrcamentoRepository itens;
  private final LinkAcessoPublicoRepository links;
  private final AprovacaoOrcamentoRepository aprovacoes;
  private final EventoOrdemServicoRepository eventos;
  private final JdbcTemplate jdbc;
  private final String publicBase;
  private final java.time.Duration linkTtl;

  public OsService(
      OrdemServicoRepository ordens,
      VeiculoRepository veiculos,
      UsuarioRepository usuarios,
      ChecklistEntradaRepository checklists,
      ChecklistItemRepository checklistItens,
      DiagnosticoItemRepository diagnosticos,
      OrcamentoRepository orcamentos,
      OrcamentoVersaoRepository versoes,
      ItemOrcamentoRepository itens,
      LinkAcessoPublicoRepository links,
      AprovacaoOrcamentoRepository aprovacoes,
      EventoOrdemServicoRepository eventos,
      JdbcTemplate jdbc,
      @Value("${app.public-base-url}") String publicBase,
      @Value("${app.public-link.ttl}") java.time.Duration linkTtl) {
    this.ordens = ordens;
    this.veiculos = veiculos;
    this.usuarios = usuarios;
    this.checklists = checklists;
    this.checklistItens = checklistItens;
    this.diagnosticos = diagnosticos;
    this.orcamentos = orcamentos;
    this.versoes = versoes;
    this.itens = itens;
    this.links = links;
    this.aprovacoes = aprovacoes;
    this.eventos = eventos;
    this.jdbc = jdbc;
    this.publicBase = publicBase.replaceAll("/$", "");
    this.linkTtl = linkTtl;
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

  @Transactional(readOnly = true)
  public OsSaida obter(UUID id) {
    return OsSaida.de(entidade(id));
  }

  public OsSaida criar(NovaOs input) {
    var v =
        veiculos
            .findByIdAndOficinaId(input.veiculoId(), TenantContext.current())
            .orElseThrow(ApiException::missing);
    if (input.kmEntrada() < v.km)
      throw ApiException.invalid("KM de entrada inferior à quilometragem cadastrada.");
    if (input.mecanicoId() != null) validarMecanico(input.mecanicoId());
    OrdemServico o = new OrdemServico();
    o.veiculoId = v.id;
    o.clienteId = v.clienteId;
    o.mecanicoId = input.mecanicoId();
    o.kmEntrada = input.kmEntrada();
    o.relato = input.relato().trim();
    o.previsaoEntrega = input.previsaoEntrega();
    o.numero =
        jdbc.queryForObject(
            "update oficina set proximo_numero_os=proximo_numero_os+1 where id=? returning proximo_numero_os-1",
            Long.class,
            TenantContext.current());
    v.km = input.kmEntrada();
    ordens.save(o);
    evento(o.id, "OS_ABERTA", "OS recebida na oficina.", autor());
    return OsSaida.de(o);
  }

  public void responsavel(UUID id, ResponsavelEntrada input) {
    var o = bloquear(id);
    revisao(o, input.revisao());
    editavel(o);
    validarMecanico(input.mecanicoId());
    o.mecanicoId = input.mecanicoId();
    evento(id, "RESPONSAVEL_ALTERADO", "Mecânico responsável atualizado.", autor());
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
    }
    if (o.status == StatusOs.AGUARDANDO_APROVACAO && input.status() == StatusOs.EM_MANUTENCAO)
      throw ApiException.conflict("A manutenção exige aprovação do cliente pelo link.");
    transicao(o, input.status(), autor());
  }

  public ChecklistSaida checklist(UUID id, ChecklistEntradaDto input) {
    var o = bloquear(id);
    editavel(o);
    if (checklists.findByOrdemServicoIdAndOficinaId(id, TenantContext.current()).isPresent())
      throw ApiException.conflict("Checklist de entrada já registrado.");
    ChecklistEntrada c = new ChecklistEntrada();
    c.ordemServicoId = id;
    c.autorId = autor();
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
    evento(id, "CHECKLIST_REGISTRADO", "Checklist de entrada registrado.", autor());
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
    d.autorId = autor();
    d.descricao = input.descricao();
    d.classificacao = input.classificacao();
    diagnosticos.save(d);
    evento(id, "DIAGNOSTICO_REGISTRADO", "Item de diagnóstico: " + d.classificacao, autor());
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
    v.autorId = autor();
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
    if (o.status != StatusOs.ORCAMENTO) transicao(o, StatusOs.ORCAMENTO, autor());
    evento(
        id,
        "ORCAMENTO_VERSIONADO",
        "Orçamento v" + v.numero + " criado. Total: R$ " + v.total,
        autor());
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

  public LinkSaida criarLink(UUID id) {
    bloquear(id);
    String token = Tokens.novo();
    LinkAcessoPublico l = new LinkAcessoPublico();
    l.ordemServicoId = id;
    l.tokenHash = Tokens.hash(token);
    l.expiraEm = Instant.now().plus(linkTtl);
    links.save(l);
    evento(
        id,
        "LINK_CRIADO",
        "Link de acesso emitido com validade de " + linkTtl.toDays() + " dias.",
        autor());
    return new LinkSaida(l.id, publicBase + "/acompanhar#" + token, token, l.expiraEm);
  }

  public void revogarLink(UUID id, UUID linkId) {
    bloquear(id);
    var l =
        links
            .findByIdAndOficinaId(linkId, TenantContext.current())
            .filter(a -> a.ordemServicoId.equals(id))
            .orElseThrow(ApiException::missing);
    l.revogadoEm = Instant.now();
    evento(id, "LINK_REVOGADO", "Link de acesso revogado.", autor());
  }

  @Transactional(readOnly = true)
  public PublicoSaida publico(UUID linkId) {
    var l = linkValido(linkId);
    return resumoPublico(entidade(l.ordemServicoId));
  }

  public PublicoSaida decidir(UUID linkId, DecisaoEntrada input, String ip) {
    var inicial = linkValido(linkId);
    var o = bloquear(inicial.ordemServicoId);
    // Recheck capability under the same OS lock used by revocation and version creation.
    var valid =
        jdbc.queryForObject(
            "select count(*) from link_acesso_publico where id=? and oficina_id=? and revogado_em is null and expira_em>now()",
            Integer.class,
            linkId,
            TenantContext.current());
    if (valid == null || valid != 1) throw ApiException.missing();
    var v = ultimaVersao(o.id);
    if (!v.id.equals(input.versaoId()))
      throw ApiException.conflict("Este orçamento foi substituído. Consulte a versão atual.");
    var existente = aprovacoes.findByOrcamentoVersaoIdAndOficinaId(v.id, TenantContext.current());
    if (existente.isPresent()) {
      if (existente.get().aprovado != input.aprovado())
        throw ApiException.conflict("Uma decisão já foi registrada para esta versão.");
      return resumoPublico(o);
    }
    if (o.status != StatusOs.AGUARDANDO_APROVACAO)
      throw ApiException.conflict("Este orçamento não está aguardando aprovação.");
    var a = new AprovacaoOrcamento();
    a.orcamentoVersaoId = v.id;
    a.linkAcessoPublicoId = linkId;
    a.aprovado = input.aprovado();
    a.ipOrigem = ip;
    aprovacoes.save(a);
    evento(
        o.id,
        input.aprovado() ? "ORCAMENTO_APROVADO" : "ORCAMENTO_RECUSADO",
        "Cliente "
            + (input.aprovado() ? "aprovou" : "recusou")
            + " o orçamento v"
            + v.numero
            + " pelo link.",
        null);
    transicao(o, input.aprovado() ? StatusOs.EM_MANUTENCAO : StatusOs.ORCAMENTO, null);
    return resumoPublico(o);
  }

  private PublicoSaida resumoPublico(OrdemServico o) {
    var v =
        veiculos
            .findByIdAndOficinaId(o.veiculoId, TenantContext.current())
            .orElseThrow(ApiException::missing);
    VersaoSaida orc = null;
    if (Set.of(
            StatusOs.AGUARDANDO_APROVACAO,
            StatusOs.EM_MANUTENCAO,
            StatusOs.AGUARDANDO_PECA,
            StatusOs.TESTE,
            StatusOs.PRONTO)
        .contains(o.status)) orc = versaoSaida(ultimaVersao(o.id));
    return new PublicoSaida(
        o.numero, o.status, v.marca + " " + v.modelo + " · " + v.placa, o.previsaoEntrega, orc);
  }

  private LinkAcessoPublico linkValido(UUID id) {
    return links
        .findByIdAndOficinaId(id, TenantContext.current())
        .filter(l -> l.revogadoEm == null && l.expiraEm.isAfter(Instant.now()))
        .orElseThrow(ApiException::missing);
  }

  private OrcamentoVersao ultimaVersao(UUID os) {
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

  private VersaoSaida versaoSaida(OrcamentoVersao v) {
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
    var u =
        usuarios
            .findByIdAndOficinaId(id, TenantContext.current())
            .orElseThrow(ApiException::missing);
    if (!u.ativo || u.papel != Papel.MECANICO)
      throw ApiException.invalid("O responsável precisa ser um mecânico ativo.");
  }

  public static UUID autor() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }
}
