package br.com.garagem.revenda.foto;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.revenda.foto.RevendaFotoService.*;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.RequerModulo;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/revenda/fotos/{contexto}/{id}")
@RequerModulo(ModuloEmpresa.REVENDA)
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
public class RevendaFotoController {
  private final RevendaFotoService service;

  public RevendaFotoController(RevendaFotoService service) {
    this.service = service;
  }

  @GetMapping
  public Pagina<Foto> listar(
      @PathVariable Contexto contexto,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return service.listar(contexto, id, pagina, tamanho);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Foto upload(
      @PathVariable Contexto contexto,
      @PathVariable UUID id,
      @RequestPart MultipartFile arquivo,
      @RequestParam String finalidade,
      @RequestParam(required = false) String descricao) {
    return service.upload(contexto, id, arquivo, finalidade, descricao);
  }

  @GetMapping("/{fotoId}/conteudo")
  public ResponseEntity<byte[]> conteudo(
      @PathVariable Contexto contexto, @PathVariable UUID id, @PathVariable UUID fotoId) {
    var c = service.conteudo(contexto, id, fotoId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"foto-"
                + fotoId
                + (c.contentType().endsWith("png") ? ".png" : ".jpg")
                + "\"")
        .contentType(MediaType.parseMediaType(c.contentType()))
        .contentLength(c.bytes().length)
        .body(c.bytes());
  }
}
