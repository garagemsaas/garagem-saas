package br.com.garagem.revenda.estoque;

import static br.com.garagem.revenda.shared.RevendaDb.*;
import static br.com.garagem.revenda.shared.RevendaEventos.Recurso.ESTOQUE;

import br.com.garagem.revenda.estoque.EstoqueDtos.*;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
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

  public PreparacaoService(EstoqueService estoques, RevendaDb db, RevendaEventos eventos) {
    this.estoques = estoques;
    this.db = db;
    this.eventos = eventos;
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
}
