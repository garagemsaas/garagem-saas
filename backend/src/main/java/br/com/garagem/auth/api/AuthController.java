package br.com.garagem.auth.api;

import br.com.garagem.auth.api.AuthDtos.*;
import br.com.garagem.auth.application.AuthService;
import br.com.garagem.shared.seguranca.LimiteRequisicoesFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
public class AuthController {
  private final AuthService service;
  private final LimiteRequisicoesFilter limite;

  public AuthController(AuthService service, LimiteRequisicoesFilter limite) {
    this.service = service;
    this.limite = limite;
  }

  @PostMapping("/login")
  @Operation(summary = "Entrar com oficina, e-mail e senha")
  public Sessao login(@Valid @RequestBody Login input, HttpServletRequest requisicao) {
    var sessao = service.login(input);
    // Só o acerto zera o contador. Quem erra segue acumulando na janela, e quem entra não carrega
    // as tentativas de quem compartilha o mesmo IP de saída.
    limite.sucessoDeLogin(requisicao);
    return sessao;
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotacionar refresh token de uso único")
  public Sessao refresh(@Valid @RequestBody Refresh input) {
    return service.refresh(input);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Revogar refresh token; access token expira em até 15 minutos")
  public void logout(@Valid @RequestBody Refresh input) {
    service.revoke(input);
  }
}
