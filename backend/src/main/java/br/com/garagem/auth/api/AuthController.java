package br.com.garagem.auth.api;

import br.com.garagem.auth.api.AuthDtos.*;
import br.com.garagem.auth.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/login")
  @Operation(summary = "Entrar com oficina, e-mail e senha")
  public Sessao login(@Valid @RequestBody Login input) {
    return service.login(input);
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
