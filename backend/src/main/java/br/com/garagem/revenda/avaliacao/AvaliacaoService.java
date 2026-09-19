package br.com.garagem.revenda.avaliacao;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.AVALIACAO;

import br.com.garagem.revenda.avaliacao.AvaliacaoDtos.*;
import br.com.garagem.revenda.estoque.*;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.veiculo.port.VeiculoPort;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AvaliacaoService {
  private final RevendaDb db;
  private final RevendaEventos eventos;
  private final RevendaReferencias referencias;
  private final VeiculoPort veiculos;
  private final EstoqueService estoques;
  private final Clock clock;
  private static final String FROM =
      "from revenda_avaliacao a join veiculo v on v.id=a.veiculo_id and v.oficina_id=a.oficina_id where a.oficina_id=:tenant";
  private static final String SELECT = "select a.*,v.placa,v.marca,v.modelo ";

  public AvaliacaoService(
      RevendaDb db,
      RevendaEventos eventos,
      RevendaReferencias referencias,
      VeiculoPort veiculos,
      EstoqueService estoques,
      Clock clock) {
    this.db = db;
    this.eventos = eventos;
    this.referencias = referencias;
    this.veiculos = veiculos;
    this.estoques = estoques;
    this.clock = clock;
  }

  public Item obter(UUID id) {
    return db.one(Item.class, SELECT + FROM + " and a.id=:id", Map.of("id", id));
  }

  public Item bloquear(UUID id) {
    return db.one(Item.class, SELECT + FROM + " and a.id=:id for update of a", Map.of("id", id));
  }

  public Pagina<Item> listar(Status status, UUID veiculoId, int pagina, int tamanho) {
    var sql = new StringBuilder(FROM);
    var p = params();
    filter(sql, p, "status", "a.status=:status", status);
    filter(sql, p, "veiculo", "a.veiculo_id=:veiculo", veiculoId);
    return db.page(Item.class, SELECT, sql.toString(), "a.criado_em desc,a.id", p, pagina, tamanho);
  }

  public Item criar(Nova n) {
    referencias.comercial(n.avaliadorId());
    if (n.clienteId() != null) referencias.cliente(n.clienteId());
    var v = veiculos.bloquear(n.veiculoId());
    if (v.propriedade().equals("EMPRESA") || !Objects.equals(v.clienteId(), n.clienteId()))
      throw ApiException.conflict("Confira o proprietário atual do veículo.");
    if (n.km() < v.km()) throw ApiException.invalid("Quilometragem inferior à registrada.");
    var id = UUID.randomUUID();
    veiculos.atualizarQuilometragem(v.id(), n.km());
    db.update(
        "insert into revenda_avaliacao(id,oficina_id,veiculo_id,cliente_id,avaliador_id,data,km,valor_estimado,valor_oferecido,validade,observacoes) values(:id,:tenant,:veiculo,:cliente,:avaliador,:data,:km,:estimado,:oferecido,:validade,:observacoes)",
        params(
            "id",
            id,
            "veiculo",
            v.id(),
            "cliente",
            n.clienteId(),
            "avaliador",
            n.avaliadorId(),
            "data",
            n.data(),
            "km",
            n.km(),
            "estimado",
            n.valorEstimado(),
            "oferecido",
            n.valorOferecido(),
            "validade",
            n.validade(),
            "observacoes",
            n.observacoes()));
    eventos.registrar(AVALIACAO, id, "ABERTA", "Avaliação comercial aberta");
    return obter(id);
  }

  public EstoqueDtos.Item aceitar(UUID id, Aceite n) {
    var inicial = obter(id);
    veiculos.bloquear(inicial.veiculoId());
    var a = bloquear(id);
    revision(a.revisao(), n.revisao());
    return adquirir(
        a, n.responsavelId(), n.precoAnunciado(), n.precoMinimo(), EstoqueDtos.Origem.COMPRA);
  }

  public void validarTroca(Item a, UUID cliente, BigDecimal valor) {
    aberta(a);
    var v = veiculos.buscar(a.veiculoId()).orElseThrow(ApiException::missing);
    if (!Objects.equals(a.clienteId(), cliente)
        || !Objects.equals(v.clienteId(), cliente)
        || !"CLIENTE".equals(v.propriedade()))
      throw ApiException.conflict("O veículo da troca deve pertencer ao comprador.");
    if (a.valorOferecido().compareTo(valor) != 0)
      throw ApiException.conflict("O valor da troca deve corresponder à avaliação vigente.");
  }

  public EstoqueDtos.Item adquirir(
      Item a,
      UUID responsavel,
      BigDecimal anunciado,
      BigDecimal minimo,
      EstoqueDtos.Origem origem) {
    aberta(a);
    veiculos.adquirir(
        a.veiculoId(),
        a.clienteId(),
        origem == EstoqueDtos.Origem.TROCA
            ? "Recebimento em troca na venda"
            : "Aceite de avaliação");
    db.update(
        "update revenda_avaliacao set status='ACEITA',revisao=revisao+1 where oficina_id=:tenant and id=:id",
        Map.of("id", a.id()));
    var estoque =
        estoques.inserir(
            new EstoqueDtos.Novo(
                a.veiculoId(),
                responsavel,
                LocalDate.now(clock),
                origem,
                a.valorOferecido(),
                anunciado,
                minimo,
                a.observacoes()),
            a.id());
    eventos.registrar(AVALIACAO, a.id(), "ACEITA", "Avaliação aceita; estoque " + estoque.id());
    return estoque;
  }

  public Item transicao(UUID id, Transicao n) {
    var a = bloquear(id);
    revision(a.revisao(), n.revisao());
    if (!a.status().equals("ABERTA")
        || !Set.of(Status.RECUSADA, Status.CANCELADA, Status.EXPIRADA).contains(n.status()))
      throw ApiException.conflict("Transição de avaliação inválida.");
    if (n.status() == Status.EXPIRADA && a.validade().isAfter(clock.instant()))
      throw ApiException.conflict("A avaliação ainda está válida.");
    db.update(
        "update revenda_avaliacao set status=:status,revisao=revisao+1 where oficina_id=:tenant and id=:id",
        params("id", id, "status", n.status()));
    eventos.registrar(AVALIACAO, id, n.status().name(), "Avaliação " + n.status());
    return obter(id);
  }

  private void aberta(Item a) {
    if (!a.status().equals("ABERTA") || !a.validade().isAfter(clock.instant()))
      throw ApiException.conflict("Avaliação encerrada ou expirada.");
  }
}
