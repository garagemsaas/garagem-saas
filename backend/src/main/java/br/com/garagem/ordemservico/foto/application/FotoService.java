package br.com.garagem.ordemservico.foto.application;

import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.ordemservico.checklist.item.repository.ChecklistItemRepository;
import br.com.garagem.ordemservico.checklist.repository.ChecklistEntradaRepository;
import br.com.garagem.ordemservico.diagnostico.repository.DiagnosticoItemRepository;
import br.com.garagem.ordemservico.foto.domain.FotoVeiculo;
import br.com.garagem.ordemservico.foto.repository.FotoVeiculoRepository;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.tenancy.TenantContext;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class FotoService {
  public record Saida(
      UUID id,
      String finalidade,
      String descricao,
      String contentType,
      long tamanho,
      UUID checklistItemId,
      UUID diagnosticoItemId) {}

  public record Conteudo(byte[] bytes, String contentType) {}

  private final FotoVeiculoRepository fotos;
  private final OsService os;
  private final FotoStorage storage;
  private final ChecklistItemRepository itens;
  private final ChecklistEntradaRepository checklists;
  private final DiagnosticoItemRepository diagnosticos;

  public FotoService(
      FotoVeiculoRepository fotos,
      OsService os,
      FotoStorage storage,
      ChecklistItemRepository itens,
      ChecklistEntradaRepository checklists,
      DiagnosticoItemRepository diagnosticos) {
    this.fotos = fotos;
    this.os = os;
    this.storage = storage;
    this.itens = itens;
    this.checklists = checklists;
    this.diagnosticos = diagnosticos;
  }

  public Saida upload(
      UUID osId,
      MultipartFile file,
      String finalidade,
      String descricao,
      UUID checklistId,
      UUID diagnosticoId) {
    OsService.editavel(os.bloquear(osId));
    if (!Set.of("ENTRADA", "DIAGNOSTICO", "SERVICO").contains(finalidade)
        || descricao != null && descricao.length() > 500)
      throw ApiException.invalid("Finalidade ou descrição inválida.");
    if (checklistId != null && diagnosticoId != null)
      throw ApiException.invalid("Vincule a foto a apenas um item.");
    if (checklistId != null) {
      var item =
          itens
              .findByIdAndOficinaId(checklistId, TenantContext.current())
              .orElseThrow(ApiException::missing);
      checklists
          .findByIdAndOficinaId(item.checklistEntradaId, TenantContext.current())
          .filter(c -> c.ordemServicoId.equals(osId))
          .orElseThrow(ApiException::missing);
    }
    if (diagnosticoId != null)
      diagnosticos
          .findByIdAndOficinaId(diagnosticoId, TenantContext.current())
          .filter(d -> d.ordemServicoId.equals(osId))
          .orElseThrow(ApiException::missing);
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw ApiException.invalid("Envie uma foto PNG ou JPEG de até 10 MB.");
    byte[] bytes;
    String contentType;
    try {
      byte[] original = file.getBytes();
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
        var readers = ImageIO.getImageReaders(input);
        // O tipo vem do conteúdo decodificado, nunca da extensão ou do Content-Type enviados.
        if (!readers.hasNext()) throw naoSuportado();
        var reader = readers.next();
        try {
          reader.setInput(input);
          String format = reader.getFormatName().toLowerCase(Locale.ROOT);
          if (!Set.of("png", "jpeg", "jpg").contains(format)) throw naoSuportado();
          if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000)
            throw ApiException.invalid("A foto deve ter até 20 megapixels.");
          var image = reader.read(0);
          var output = new ByteArrayOutputStream();
          String target = format.equals("png") ? "png" : "jpg";
          ImageIO.write(image, target, output);
          bytes = output.toByteArray();
          contentType = target.equals("png") ? "image/png" : "image/jpeg";
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException e) {
      throw naoSuportado();
    }
    if (bytes.length > 10 * 1024 * 1024)
      throw ApiException.invalid("A foto processada excede 10 MB.");
    FotoVeiculo f = new FotoVeiculo();
    f.ordemServicoId = osId;
    f.autorId = OsService.autor();
    f.finalidade = finalidade;
    f.descricao = descricao;
    f.checklistItemId = checklistId;
    f.diagnosticoItemId = diagnosticoId;
    f.objeto = TenantContext.current() + "/" + osId + "/" + f.id;
    f.contentType = contentType;
    f.tamanho = bytes.length;
    try {
      storage.put(f.objeto, bytes, contentType);
    } catch (RuntimeException e) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ErrorCodes.STORAGE_UNAVAILABLE,
          "Armazenamento de fotos indisponível.");
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              try {
                storage.delete(f.objeto);
              } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(FotoService.class)
                    .atError()
                    .addKeyValue("foto_id", f.id)
                    .log("limpeza_foto_pendente");
              }
            }
          }
        });
    fotos.save(f);
    os.evento(osId, "FOTO_ADICIONADA", "Foto de " + finalidade + " adicionada.", OsService.autor());
    return saida(f);
  }

  @Transactional(readOnly = true)
  public List<Saida> listar(UUID osId) {
    os.entidade(osId);
    return fotos
        .findByOrdemServicoIdAndOficinaIdOrderByCriadoEmAscIdAsc(osId, TenantContext.current())
        .stream()
        .map(this::saida)
        .toList();
  }

  @Transactional(readOnly = true)
  public Conteudo conteudo(UUID osId, UUID fotoId) {
    os.entidade(osId);
    var f =
        fotos
            .findByIdAndOficinaId(fotoId, TenantContext.current())
            .filter(a -> a.ordemServicoId.equals(osId))
            .orElseThrow(ApiException::missing);
    try {
      return new Conteudo(storage.get(f.objeto), f.contentType);
    } catch (RuntimeException e) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ErrorCodes.STORAGE_UNAVAILABLE,
          "Foto temporariamente indisponível.");
    }
  }

  private static ApiException naoSuportado() {
    return new ApiException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
        "Envie uma imagem PNG ou JPEG válida.");
  }

  private Saida saida(FotoVeiculo f) {
    return new Saida(
        f.id,
        f.finalidade,
        f.descricao,
        f.contentType,
        f.tamanho,
        f.checklistItemId,
        f.diagnosticoItemId);
  }
}
