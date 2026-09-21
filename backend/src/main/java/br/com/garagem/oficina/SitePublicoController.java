package br.com.garagem.oficina;

import br.com.garagem.tenancy.SemModulo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Site público da empresa. Serve qualquer empresa, com ou sem os módulos operacionais, porque uma
 * página de apresentação não depende do que a empresa contratou — daí {@link SemModulo}.
 *
 * <p>O slug vem do endereço e é o próprio identificador público da empresa. Ele não concede acesso
 * a nada: o serviço devolve apenas campos de apresentação, e só de empresa ativa com o site
 * publicado.
 */
@RestController
@SemModulo
@RequestMapping("/api/v1/site/{slug}")
@SecurityRequirements
public class SitePublicoController {
  private final SitePublicoService service;

  public SitePublicoController(SitePublicoService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Conteúdo público do site da empresa")
  public SitePublicoService.Site obter(@PathVariable String slug) {
    return service.obter(slug);
  }

  /**
   * Imagem pública do site. Diferente do resto da API, aqui o cache é bem-vindo: são bytes
   * imutáveis — o identificador muda quando a imagem muda — e uma página institucional recarregada
   * a cada visita gastaria banda à toa.
   */
  @GetMapping("/imagens/{tipo}/{id}")
  public ResponseEntity<byte[]> imagem(
      @PathVariable String slug, @PathVariable String tipo, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic())
        .header("X-Content-Type-Options", "nosniff")
        .body(service.imagem(slug, tipo, id));
  }
}
