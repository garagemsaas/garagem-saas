package br.com.garagem.ordemservico.foto.api;

import br.com.garagem.ordemservico.foto.application.FotoService;
import br.com.garagem.ordemservico.foto.application.FotoService.Saida;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ordens-servico/{osId}/fotos")
public class FotoController {
  private final FotoService service;

  public FotoController(FotoService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Anexar foto PNG/JPEG privada",
      description =
          "Multipart. O tipo é decidido pelo conteúdo decodificado, não pela extensão nem pelo"
              + " Content-Type enviados; a imagem é reescrita, o que descarta metadados como GPS."
              + " Limite de 10 MB e 20 megapixels. Formato diferente de PNG/JPEG responde 415.")
  public Saida upload(
      @PathVariable UUID osId,
      @RequestPart MultipartFile arquivo,
      @RequestParam String finalidade,
      @RequestParam(required = false) String descricao,
      @RequestParam(required = false) UUID checklistItemId,
      @RequestParam(required = false) UUID diagnosticoItemId) {
    return service.upload(osId, arquivo, finalidade, descricao, checklistItemId, diagnosticoItemId);
  }

  @GetMapping
  @Operation(summary = "Listar fotos privadas da OS")
  public List<Saida> listar(@PathVariable UUID osId) {
    return service.listar(osId);
  }

  @GetMapping("/{fotoId}/conteudo")
  @Operation(
      summary = "Ler foto mediante autenticação e isolamento da oficina",
      description =
          "O bucket é privado e nunca é exposto: os bytes passam pela API, que confere oficina e"
              + " vínculo com a OS antes de ler o objeto.")
  public ResponseEntity<byte[]> conteudo(@PathVariable UUID osId, @PathVariable UUID fotoId) {
    var c = service.conteudo(osId, fotoId);
    // O nome é derivado do id, não de nada que o usuário tenha enviado no upload.
    String arquivo = "foto-" + fotoId + (c.contentType().endsWith("png") ? ".png" : ".jpg");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + arquivo + "\"")
        .contentLength(c.bytes().length)
        .contentType(MediaType.parseMediaType(c.contentType()))
        .body(c.bytes());
  }
}
