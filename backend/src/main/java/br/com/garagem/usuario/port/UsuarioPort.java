package br.com.garagem.usuario.port;

import java.util.UUID;

/**
 * O que outros módulos precisam saber sobre a equipe da oficina.
 *
 * <p>Responde perguntas de negócio em vez de devolver a entidade: quem valida um responsável quer
 * saber se ele pode assumir aquele papel, não inspecionar o cadastro. Assim o enum {@code Papel} e
 * o repositório continuam internos, e mudar a modelagem de usuário não alcança outros módulos.
 */
public interface UsuarioPort {
  /** Existe na oficina autenticada. */
  boolean existe(UUID usuarioId);

  /** Pode ser responsável técnico por uma ordem de serviço. */
  boolean ehMecanicoAtivo(UUID usuarioId);

  /** Pode responder comercialmente pela oficina (proprietário ou atendente). */
  boolean ehComercialAtivo(UUID usuarioId);
}
