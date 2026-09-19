package br.com.garagem.revenda.estoque;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.*;

import br.com.garagem.revenda.estoque.EstoqueDtos.*;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.veiculo.port.VeiculoPort;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EstoqueService {
  private final RevendaDb db;
  private final RevendaEventos eventos;
  private final RevendaReferencias referencias;
  private final VeiculoPort veiculos;
  private final Clock clock;
  private static final String FROM =
      "from revenda_estoque e join veiculo v on v.id=e.veiculo_id and v.oficina_id=e.oficina_id ";
  private static final String COST =
      "coalesce((select sum(c.valor) from revenda_custo c where c.oficina_id=e.oficina_id and c.estoque_id=e.id),0)";
  private static final String SELECT =
      "select e.*,v.placa,v.marca,v.modelo,"
          + COST
          + " custos_preparacao,e.valor_aquisicao+"
          + COST
          + " custo_total,e.preco_anunciado-e.valor_aquisicao-"
          + COST
          + " margem_prevista ";

  public EstoqueService(
      RevendaDb db,
      RevendaEventos eventos,
      RevendaReferencias referencias,
      VeiculoPort veiculos,
      Clock clock) {
    this.db = db;
    this.eventos = eventos;
    this.referencias = referencias;
    this.veiculos = veiculos;
    this.clock = clock;
  }

  public Item obter(UUID id) {
    return db.one(
        Item.class, SELECT + FROM + "where e.oficina_id=:tenant and e.id=:id", Map.of("id", id));
  }

  public Item bloquear(UUID id) {
    var item =
        db.one(
            Item.class,
            SELECT + FROM + "where e.oficina_id=:tenant and e.id=:id for update of e",
            Map.of("id", id));
    if (item.status().equals("RESERVADO")) {
      var reservas =
          db.rows(
              ReservaExpirada.class,
              "select id from revenda_reserva where oficina_id=:tenant and estoque_id=:id and status='ATIVA' and validade<=:agora",
              params("id", id, "agora", clock.instant()));
      for (var reserva : reservas) {
        db.update(
            "update revenda_reserva set status='EXPIRADA',revisao=revisao+1 where oficina_id=:tenant and id=:id",
            Map.of("id", reserva.id()));
        eventos.registrar(RESERVA, reserva.id(), "EXPIRADA", "Reserva expirada");
      }
      if (!reservas.isEmpty()) {
        status(id, "DISPONIVEL", "Reserva expirada; veículo disponível");
        item = obter(id);
      }
    }
    return item;
  }

  public record ReservaExpirada(UUID id) {}

  public void expirarReservas() {
    // Bounded batch, row locks shared with reservations/sales; subsequent pages continue the sweep.
    var itens =
        db.rows(
            ReservaExpirada.class,
            "select e.id "
                + FROM
                + "where e.oficina_id=:tenant and e.status='RESERVADO' and exists(select 1 from revenda_reserva r where r.oficina_id=e.oficina_id and r.estoque_id=e.id and r.status='ATIVA' and r.validade<=:agora) order by e.id limit 100 for update of e skip locked",
            params("agora", clock.instant()));
    for (var item : itens) bloquear(item.id());
  }

  public Pagina<Item> listar(Filtro f, int pagina, int tamanho) {
    expirarReservas();
    var p = params();
    var sql = new StringBuilder(FROM + "where e.oficina_id=:tenant");
    filter(sql, p, "status", "e.status=:status", f.status());
    filter(sql, p, "marca", "lower(v.marca) like lower('%'||:marca||'%')", f.marca());
    filter(sql, p, "modelo", "lower(v.modelo) like lower('%'||:modelo||'%')", f.modelo());
    filter(sql, p, "de", "e.preco_anunciado>=:de", f.precoDe());
    filter(sql, p, "ate", "e.preco_anunciado<=:ate", f.precoAte());
    filter(sql, p, "entradaDe", "e.entrada>=:entradaDe", f.entradaDe());
    filter(sql, p, "entradaAte", "e.entrada<=:entradaAte", f.entradaAte());
    filter(
        sql,
        p,
        "busca",
        "lower(concat_ws(' ',v.placa,v.chassi,v.marca,v.modelo,v.versao)) like lower('%'||:busca||'%')",
        f.busca());
    return db.page(Item.class, SELECT, sql.toString(), "e.entrada desc,e.id", p, pagina, tamanho);
  }

  public Item criar(Novo n) {
    if (n.origem() == Origem.TROCA) throw ApiException.invalid("Registre a troca junto da venda.");
    var v = veiculos.bloquear(n.veiculoId());
    veiculos.adquirir(v.id(), v.clienteId(), "Aquisição direta para revenda");
    return inserir(n, null);
  }

  public Item inserir(Novo n, UUID avaliacaoId) {
    referencias.comercial(n.responsavelId());
    validarPrecos(n.precoAnunciado(), n.precoMinimo());
    var id = UUID.randomUUID();
    db.update(
        "insert into revenda_estoque(id,oficina_id,veiculo_id,avaliacao_id,responsavel_id,entrada,origem,valor_aquisicao,preco_anunciado,preco_minimo,status,observacoes) values(:id,:tenant,:veiculo,:avaliacao,:responsavel,:entrada,:origem,:custo,:anunciado,:minimo,'EM_PREPARACAO',:observacoes)",
        params(
            "id",
            id,
            "veiculo",
            n.veiculoId(),
            "avaliacao",
            avaliacaoId,
            "responsavel",
            n.responsavelId(),
            "entrada",
            n.entrada(),
            "origem",
            n.origem(),
            "custo",
            n.valorAquisicao(),
            "anunciado",
            n.precoAnunciado(),
            "minimo",
            n.precoMinimo(),
            "observacoes",
            n.observacoes()));
    eventos.registrar(ESTOQUE, id, "AQUISICAO", "Veículo adquirido; preparação iniciada");
    return obter(id);
  }

  public Item precos(UUID id, Precos n) {
    var e = bloquear(id);
    revision(e.revisao(), n.revisao());
    editavel(e);
    validarPrecos(n.precoAnunciado(), n.precoMinimo());
    db.update(
        "update revenda_estoque set preco_anunciado=:anunciado,preco_minimo=:minimo,revisao=revisao+1 where oficina_id=:tenant and id=:id",
        params("id", id, "anunciado", n.precoAnunciado(), "minimo", n.precoMinimo()));
    eventos.registrar(
        ESTOQUE,
        id,
        "PRECO",
        "Preços alterados: anunciado " + n.precoAnunciado() + "; mínimo " + n.precoMinimo());
    return obter(id);
  }

  public Item transicao(UUID id, Transicao n) {
    var e = bloquear(id);
    revision(e.revisao(), n.revisao());
    boolean allowed =
        switch (e.status()) {
          case "EM_AVALIACAO" -> n.status() == Status.EM_PREPARACAO;
          case "EM_PREPARACAO" -> n.status() == Status.DISPONIVEL;
          case "DISPONIVEL" -> n.status() == Status.EM_PREPARACAO;
          default -> false;
        };
    if (!allowed) throw ApiException.conflict("Transição de estoque inválida.");
    if (n.status() == Status.DISPONIVEL
        && e.ordemServicoId() != null
        && db.count(
                "select count(*) from revenda_custo where oficina_id=:tenant and estoque_id=:id and ordem_servico_id=:os",
                params("id", id, "os", e.ordemServicoId()))
            == 0)
      throw ApiException.conflict(
          "Conclua a OS interna e importe o custo antes de disponibilizar.");
    status(id, n.status().name(), "Situação alterada para " + n.status());
    return obter(id);
  }

  public void status(UUID id, String status, String descricao) {
    db.update(
        "update revenda_estoque set status=:status,revisao=revisao+1 where oficina_id=:tenant and id=:id",
        params("id", id, "status", status));
    eventos.registrar(ESTOQUE, id, status, descricao);
  }

  public void tocar(UUID id) {
    db.update(
        "update revenda_estoque set revisao=revisao+1 where oficina_id=:tenant and id=:id",
        Map.of("id", id));
  }

  public static void editavel(Item e) {
    if (e.status().equals("VENDIDO") || e.status().equals("RESERVADO"))
      throw ApiException.conflict("Estoque vendido ou reservado não pode ser alterado.");
  }

  private static void validarPrecos(java.math.BigDecimal anunciado, java.math.BigDecimal minimo) {
    if (minimo.compareTo(anunciado) > 0)
      throw ApiException.invalid("Preço mínimo maior que o anunciado.");
  }
}
