package br.com.garagem.revenda.lead;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.LEAD;

import br.com.garagem.revenda.lead.LeadDtos.*;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.veiculo.port.VeiculoPort;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeadService {
  private final RevendaDb db;
  private final RevendaEventos eventos;
  private final RevendaReferencias referencias;
  private final VeiculoPort veiculos;
  private static final String SELECT =
      "select l.*,c.nome cliente_nome,c.telefone,c.email,concat_ws(' ',v.marca,v.modelo,v.placa) veiculo_descricao ";
  private static final String FROM =
      "from revenda_lead l join cliente c on c.id=l.cliente_id and c.oficina_id=l.oficina_id left join veiculo v on v.id=l.veiculo_id and v.oficina_id=l.oficina_id where l.oficina_id=:tenant";

  public LeadService(
      RevendaDb db, RevendaEventos eventos, RevendaReferencias referencias, VeiculoPort veiculos) {
    this.db = db;
    this.eventos = eventos;
    this.referencias = referencias;
    this.veiculos = veiculos;
  }

  public Item obter(UUID id) {
    return db.one(Item.class, SELECT + FROM + " and l.id=:id", Map.of("id", id));
  }

  public Item bloquear(UUID id) {
    return db.one(Item.class, SELECT + FROM + " and l.id=:id for update of l", Map.of("id", id));
  }

  public Pagina<Item> listar(Filtro f, int pagina, int tamanho) {
    var sql = new StringBuilder(FROM);
    var p = params();
    filter(sql, p, "status", "l.status=:status", f.status());
    filter(sql, p, "vendedor", "l.vendedor_id=:vendedor", f.vendedorId());
    filter(sql, p, "veiculo", "l.veiculo_id=:veiculo", f.veiculoId());
    filter(sql, p, "de", "l.criado_em>=:de", f.de());
    filter(sql, p, "ate", "l.criado_em<:ate", f.ate());
    return db.page(Item.class, SELECT, sql.toString(), "l.criado_em desc,l.id", p, pagina, tamanho);
  }

  public Item criar(Novo n) {
    referencias.cliente(n.clienteId());
    referencias.comercial(n.vendedorId());
    if (n.veiculoId() != null) veiculos.buscar(n.veiculoId()).orElseThrow(ApiException::missing);
    var id = UUID.randomUUID();
    db.update(
        "insert into revenda_lead(id,oficina_id,cliente_id,veiculo_id,vendedor_id,origem,observacoes) values(:id,:tenant,:cliente,:veiculo,:vendedor,:origem,:observacoes)",
        params(
            "id",
            id,
            "cliente",
            n.clienteId(),
            "veiculo",
            n.veiculoId(),
            "vendedor",
            n.vendedorId(),
            "origem",
            n.origem(),
            "observacoes",
            n.observacoes()));
    eventos.registrar(LEAD, id, "NOVO", "Atendimento comercial iniciado");
    return obter(id);
  }

  public Item transicao(UUID id, Transicao n) {
    var l = bloquear(id);
    revision(l.revisao(), n.revisao());
    var destinos =
        switch (l.status()) {
          case "NOVO" -> Set.of(Status.CONTATO_REALIZADO, Status.PERDIDO);
          case "CONTATO_REALIZADO" -> Set.of(Status.INTERESSADO, Status.PERDIDO);
          case "INTERESSADO" -> Set.of(Status.NEGOCIACAO, Status.PERDIDO);
          case "PROPOSTA" -> Set.of(Status.NEGOCIACAO, Status.PERDIDO);
          case "NEGOCIACAO" -> Set.of(Status.INTERESSADO, Status.PERDIDO);
          default -> Set.<Status>of();
        };
    if (!destinos.contains(n.status()))
      throw ApiException.conflict(
          "Transição de lead inválida. Proposta e venda avançam pelo respectivo fluxo.");
    status(id, n.status().name());
    return obter(id);
  }

  public void vincularProposta(UUID id, UUID clienteId, UUID veiculoId) {
    var l = bloquear(id);
    if (!l.clienteId().equals(clienteId)
        || l.veiculoId() != null && !l.veiculoId().equals(veiculoId))
      throw ApiException.invalid("Lead não corresponde ao comprador e veículo.");
    if (Set.of("VENDIDO", "PERDIDO").contains(l.status()))
      throw ApiException.conflict("Lead encerrado.");
    status(id, "PROPOSTA");
  }

  public void vendido(UUID id) {
    var l = bloquear(id);
    if (l.status().equals("PERDIDO")) throw ApiException.conflict("Lead encerrado como perdido.");
    status(id, "VENDIDO");
  }

  private void status(UUID id, String status) {
    db.update(
        "update revenda_lead set status=:status,revisao=revisao+1 where oficina_id=:tenant and id=:id",
        params("id", id, "status", status));
    eventos.registrar(LEAD, id, status, "Atendimento alterado para " + status);
  }
}
