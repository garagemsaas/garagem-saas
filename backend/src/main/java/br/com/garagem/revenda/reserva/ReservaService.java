package br.com.garagem.revenda.reserva;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.RESERVA;

import br.com.garagem.revenda.estoque.EstoqueService;
import br.com.garagem.revenda.reserva.ReservaDtos.*;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReservaService {
  private final RevendaDb db;
  private final EstoqueService estoques;
  private final RevendaReferencias referencias;
  private final RevendaEventos eventos;

  public ReservaService(
      RevendaDb db,
      EstoqueService estoques,
      RevendaReferencias referencias,
      RevendaEventos eventos) {
    this.db = db;
    this.estoques = estoques;
    this.referencias = referencias;
    this.eventos = eventos;
  }

  public Item obter(UUID id) {
    return db.one(
        Item.class,
        "select * from revenda_reserva where oficina_id=:tenant and id=:id",
        Map.of("id", id));
  }

  public Pagina<Item> listar(UUID estoqueId, int pagina, int tamanho) {
    estoques.expirarReservas();
    var p = params();
    var sql = new StringBuilder("from revenda_reserva where oficina_id=:tenant");
    filter(sql, p, "estoque", "estoque_id=:estoque", estoqueId);
    return db.page(Item.class, "select *", sql.toString(), "criado_em desc,id", p, pagina, tamanho);
  }

  public Item criar(Nova n) {
    referencias.cliente(n.clienteId());
    referencias.comercial(n.vendedorId());
    var e = estoques.bloquear(n.estoqueId());
    revision(e.revisao(), n.revisaoEstoque());
    if (!e.status().equals("DISPONIVEL"))
      throw ApiException.conflict("O veículo não está disponível para reserva.");
    var id = UUID.randomUUID();
    db.update(
        "insert into revenda_reserva(id,oficina_id,estoque_id,cliente_id,vendedor_id,validade,observacoes) values(:id,:tenant,:estoque,:cliente,:vendedor,:validade,:observacoes)",
        params(
            "id",
            id,
            "estoque",
            e.id(),
            "cliente",
            n.clienteId(),
            "vendedor",
            n.vendedorId(),
            "validade",
            n.validade(),
            "observacoes",
            n.observacoes()));
    estoques.status(e.id(), "RESERVADO", "Reserva " + id);
    eventos.registrar(RESERVA, id, "ATIVA", "Reserva registrada");
    return obter(id);
  }

  public Item cancelar(UUID id, Cancelamento n) {
    var inicial = obter(id);
    estoques.bloquear(inicial.estoqueId());
    var r = obter(id);
    revision(r.revisao(), n.revisao());
    if (!r.status().equals("ATIVA")) throw ApiException.conflict("Reserva já encerrada.");
    db.update(
        "update revenda_reserva set status='CANCELADA',revisao=revisao+1 where oficina_id=:tenant and id=:id",
        Map.of("id", id));
    estoques.status(r.estoqueId(), "DISPONIVEL", "Reserva cancelada");
    eventos.registrar(RESERVA, id, "CANCELADA", "Reserva cancelada");
    return obter(id);
  }

  /** Caller holds the stock row lock, including expiration sweep. */
  public UUID concluir(UUID estoqueId, UUID clienteId) {
    var reservas =
        db.rows(
            Item.class,
            "select * from revenda_reserva where oficina_id=:tenant and estoque_id=:estoque and status='ATIVA'",
            Map.of("estoque", estoqueId));
    if (reservas.isEmpty()) return null;
    var r = reservas.getFirst();
    if (!r.clienteId().equals(clienteId))
      throw ApiException.conflict("Veículo reservado para outro cliente.");
    db.update(
        "update revenda_reserva set status='CONCLUIDA',revisao=revisao+1 where oficina_id=:tenant and id=:id",
        Map.of("id", r.id()));
    eventos.registrar(RESERVA, r.id(), "CONCLUIDA", "Reserva concluída na venda");
    return r.id();
  }
}
