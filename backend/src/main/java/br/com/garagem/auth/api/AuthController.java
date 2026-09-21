package br.com.garagem.auth.api;

import br.com.garagem.auth.api.AuthDtos.*;
import br.com.garagem.auth.application.AuthService;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.seguranca.LimiteRequisicoesFilter;
import br.com.garagem.tenancy.SemModulo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@SemModulo
@RequestMapping("/api/v1/auth")
@SecurityRequirements
public class AuthController {
  private final AuthService service;
  private final LimiteRequisicoesFilter limite;
  private final RefreshCookie cookie;

  public AuthController(AuthService service, LimiteRequisicoesFilter limite, RefreshCookie cookie) {
    this.service = service;
    this.limite = limite;
    this.cookie = cookie;
  }

  @PostMapping("/login")
  @Operation(summary = "Entrar com oficina, e-mail e senha")
  public ResponseEntity<Sessao> login(
      @Valid @RequestBody Login input, HttpServletRequest requisicao) {
    var sessao = service.login(input);
    // Só o acerto zera o contador. Quem erra segue acumulando na janela, e quem entra não carrega
    // as tentativas de quem compartilha o mesmo IP de saída.
    limite.sucessoDeLogin(requisicao);
    return comCookie(sessao);
  }

  /**
   * O corpo é opcional. Navegador não manda nada e a credencial vem do cookie; cliente de API
   * continua mandando empresa e token no corpo, como sempre. Quando os dois chegam, o cookie vence:
   * ele é o único que o JavaScript da página não consegue forjar.
   */
  @PostMapping("/refresh")
  @Operation(summary = "Rotacionar refresh token de uso único")
  public ResponseEntity<Sessao> refresh(
      @RequestBody(required = false) @Valid Refresh input, HttpServletRequest requisicao) {
    return comCookie(service.refresh(credencial(input, requisicao)));
  }

  @PostMapping("/logout")
  @Operation(summary = "Revogar refresh token; access token expira em até 15 minutos")
  public ResponseEntity<Void> logout(
      @RequestBody(required = false) @Valid Refresh input, HttpServletRequest requisicao) {
    service.revoke(credencial(input, requisicao));
    // O cookie é apagado mesmo se a revogação não encontrar linha: sair tem de limpar o navegador
    // de qualquer forma, senão a pessoa fica com um cookie morto que só produz 401.
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie.limpar())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .build();
  }

  private Refresh credencial(Refresh input, HttpServletRequest requisicao) {
    return cookie.ler(requisicao).orElseGet(() -> exigir(input));
  }

  private static Refresh exigir(Refresh input) {
    // Sem cookie e sem corpo não há o que validar: é o caso de quem nunca entrou, ou de quem já
    // saiu. A mesma resposta de um token inválido, para não contar a diferença.
    if (input == null) throw ApiException.unauthorized();
    return input;
  }

  private ResponseEntity<Sessao> comCookie(Sessao sessao) {
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.emitir(sessao.oficinaId(), sessao.refreshToken()))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(sessao);
  }
}
