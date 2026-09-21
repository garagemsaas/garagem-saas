package br.com.garagem.auth.api;

import br.com.garagem.auth.api.AuthDtos.Refresh;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Onde o refresh token viaja quando o cliente é um navegador.
 *
 * <p>Antes ele voltava no corpo do login e o frontend o guardava numa variável — sessão perdida a
 * cada F5. As duas saídas óbvias eram ruins: {@code localStorage} entrega o token a qualquer XSS, e
 * manter em memória obriga a pessoa a entrar de novo o tempo todo. O cookie {@code HttpOnly}
 * resolve as duas coisas: sobrevive ao recarregamento e é ilegível por JavaScript.
 *
 * <p>O valor carrega empresa e token juntos, separados por ponto. Não é para esconder nada — é para
 * que o navegador não possa combinar o token de uma empresa com o identificador de outra: o par só
 * vale inteiro, e um par remontado simplesmente não encontra linha nenhuma.
 *
 * <p>{@code Path} restrito a {@code /api/v1/auth} faz o cookie não acompanhar nenhuma outra
 * requisição — a API inteira continua autenticando por {@code Authorization}, e o refresh só
 * aparece onde é usado.
 */
@Component
public class RefreshCookie {
  /** Nome fixo: o frontend nunca o lê, então não há motivo para ser configurável. */
  public static final String NOME = "garagem_refresh";

  public static final String CAMINHO = "/api/v1/auth";

  private final boolean secure;
  private final String sameSite;
  private final Duration ttl;

  public RefreshCookie(
      @Value("${app.auth.refresh-cookie.secure:true}") boolean secure,
      @Value("${app.auth.refresh-cookie.same-site:None}") String sameSite,
      @Value("${app.jwt.refresh-ttl:P7D}") Duration ttl) {
    this.secure = secure;
    this.sameSite = sameSite;
    this.ttl = ttl;
  }

  /**
   * Cookie da sessão recém-emitida.
   *
   * <p>Frontend e API ficam em domínios diferentes em produção, e aí só {@code SameSite=None} faz o
   * navegador enviar o cookie — que por sua vez exige {@code Secure}. Em desenvolvimento, onde tudo
   * é {@code http://localhost} pela mesma origem, {@code Lax} sem {@code Secure} é o que funciona.
   * Daí os dois serem configuráveis, e não uma constante.
   */
  public String emitir(UUID oficinaId, String refreshToken) {
    return ResponseCookie.from(NOME, oficinaId + "." + refreshToken)
        .httpOnly(true)
        .secure(secure)
        .sameSite(sameSite)
        .path(CAMINHO)
        .maxAge(ttl)
        .build()
        .toString();
  }

  /**
   * Cookie vazio e expirado, com os mesmos atributos — senão o navegador não substitui o antigo.
   */
  public String limpar() {
    return ResponseCookie.from(NOME, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite(sameSite)
        .path(CAMINHO)
        .maxAge(0)
        .build()
        .toString();
  }

  /**
   * Lê a credencial do cookie. Devolve vazio quando não há cookie ou quando o formato não bate —
   * quem chama trata os dois casos como "não autenticado", sem distinguir, porque a diferença só
   * interessaria a quem está tentando adivinhar.
   */
  public Optional<Refresh> ler(HttpServletRequest requisicao) {
    var cookies = requisicao.getCookies();
    if (cookies == null) return Optional.empty();
    for (var cookie : cookies) {
      if (!NOME.equals(cookie.getName())) continue;
      String valor = cookie.getValue();
      int corte = valor == null ? -1 : valor.indexOf('.');
      if (corte <= 0 || corte == valor.length() - 1) return Optional.empty();
      try {
        return Optional.of(
            new Refresh(UUID.fromString(valor.substring(0, corte)), valor.substring(corte + 1)));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
