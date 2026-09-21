package br.com.garagem.revenda.proposta;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.PROPOSTA;

import br.com.garagem.revenda.avaliacao.AvaliacaoService;
import br.com.garagem.revenda.estoque.*;
import br.com.garagem.revenda.lead.LeadService;
import br.com.garagem.revenda.proposta.PropostaDtos.*;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PropostaService {
  private final RevendaDb db;
  private final EstoqueService estoques;
  private final AvaliacaoService avaliacoes;
  private final LeadService leads;
  private final RevendaEventos eventos;
  private final RevendaReferencias referencias;
  private final Clock clock;
  private static final String SELECT =
      "select p.*,pv.id versao_id,pv.preco_anunciado,pv.valor_negociado,pv.preco_anunciado-pv.valor_negociado desconto,pv.entrada,pv.avaliacao_troca_id,pv.valor_troca,pv.validade,pv.observacoes,c.nome cliente_nome,c.telefone,concat_ws(' ',v.marca,v.modelo,v.placa) veiculo_descricao ";
  private static final String FROM =
      "from revenda_proposta p join revenda_proposta_versao pv on pv.proposta_id=p.id and pv.oficina_id=p.oficina_id and pv.numero=p.numero_versao join revenda_estoque e on e.id=p.estoque_id and e.oficina_id=p.oficina_id join veiculo v on v.id=e.veiculo_id and v.oficina_id=e.oficina_id join cliente c on c.id=p.cliente_id and c.oficina_id=p.oficina_id where p.oficina_id=:tenant";

  public PropostaService(
      RevendaDb db,
      EstoqueService estoques,
      AvaliacaoService avaliacoes,
      LeadService leads,
      RevendaEventos eventos,
      RevendaReferencias referencias,
      Clock clock) {
    this.db = db;
    this.estoques = estoques;
    this.avaliacoes = avaliacoes;
    this.leads = leads;
    this.eventos = eventos;
    this.referencias = referencias;
    this.clock = clock;
  }

  public Item obter(UUID id) {
    return db.one(Item.class, SELECT + FROM + " and p.id=:id", Map.of("id", id));
  }

  public Item bloquear(UUID id) {
    return db.one(Item.class, SELECT + FROM + " and p.id=:id for update of p", Map.of("id", id));
  }

  public Pagina<Item> listar(Filtro f, int pagina, int tamanho) {
    var sql = new StringBuilder(FROM);
    var p = params();
    filter(sql, p, "status", "p.status=:status", f.status());
    filter(sql, p, "cliente", "p.cliente_id=:cliente", f.clienteId());
    filter(sql, p, "veiculo", "e.veiculo_id=:veiculo", f.veiculoId());
    filter(sql, p, "vendedor", "p.vendedor_id=:vendedor", f.vendedorId());
    return db.page(Item.class, SELECT, sql.toString(), "p.criado_em desc,p.id", p, pagina, tamanho);
  }

  public Pagina<Versao> versoes(UUID id, int pagina, int tamanho) {
    obter(id);
    return db.page(
        Versao.class,
        "select *,preco_anunciado-valor_negociado desconto",
        "from revenda_proposta_versao where oficina_id=:tenant and proposta_id=:id",
        "numero desc",
        Map.of("id", id),
        pagina,
        tamanho);
  }

  public Item criar(Nova n) {
    referencias.cliente(n.clienteId());
    referencias.comercial(n.vendedorId());
    var e = estoques.bloquear(n.estoqueId());
    validar(e, n.clienteId(), n.termos());
    if (n.leadId() != null) leads.vincularProposta(n.leadId(), n.clienteId(), e.veiculoId());
    var id = UUID.randomUUID();
    db.update(
        "insert into revenda_proposta(id,oficina_id,estoque_id,cliente_id,vendedor_id,lead_id) values(:id,:tenant,:estoque,:cliente,:vendedor,:lead)",
        params(
            "id",
            id,
            "estoque",
            e.id(),
            "cliente",
            n.clienteId(),
            "vendedor",
            n.vendedorId(),
            "lead",
            n.leadId()));
    versao(id, 1, e, n.termos());
    return obter(id);
  }

  public Item revisar(UUID id, Revisar n) {
    var inicial = obter(id);
    var e = estoques.bloquear(inicial.estoqueId());
    var p = bloquear(id);
    revision(p.revisao(), n.revisao());
    if (!Set.of("RASCUNHO", "ENVIADA", "RECUSADA").contains(p.status()))
      throw ApiException.conflict("Proposta encerrada não pode ser revisada.");
    validar(e, p.clienteId(), n.termos());
    versao(id, p.numeroVersao() + 1, e, n.termos());
    db.update(
        "update revenda_proposta set status='RASCUNHO',numero_versao=numero_versao+1,revisao=revisao+1 where oficina_id=:tenant and id=:id",
        Map.of("id", id));
    return obter(id);
  }

  private void versao(UUID id, int numero, EstoqueDtos.Item e, Termos n) {
    db.update(
        "insert into revenda_proposta_versao(id,oficina_id,proposta_id,numero,preco_anunciado,valor_negociado,entrada,avaliacao_troca_id,valor_troca,validade,observacoes,autor_id) values(:versao,:tenant,:id,:numero,:anunciado,:negociado,:entrada,:troca,:valorTroca,:validade,:observacoes,:autor)",
        params(
            "versao",
            UUID.randomUUID(),
            "id",
            id,
            "numero",
            numero,
            "anunciado",
            e.precoAnunciado(),
            "negociado",
            n.valorNegociado(),
            "entrada",
            n.entrada(),
            "troca",
            n.avaliacaoTrocaId(),
            "valorTroca",
            n.valorTroca(),
            "validade",
            n.validade(),
            "observacoes",
            n.observacoes(),
            "autor",
            UsuarioAutenticado.id()));
    eventos.registrar(PROPOSTA, id, "VERSAO_CRIADA", "Versão comercial " + numero, numero);
  }

  private void validar(EstoqueDtos.Item e, UUID cliente, Termos n) {
    negociavel(e);
    ValoresComerciais.validar(
        e.precoAnunciado(),
        e.precoMinimo(),
        n.valorNegociado(),
        n.entrada(),
        n.avaliacaoTrocaId(),
        n.valorTroca());
    if (n.avaliacaoTrocaId() != null) {
      var a = avaliacoes.obter(n.avaliacaoTrocaId());
      if (a.veiculoId().equals(e.veiculoId()))
        throw ApiException.invalid("Troca deve ser outro veículo.");
      avaliacoes.validarTroca(a, cliente, n.valorTroca());
    }
  }

  public Item transicao(UUID id, Transicao n) {
    var inicial = obter(id);
    var e = estoques.bloquear(inicial.estoqueId());
    var p = bloquear(id);
    revision(p.revisao(), n.revisao());
    boolean allowed =
        switch (p.status()) {
          case "RASCUNHO" ->
              Set.of(Status.ENVIADA, Status.CANCELADA, Status.EXPIRADA).contains(n.status());
          case "ENVIADA" ->
              Set.of(Status.ACEITA, Status.RECUSADA, Status.CANCELADA, Status.EXPIRADA)
                  .contains(n.status());
          default -> false;
        };
    if (!allowed) throw ApiException.conflict("Transição de proposta inválida.");
    boolean expired = !p.validade().isAfter(clock.instant());
    if (n.status() == Status.EXPIRADA && !expired
        || Set.of(Status.ENVIADA, Status.ACEITA).contains(n.status()) && expired)
      throw ApiException.conflict("Confira a validade da proposta.");
    if (Set.of(Status.ENVIADA, Status.ACEITA).contains(n.status()))
      validar(
          e,
          p.clienteId(),
          new Termos(
              p.valorNegociado(),
              p.entrada(),
              p.avaliacaoTrocaId(),
              p.valorTroca(),
              p.validade(),
              p.observacoes()));
    db.update(
        "update revenda_proposta set status=:status,revisao=revisao+1 where oficina_id=:tenant and id=:id",
        params("id", id, "status", n.status()));
    eventos.registrar(PROPOSTA, id, n.status().name(), "Proposta " + n.status(), p.numeroVersao());
    return obter(id);
  }

  public void validarVenda(Item p, UUID estoque, UUID cliente, UUID versao) {
    if (!p.estoqueId().equals(estoque)
        || !p.clienteId().equals(cliente)
        || !p.versaoId().equals(versao)
        || !p.status().equals("ACEITA")
        || !p.validade().isAfter(clock.instant()))
      throw ApiException.conflict("Proposta não aceita, expirada ou divergente da venda.");
  }

  public void concluirVenda(Item p, UUID venda) {
    if (p.leadId() != null) leads.vendido(p.leadId());
    eventos.registrar(PROPOSTA, p.id(), "VENDA", "Venda " + venda, p.numeroVersao());
  }

  private static void negociavel(EstoqueDtos.Item e) {
    if (!Set.of("DISPONIVEL", "RESERVADO").contains(e.status()))
      throw ApiException.conflict("Veículo não disponível para negociação.");
  }
}
