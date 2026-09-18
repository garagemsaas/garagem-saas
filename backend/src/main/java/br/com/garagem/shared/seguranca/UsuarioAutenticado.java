package br.com.garagem.shared.seguranca;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Quem está executando a requisição.
 *
 * <p>Este utilitário morava dentro do serviço de ordem de serviço e era chamado por quatro módulos
 * — inclusive por {@code assinatura}, que por isso importava {@code ordemservico} enquanto {@code
 * ordemservico} já importava {@code assinatura}. Era um ciclo entre módulos criado por uma
 * utilidade guardada no lugar errado: identidade do autor não é assunto de ordem de serviço.
 */
public final class UsuarioAutenticado {
  private UsuarioAutenticado() {}

  /** Id do usuário da requisição. Só é chamado em caminho autenticado, onde o subject existe. */
  public static UUID id() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }
}
