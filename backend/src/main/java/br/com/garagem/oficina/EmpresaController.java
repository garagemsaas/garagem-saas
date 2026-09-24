package br.com.garagem.oficina;

import br.com.garagem.tenancy.RequerModulo;
import br.com.garagem.tenancy.SemModulo;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@SemModulo
@RequestMapping("/api/v1")
public class EmpresaController {
  private final EmpresaService service;

  public EmpresaController(EmpresaService service) {
    this.service = service;
  }

  @GetMapping("/empresa")
  public EmpresaService.Empresa obter() {
    return service.obter();
  }

  /**
   * A identidade vale para qualquer empresa, mas a página pública só existe porque existe uma OS.
   * Uma empresa que deixou de ter o módulo mantém links antigos ainda dentro da validade; eles não
   * devem continuar respondendo, nem com a identidade. Daí a exigência ser destes dois métodos, e
   * não da classe.
   */
  @GetMapping("/publico/{token}/empresa")
  @RequerModulo(ModuloEmpresa.OFICINA)
  public EmpresaService.Branding publico(@PathVariable String token) {
    return service.branding();
  }

  @GetMapping("/publico/{token}/empresa/imagens/{tipo}/{id}")
  @RequerModulo(ModuloEmpresa.OFICINA)
  public ResponseEntity<byte[]> imagemPublica(@PathVariable String tipo, @PathVariable UUID id) {
    return imagem(tipo, id);
  }

  @GetMapping("/empresa/imagens/{tipo}/{id}")
  public ResponseEntity<byte[]> imagem(@PathVariable String tipo, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(service.imagem(tipo, id));
  }
}
