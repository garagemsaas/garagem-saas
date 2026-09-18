package br.com.garagem.shared.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Geração e hash de credenciais opacas (refresh token e link público). Ficava em {@code auth}, mas
 * é utilidade de infraestrutura: o módulo de ordem de serviço precisava dela para os links do
 * cliente e acabava importando o interior de {@code auth}.
 */
public final class Tokens {
  private static final SecureRandom RANDOM = new SecureRandom();

  private Tokens() {}

  public static String novo() {
    byte[] b = new byte[32];
    RANDOM.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
