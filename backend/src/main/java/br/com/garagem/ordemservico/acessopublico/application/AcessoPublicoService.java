package br.com.garagem.ordemservico.acessopublico.application;

import br.com.garagem.ordemservico.acessopublico.aprovacao.domain.AprovacaoOrcamento;
import br.com.garagem.ordemservico.acessopublico.aprovacao.repository.AprovacaoOrcamentoRepository;
import br.com.garagem.ordemservico.acessopublico.domain.LinkAcessoPublico;
import br.com.garagem.ordemservico.acessopublico.repository.LinkAcessoPublicoRepository;
import br.com.garagem.ordemservico.api.OsDtos.*;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.ordemservico.domain.OrdemServico;
import br.com.garagem.ordemservico.domain.StatusOs;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.seguranca.Tokens;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.veiculo.port.VeiculoPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acompanhamento da OS pelo cliente final: emissão e revogação do link, consulta pública e decisão
 * sobre o orçamento.
 *
 * <p>Saiu de {@code OsService}, que acumulava seis responsabilidades em 534 linhas. Esta é a fatia
 * mais coesa e a que mais se diferencia do resto: tem modelo de segurança próprio (capacidade por
 * token, sem sessão), audiência própria (o cliente final, não a oficina) e regras próprias de
 * visibilidade — o resumo público mostra menos do que qualquer tela interna.
 *
 * <p>O ciclo da OS em si continua em {@code OsService}, que segue dono do lock, da transição de
 * status e da linha do tempo. Aqui só se consome isso.
 */
@Service
@Transactional
public class AcessoPublicoService {
  private final OsService os;
  private final LinkAcessoPublicoRepository links;
  private final AprovacaoOrcamentoRepository aprovacoes;
  private final VeiculoPort veiculos;
  private final JdbcTemplate jdbc;
  private final String publicBase;
  private final Duration linkTtl;

  public AcessoPublicoService(
      OsService os,
      LinkAcessoPublicoRepository links,
      AprovacaoOrcamentoRepository aprovacoes,
      VeiculoPort veiculos,
      JdbcTemplate jdbc,
      @Value("${app.public-base-url}") String publicBase,
      @Value("${app.public-link.ttl}") Duration linkTtl) {
    this.os = os;
    this.links = links;
    this.aprovacoes = aprovacoes;
    this.veiculos = veiculos;
    this.jdbc = jdbc;
    this.publicBase = publicBase.replaceAll("/$", "");
    this.linkTtl = linkTtl;
  }

  public LinkSaida criarLink(UUID id) {
    os.bloquear(id);
    String token = Tokens.novo();
    LinkAcessoPublico l = new LinkAcessoPublico();
    l.ordemServicoId = id;
    l.tokenHash = Tokens.hash(token);
    l.expiraEm = Instant.now().plus(linkTtl);
    links.save(l);
    os.evento(
        id,
        "LINK_CRIADO",
        "Link de acesso emitido com validade de " + linkTtl.toDays() + " dias.",
        UsuarioAutenticado.id());
    return new LinkSaida(l.id, publicBase + "/acompanhar#" + token, token, l.expiraEm);
  }

  public void revogarLink(UUID id, UUID linkId) {
    os.bloquear(id);
    var l =
        links
            .findByIdAndOficinaId(linkId, TenantContext.current())
            .filter(a -> a.ordemServicoId.equals(id))
            .orElseThrow(ApiException::missing);
    if (l.revogadoEm != null) return;
    l.revogadoEm = Instant.now();
    os.evento(id, "LINK_REVOGADO", "Link de acesso revogado.", UsuarioAutenticado.id());
  }

  @Transactional(readOnly = true)
  public PublicoSaida publico(UUID linkId) {
    var l = linkValido(linkId);
    return resumoPublico(os.entidade(l.ordemServicoId));
  }

  public PublicoSaida decidir(UUID linkId, DecisaoEntrada input, String ip) {
    var inicial = linkValido(linkId);
    var o = os.bloquear(inicial.ordemServicoId);
    // Reconfere a capacidade sob o mesmo lock da OS usado por revogação e criação de versão.
    var valido =
        jdbc.queryForObject(
            "select count(*) from link_acesso_publico where id=? and oficina_id=? and revogado_em is null and expira_em>clock_timestamp()",
            Integer.class,
            linkId,
            TenantContext.current());
    if (valido == null || valido != 1) throw ApiException.missing();
    var v = os.ultimaVersao(o.id);
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
    os.evento(
        o.id,
        input.aprovado() ? "ORCAMENTO_APROVADO" : "ORCAMENTO_RECUSADO",
        "Cliente "
            + (input.aprovado() ? "aprovou" : "recusou")
            + " o orçamento v"
            + v.numero
            + " pelo link.",
        null);
    os.transicaoPorDecisaoPublica(o, input.aprovado());
    return resumoPublico(o);
  }

  /** O cliente vê o necessário para decidir: sem valores internos, sem fotos, sem histórico. */
  private PublicoSaida resumoPublico(OrdemServico o) {
    var v = veiculos.buscar(o.veiculoId).orElseThrow(ApiException::missing);
    VersaoSaida orc = null;
    if (Set.of(
            StatusOs.AGUARDANDO_APROVACAO,
            StatusOs.EM_MANUTENCAO,
            StatusOs.AGUARDANDO_PECA,
            StatusOs.TESTE,
            StatusOs.PRONTO)
        .contains(o.status)) orc = os.versaoSaida(os.ultimaVersao(o.id));
    else if (o.status == StatusOs.ORCAMENTO) {
      // A recusa continua consultável até que uma nova versão em preparação a substitua.
      var historico = os.versoes(o.id);
      if (!historico.isEmpty() && historico.getLast().decisao() != null) orc = historico.getLast();
    }
    return new PublicoSaida(o.numero, o.status, v.descricao(), o.previsaoEntrega, orc);
  }

  private LinkAcessoPublico linkValido(UUID id) {
    return links
        .findByIdAndOficinaId(id, TenantContext.current())
        .filter(l -> l.revogadoEm == null && l.expiraEm.isAfter(Instant.now()))
        .orElseThrow(ApiException::missing);
  }
}
