package br.com.garagem.revenda.shared;

import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RevendaEventos {
  public enum Recurso {
    ESTOQUE,
    AVALIACAO,
    LEAD,
    PROPOSTA,
    RESERVA,
    VENDA;

    public String tabela() {
      return "revenda_" + name().toLowerCase(Locale.ROOT);
    }

    public String coluna() {
      return name().toLowerCase(Locale.ROOT) + "_id";
    }
  }

  public record Evento(
      long id, String tipo, String descricao, Integer versao, UUID autorId, Instant criadoEm) {}

  private final RevendaDb db;

  public RevendaEventos(RevendaDb db) {
    this.db = db;
  }

  public void registrar(Recurso recurso, UUID id, String tipo, String descricao) {
    registrar(recurso, id, tipo, descricao, null);
  }

  public void registrar(Recurso recurso, UUID id, String tipo, String descricao, Integer versao) {
    db.update(
        "insert into revenda_evento(oficina_id,"
            + recurso.coluna()
            + ",tipo,descricao,versao,autor_id) values(:tenant,:id,:tipo,:descricao,:versao,:autor)",
        RevendaDb.params(
            "id",
            id,
            "tipo",
            tipo,
            "descricao",
            descricao,
            "versao",
            versao,
            "autor",
            UsuarioAutenticado.id()));
  }

  @Transactional(readOnly = true)
  public Pagina<Evento> listar(Recurso recurso, UUID id, int pagina, int tamanho) {
    if (db.count(
            "select count(*) from " + recurso.tabela() + " where oficina_id=:tenant and id=:id",
            Map.of("id", id))
        != 1) throw br.com.garagem.shared.error.ApiException.missing();
    return db.page(
        Evento.class,
        "select id,tipo,descricao,versao,autor_id,criado_em",
        "from revenda_evento where oficina_id=:tenant and " + recurso.coluna() + "=:id",
        "id desc",
        Map.of("id", id),
        pagina,
        tamanho);
  }
}
