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
  @Operation(summary = "Anexar foto PNG/JPEG privada; remove metadados e valida item da mesma OS")
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
  @Operation(summary = "Ler foto mediante autenticação e isolamento da oficina")
  public ResponseEntity<byte[]> conteudo(@PathVariable UUID osId, @PathVariable UUID fotoId) {
    var c = service.conteudo(osId, fotoId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(c.contentType()))
        .body(c.bytes());
  }
}
