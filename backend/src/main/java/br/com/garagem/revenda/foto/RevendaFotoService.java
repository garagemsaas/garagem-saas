package br.com.garagem.revenda.foto;

import static br.com.garagem.revenda.shared.RevendaDb.*;

import br.com.garagem.revenda.avaliacao.AvaliacaoService;
import br.com.garagem.revenda.estoque.EstoqueService;
import br.com.garagem.revenda.shared.*;
import br.com.garagem.revenda.shared.RevendaEventos.Recurso;
import br.com.garagem.shared.error.*;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.shared.storage.*;
import br.com.garagem.tenancy.TenantContext;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class RevendaFotoService {
  public enum Contexto {
    AVALIACAO,
    ESTOQUE;

    String coluna() {
      return name().toLowerCase(Locale.ROOT) + "_id";
    }
  }

  public record Foto(
      UUID id,
      String finalidade,
      String descricao,
      String contentType,
      long tamanho,
      Instant criadoEm) {}

  public record Objeto(String objeto, String contentType) {}

  public record Conteudo(byte[] bytes, String contentType) {}

  private final RevendaDb db;
  private final ArquivoStorage storage;
  private final EstoqueService estoques;
  private final AvaliacaoService avaliacoes;
  private final RevendaEventos eventos;

  public RevendaFotoService(
      RevendaDb db,
      ArquivoStorage storage,
      EstoqueService estoques,
      AvaliacaoService avaliacoes,
      RevendaEventos eventos) {
    this.db = db;
    this.storage = storage;
    this.estoques = estoques;
    this.avaliacoes = avaliacoes;
    this.eventos = eventos;
  }

  private void existe(Contexto contexto, UUID id) {
    if (contexto == Contexto.ESTOQUE) estoques.obter(id);
    else avaliacoes.obter(id);
  }

  public Foto upload(
      Contexto contexto, UUID id, MultipartFile arquivo, String finalidade, String descricao) {
    if (contexto == Contexto.ESTOQUE) EstoqueService.editavel(estoques.bloquear(id));
    else if (!avaliacoes.bloquear(id).status().equals("ABERTA"))
      throw ApiException.conflict("Avaliação encerrada.");
    if (!(contexto == Contexto.AVALIACAO ? Set.of("AVALIACAO") : Set.of("ESTOQUE", "PREPARACAO"))
            .contains(finalidade)
        || descricao != null && descricao.length() > 500)
      throw ApiException.invalid("Finalidade ou descrição inválida.");
    var imagem = ImagemSegura.decodificar(arquivo);
    var fotoId = UUID.randomUUID();
    String objeto = TenantContext.current() + "/revenda/" + id + "/" + fotoId;
    try {
      storage.put(objeto, imagem.bytes(), imagem.contentType());
    } catch (RuntimeException e) {
      throw indisponivel();
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED)
              try {
                storage.delete(objeto);
              } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(RevendaFotoService.class)
                    .atError()
                    .addKeyValue("foto_id", fotoId)
                    .log("limpeza_foto_pendente");
              }
          }
        });
    db.update(
        "insert into revenda_foto(id,oficina_id,"
            + contexto.coluna()
            + ",finalidade,descricao,objeto,content_type,tamanho,autor_id) values(:foto,:tenant,:id,:finalidade,:descricao,:objeto,:tipo,:tamanho,:autor)",
        params(
            "foto",
            fotoId,
            "id",
            id,
            "finalidade",
            finalidade,
            "descricao",
            descricao,
            "objeto",
            objeto,
            "tipo",
            imagem.contentType(),
            "tamanho",
            imagem.bytes().length,
            "autor",
            UsuarioAutenticado.id()));
    eventos.registrar(
        Recurso.valueOf(contexto.name()), id, "FOTO", "Foto de " + finalidade + " adicionada");
    return db.one(
        Foto.class,
        "select * from revenda_foto where oficina_id=:tenant and id=:id",
        Map.of("id", fotoId));
  }

  public Pagina<Foto> listar(Contexto contexto, UUID id, int pagina, int tamanho) {
    existe(contexto, id);
    return db.page(
        Foto.class,
        "select *",
        "from revenda_foto where oficina_id=:tenant and " + contexto.coluna() + "=:id",
        "criado_em desc,id",
        Map.of("id", id),
        pagina,
        tamanho);
  }

  public Conteudo conteudo(Contexto contexto, UUID id, UUID fotoId) {
    existe(contexto, id);
    var f =
        db.one(
            Objeto.class,
            "select objeto,content_type from revenda_foto where oficina_id=:tenant and "
                + contexto.coluna()
                + "=:id and id=:foto",
            params("id", id, "foto", fotoId));
    try {
      return new Conteudo(storage.get(f.objeto()), f.contentType());
    } catch (RuntimeException e) {
      throw indisponivel();
    }
  }

  private static ApiException indisponivel() {
    return new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCodes.STORAGE_UNAVAILABLE,
        "Armazenamento de fotos temporariamente indisponível.");
  }
}
