package br.com.garagem.revenda.estoque;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.ESTOQUE;

import br.com.garagem.ordemservico.port.OrdemServicoPort;
import br.com.garagem.revenda.estoque.EstoqueDtos.*;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.veiculo.port.VeiculoPort;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PreparacaoService {
  private final EstoqueService estoques;
  private final RevendaDb db;
  private final RevendaEventos eventos;
  private final OrdemServicoPort ordens;
  private final VeiculoPort veiculos;
  private final Clock clock;

  public PreparacaoService(
      EstoqueService estoques,
      RevendaDb db,
      RevendaEventos eventos,
      OrdemServicoPort ordens,
      VeiculoPort veiculos,
      Clock clock) {
    this.estoques = estoques;
    this.db = db;
    this.eventos = eventos;
    this.ordens = ordens;
    this.veiculos = veiculos;
    this.clock = clock;
  }

  public Pagina<Custo> custos(UUID id, int pagina, int tamanho) {
    estoques.obter(id);
    return db.page(
        Custo.class,
        "select *",
        "from revenda_custo where oficina_id=:tenant and estoque_id=:id",
        "criado_em desc,id",
        Map.of("id", id),
        pagina,
        tamanho);
  }

  public Item custo(UUID id, NovoCusto n) {
    var e = estoques.bloquear(id);
    revision(e.revisao(), n.revisao());
    EstoqueService.editavel(e);
    inserirCusto(
        id,
        n.descricao(),
        n.categoria(),
        n.fornecedor(),
        n.valor(),
        n.data(),
        n.observacoes(),
        null,
        null);
    return estoques.obter(id);
  }

  private void inserirCusto(
      UUID id,
      String descricao,
      String categoria,
      String fornecedor,
      java.math.BigDecimal valor,
      LocalDate data,
      String observacoes,
      UUID os,
      UUID versao) {
    db.update(
        "insert into revenda_custo(id,oficina_id,estoque_id,descricao,categoria,fornecedor,valor,data,observacoes,autor_id,ordem_servico_id,orcamento_versao_id) values(:custo,:tenant,:id,:descricao,:categoria,:fornecedor,:valor,:data,:observacoes,:autor,:os,:versao)",
        params(
            "custo",
            UUID.randomUUID(),
            "id",
            id,
            "descricao",
            descricao,
            "categoria",
            categoria,
            "fornecedor",
            fornecedor,
            "valor",
            valor,
            "data",
            data,
            "observacoes",
            observacoes,
            "autor",
            UsuarioAutenticado.id(),
            "os",
            os,
            "versao",
            versao));
    estoques.tocar(id);
    eventos.registrar(ESTOQUE, id, "CUSTO", "Custo de preparação: " + descricao + " — " + valor);
  }

  public Item iniciar(UUID id, Preparar n) {
    var inicial = estoques.obter(id);
    veiculos.bloquear(inicial.veiculoId());
    var e = estoques.bloquear(id);
    revision(e.revisao(), n.revisao());
    if (!e.status().equals("EM_PREPARACAO") || e.ordemServicoId() != null)
      throw ApiException.conflict("Preparação interna já iniciada ou estoque indisponível.");
    UUID os = ordens.iniciarPreparacao(e.veiculoId(), n.mecanicoId(), n.km(), n.relato());
    db.update(
        "update revenda_estoque set ordem_servico_id=:os,revisao=revisao+1 where oficina_id=:tenant and id=:id",
        params("id", id, "os", os));
    eventos.registrar(ESTOQUE, id, "OS_INTERNA", "Preparação vinculada à OS " + os);
    return estoques.obter(id);
  }

  public Item concluir(UUID id, Revisao n) {
    var e = estoques.bloquear(id);
    revision(e.revisao(), n.revisao());
    EstoqueService.editavel(e);
    if (e.ordemServicoId() == null) throw ApiException.conflict("Não há OS interna vinculada.");
    var p = ordens.preparacao(e.ordemServicoId(), e.veiculoId());
    if (!p.concluida()) throw ApiException.conflict("A OS interna ainda não está pronta.");
    if (db.count(
            "select count(*) from revenda_custo where oficina_id=:tenant and ordem_servico_id=:os",
            Map.of("os", e.ordemServicoId()))
        == 0)
      inserirCusto(
          id,
          "Preparação pela oficina",
          "OFICINA",
          null,
          p.custo(),
          LocalDate.now(clock),
          null,
          p.ordemServicoId(),
          p.versaoId());
    return estoques.obter(id);
  }
}
