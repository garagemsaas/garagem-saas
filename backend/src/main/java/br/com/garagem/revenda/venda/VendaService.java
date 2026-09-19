package br.com.garagem.revenda.venda;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.VENDA;

import br.com.garagem.revenda.avaliacao.*;
import br.com.garagem.revenda.estoque.*;
import br.com.garagem.revenda.proposta.*;
import br.com.garagem.revenda.reserva.ReservaService;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.revenda.venda.VendaDtos.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.veiculo.port.VeiculoPort;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VendaService {
  private final RevendaDb db;
  private final EstoqueService estoques;
  private final AvaliacaoService avaliacoes;
  private final PropostaService propostas;
  private final ReservaService reservas;
  private final VeiculoPort veiculos;
  private final RevendaReferencias referencias;
  private final RevendaEventos eventos;
  private static final String SELECT =
      "select s.*,e.veiculo_id,s.preco_anunciado-s.valor_vendido desconto,c.nome cliente_nome,concat_ws(' ',v.marca,v.modelo,v.placa) veiculo_descricao ";
  private static final String FROM =
      "from revenda_venda s join revenda_estoque e on e.id=s.estoque_id and e.oficina_id=s.oficina_id join veiculo v on v.id=e.veiculo_id and v.oficina_id=e.oficina_id join cliente c on c.id=s.cliente_id and c.oficina_id=s.oficina_id where s.oficina_id=:tenant";

  public VendaService(
      RevendaDb db,
      EstoqueService estoques,
      AvaliacaoService avaliacoes,
      PropostaService propostas,
      ReservaService reservas,
      VeiculoPort veiculos,
      RevendaReferencias referencias,
      RevendaEventos eventos) {
    this.db = db;
    this.estoques = estoques;
    this.avaliacoes = avaliacoes;
    this.propostas = propostas;
    this.reservas = reservas;
    this.veiculos = veiculos;
    this.referencias = referencias;
    this.eventos = eventos;
  }

  public Item obter(UUID id) {
    return db.one(Item.class, SELECT + FROM + " and s.id=:id", Map.of("id", id));
  }

  public Pagina<Item> listar(Filtro f, int pagina, int tamanho) {
    var sql = new StringBuilder(FROM);
    var p = params();
    filter(sql, p, "de", "s.criado_em>=:de", f.de());
    filter(sql, p, "ate", "s.criado_em<:ate", f.ate());
    filter(sql, p, "vendedor", "s.vendedor_id=:vendedor", f.vendedorId());
    filter(sql, p, "cliente", "s.cliente_id=:cliente", f.clienteId());
    filter(sql, p, "veiculo", "e.veiculo_id=:veiculo", f.veiculoId());
    return db.page(Item.class, SELECT, sql.toString(), "s.criado_em desc,s.id", p, pagina, tamanho);
  }

  public Item criar(Nova n) {
    referencias.cliente(n.clienteId());
    referencias.comercial(n.vendedorId());
    var inicial = estoques.obter(n.estoqueId());
    AvaliacaoDtos.Item troca =
        n.avaliacaoTrocaId() == null ? null : avaliacoes.obter(n.avaliacaoTrocaId());
    var ids = new TreeSet<UUID>();
    ids.add(inicial.veiculoId());
    if (troca != null) {
      if (troca.veiculoId().equals(inicial.veiculoId()))
        throw ApiException.invalid("Troca deve ser outro veículo.");
      ids.add(troca.veiculoId());
    }
    // Ownership rows first, ordered identically across acquisitions and sales.
    ids.forEach(veiculos::bloquear);
    var e = estoques.bloquear(n.estoqueId());
    revision(e.revisao(), n.revisaoEstoque());
    if (!Set.of("DISPONIVEL", "RESERVADO").contains(e.status()))
      throw ApiException.conflict("Veículo não disponível para venda.");
    ValoresComerciais.validar(
        e.precoAnunciado(),
        e.precoMinimo(),
        n.valorVendido(),
        n.entrada(),
        n.avaliacaoTrocaId(),
        n.valorTroca());
    PropostaDtos.Item proposta = null;
    if (n.propostaId() != null) {
      proposta = propostas.bloquear(n.propostaId());
      propostas.validarVenda(proposta, e.id(), n.clienteId(), n.propostaVersaoId());
      if (proposta.valorNegociado().compareTo(n.valorVendido()) != 0
          || proposta.entrada().compareTo(n.entrada()) != 0
          || proposta.valorTroca().compareTo(n.valorTroca()) != 0
          || !Objects.equals(proposta.avaliacaoTrocaId(), n.avaliacaoTrocaId()))
        throw ApiException.conflict("Valores divergem da proposta aceita.");
    } else if (n.propostaVersaoId() != null) throw ApiException.invalid("Versão exige proposta.");
    UUID reserva = reservas.concluir(e.id(), n.clienteId());
    UUID estoqueTroca = null;
    if (troca != null) {
      troca = avaliacoes.bloquear(troca.id());
      avaliacoes.validarTroca(troca, n.clienteId(), n.valorTroca());
      estoqueTroca =
          avaliacoes
              .adquirir(
                  troca,
                  n.vendedorId(),
                  troca.valorEstimado(),
                  java.math.BigDecimal.ZERO,
                  EstoqueDtos.Origem.TROCA)
              .id();
    }
    var id = UUID.randomUUID();
    db.update(
        "insert into revenda_venda(id,oficina_id,estoque_id,cliente_id,vendedor_id,proposta_id,proposta_versao_id,reserva_id,estoque_troca_id,preco_anunciado,valor_vendido,entrada,valor_troca,custo_acumulado,margem_bruta,observacoes) values(:id,:tenant,:estoque,:cliente,:vendedor,:proposta,:versao,:reserva,:troca,:anunciado,:vendido,:entrada,:valorTroca,:custo,:margem,:observacoes)",
        params(
            "id",
            id,
            "estoque",
            e.id(),
            "cliente",
            n.clienteId(),
            "vendedor",
            n.vendedorId(),
            "proposta",
            n.propostaId(),
            "versao",
            n.propostaVersaoId(),
            "reserva",
            reserva,
            "troca",
            estoqueTroca,
            "anunciado",
            e.precoAnunciado(),
            "vendido",
            n.valorVendido(),
            "entrada",
            n.entrada(),
            "valorTroca",
            n.valorTroca(),
            "custo",
            e.custoTotal(),
            "margem",
            n.valorVendido().subtract(e.custoTotal()),
            "observacoes",
            n.observacoes()));
    estoques.status(e.id(), "VENDIDO", "Venda " + id);
    veiculos.transferirAoCliente(e.veiculoId(), n.clienteId(), "Venda " + id);
    if (proposta != null) propostas.concluirVenda(proposta, id);
    eventos.registrar(
        VENDA,
        id,
        "VENDA",
        "Venda concluída" + (estoqueTroca == null ? "" : " com recebimento em troca"));
    return obter(id);
  }
}
